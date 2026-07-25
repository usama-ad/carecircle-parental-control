package com.example.carecirclechildapp.services

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface.ROTATION_0
import androidx.annotation.OptIn
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.example.carecirclechildapp.Activities.BlinkEyeWarning
import com.example.carecirclechildapp.Activities.CloseScreenWarning
import com.example.carecirclechildapp.modals.BlinkLog
import com.example.carecirclechildapp.utils.FirestoreUtils
import com.example.carecirclechildapp.utils.PreferenceHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.Executors

class FaceDistanceService : LifecycleService() {

    private var cameraProvider: ProcessCameraProvider? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var blinkCount = 0
    private var lastBlinkTime = System.currentTimeMillis()
    private var closeFrameCount = 0
    private var lastFaceDetectedTime = System.currentTimeMillis()
    private var parentId: String = ""
    private var childName: String = ""

    private val auth by lazy { FirebaseAuth.getInstance() }

    private val detector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .enableTracking()
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .build()
        )
    }

    override fun onCreate() {
        super.onCreate()
        PreferenceHelper.init(applicationContext)
        Log.d("FaceCheck", "onCreate: Service Started")

        try {
            startForegroundService()
            startCamera()
        } catch (e: Exception) {
            Log.e("FaceCheck", "Failed to init camera: ${e.message}", e)
        }

        FirestoreUtils.getParent { id ->
            parentId = id ?: ""
        }
        FirestoreUtils.getChildName { name ->
            childName = name ?: ""
        }
    }

    private fun startForegroundService() {
        val channelId = "face_distance_channel"
        val channelName = "Face Distance Monitoring"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(chan)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Eye Distance Monitor")
            .setContentText("Monitoring face distance")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()

        startForeground(101, notification)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build()
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetRotation(ROTATION_0)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor, ImageAnalysis.Analyzer { imageProxy ->
                    processImageProxy(imageProxy)
                })

                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                    .build()

                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                Log.e("FaceCheck", "Camera start failed: ${e.message}", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        detector.process(image)
            .addOnSuccessListener { faces ->
                try {
                    val currentTime = System.currentTimeMillis()

                    if (faces.isNotEmpty()) {
                        val face = faces[0]

                        // --- STABILITY FILTERS ---
                        val box = face.boundingBox
                        if (face.trackingId == null || box.width() < 80 || box.height() < 80) {
                            // Very tiny / unreliable face in dark → skip
                            imageProxy.close()
                            return@addOnSuccessListener
                        }

                        // Eye probabilities must be available
                        val leftEye = face.leftEyeOpenProbability
                        val rightEye = face.rightEyeOpenProbability
                        if (leftEye == null || rightEye == null) {
                            imageProxy.close()
                            return@addOnSuccessListener
                        }

                        // Skip ghost detections (both eyes 0 and very small face)
                        if (leftEye < 0.01f && rightEye < 0.01f && box.width() < 120) {
                            imageProxy.close()
                            return@addOnSuccessListener
                        }

                        lastFaceDetectedTime = currentTime

                        // ---- CLOSE SCREEN WARNING ----
                        val faceWidth = box.width()
                        if (faceWidth in 301..999) {
                            closeFrameCount++
                            if (closeFrameCount >= 3 && !PreferenceHelper.isCloseWarningVisible()) {
                                sendCloseAlert()
                                showTooCloseWarning()
                                closeFrameCount = 0
                            }
                        } else {
                            closeFrameCount = 0
                            if (faceWidth < 250 && PreferenceHelper.isCloseWarningVisible()) {
                                PreferenceHelper.setCloseWarningVisible(false)
                            }
                        }

                        // ---- BLINK WARNING ----
                        val isBlinking = leftEye < 0.4 && rightEye < 0.4
                        val duration = currentTime - lastBlinkTime

                        if (isBlinking) {
                            blinkCount++
                            lastBlinkTime = currentTime
                            PreferenceHelper.setBlinkWarningVisible(false)
                        }

                        if (duration > 15000 && !PreferenceHelper.isBlinkWarningVisible()) {
                            val blinkLog = BlinkLog(System.currentTimeMillis(), blinkCount, duration, true)
                            storeBlinkLog(auth.currentUser?.uid, blinkLog)
                            sendBlinkAlert()
                            showBlinkReminder()
                            lastBlinkTime = currentTime
                            blinkCount = 0
                        }
                    } else {
                        // No face → reset timers after 2s
                        if (currentTime - lastFaceDetectedTime > 2000) {
                            lastBlinkTime = currentTime
                            closeFrameCount = 0
                        }
                    }
                } catch (e: Exception) {
                    Log.e("FaceCheck", "Face analysis error: ${e.message}", e)
                }
            }
            .addOnFailureListener {
                Log.e("FaceCheck", "Face detection failed: ${it.message}", it)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun storeBlinkLog(uid: String?, blinkLog: BlinkLog) {
        if (uid.isNullOrBlank()) return
        try {
            FirestoreUtils.uploadBlinkData(uid, blinkLog)
        } catch (e: Exception) {
            Log.e("FaceCheck", "Failed to upload blink log: ${e.message}", e)
        }
    }

    private fun sendBlinkAlert() {
        val uid = auth.currentUser?.uid ?: return
        if (parentId.isBlank()) return
        try {
            FirestoreUtils.sendAlertToFirebase(
                parentId,
                uid,
                "Blink Alert",
                "Child ${childName.ifBlank { "Unknown" }} hasn't blinked for 15 sec",
                ""
            )
        } catch (e: Exception) {
            Log.e("FaceCheck", "sendBlinkAlert failed: ${e.message}", e)
        }
    }

    private fun sendCloseAlert() {
        val uid = auth.currentUser?.uid ?: return
        if (parentId.isBlank()) return
        try {
            FirestoreUtils.sendAlertToFirebase(
                parentId,
                uid,
                "Close Screen",
                "Child ${childName.ifBlank { "Unknown" }} is too close to the screen",
                ""
            )
        } catch (e: Exception) {
            Log.e("FaceCheck", "sendCloseAlert failed: ${e.message}", e)
        }
    }

    private fun showTooCloseWarning() {
        if (!PreferenceHelper.isCloseWarningVisible()) {
            PreferenceHelper.setCloseWarningVisible(true)
            val intent = Intent(this, CloseScreenWarning::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            Handler(Looper.getMainLooper()).post {
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e("FaceCheck", "Failed to show CloseScreenWarning: ${e.message}", e)
                }
            }
        }
    }

    private fun showBlinkReminder() {
        if (!PreferenceHelper.isBlinkWarningVisible()) {
            PreferenceHelper.setBlinkWarningVisible(true)
            val intent = Intent(this, BlinkEyeWarning::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            Handler(Looper.getMainLooper()).post {
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e("FaceCheck", "Failed to show BlinkEyeWarning: ${e.message}", e)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            Log.e("FaceCheck", "onDestroy unbind failed: ${e.message}", e)
        }
        try {
            cameraExecutor.shutdown()
        } catch (e: Exception) {
            Log.e("FaceCheck", "Executor shutdown failed: ${e.message}", e)
        }
    }
}
