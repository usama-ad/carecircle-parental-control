package com.example.carecirclechildapp.services

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.Log
import android.view.Surface
import androidx.core.app.NotificationCompat
import com.example.carecirclechildapp.webrtc.FirebaseSignalingClient
import livekit.org.webrtc.DefaultVideoDecoderFactory
import livekit.org.webrtc.DefaultVideoEncoderFactory
import livekit.org.webrtc.EglBase
import livekit.org.webrtc.PeerConnectionFactory
import livekit.org.webrtc.ScreenCapturerAndroid
import livekit.org.webrtc.SurfaceTextureHelper
import livekit.org.webrtc.VideoCapturer
import livekit.org.webrtc.VideoSource
import livekit.org.webrtc.VideoTrack

class WebRTCStreamingService : Service() {
    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private var mediaProjection: MediaProjection? = null
    private var videoCapturer: VideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private lateinit var eglBase: EglBase
    private var surface: Surface? = null
    private var isCapturing = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("FirebaseSignaling", "onStartCommand: service started")
        startForegroundService()
        setupWebRTC()
        initProjection(intent)
        return START_STICKY
    }

    private fun setupWebRTC() {
        eglBase = EglBase.create()
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(this)
                .createInitializationOptions()
        )
        val options = PeerConnectionFactory.Options()
        val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()

        videoSource = peerConnectionFactory.createVideoSource(false)
        videoTrack = peerConnectionFactory.createVideoTrack("screen_track", videoSource!!)

        // Use the same signaling client (which has the Twilio servers list inside)
        FirebaseSignalingClient.init(peerConnectionFactory, videoTrack!!, isCaller = true)
    }

    private fun initProjection(intent: Intent?) {
        if (intent == null) {
            stopSelf()
            return
        }

        if (isCapturing) return

        val resultCode = intent.getIntExtra("resultCode", Activity.RESULT_CANCELED)
        val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("data", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("data")
        }

        if (resultCode != Activity.RESULT_OK || data == null) {
            stopSelf()
            return
        }

        try {
            val surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
            surface = Surface(surfaceTextureHelper.surfaceTexture)

            videoCapturer = ScreenCapturerAndroid(data, object : MediaProjection.Callback() {
                override fun onStop() {
                    isCapturing = false
                    stopSelf()
                }
            })

            videoCapturer!!.initialize(surfaceTextureHelper, this, videoSource!!.capturerObserver)

            val metrics = resources.displayMetrics
            surfaceTextureHelper.setTextureSize(metrics.widthPixels, metrics.heightPixels)
            videoCapturer!!.startCapture(metrics.widthPixels, metrics.heightPixels, 30)

            isCapturing = true
        } catch (e: Exception) {
            Log.e("WebRTC", "Error initializing MediaProjection", e)
            stopSelf()
        }
    }

    private fun startForegroundService() {
        val channelId = "screen_share_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Screen Sharing", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Screen Sharing Active")
            .setContentText("Sharing screen with parent.")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(201, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(201, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            videoCapturer?.stopCapture()
        } catch (e: Exception) {
            Log.e("WebRTC", "Error stopping capture: ${e.message}", e)
        }

        videoCapturer?.dispose()
        videoSource?.dispose()
        surface?.release()
        mediaProjection?.stop()
        FirebaseSignalingClient.close()
        isCapturing = false
        eglBase.release()
        Log.d("WebRTC", "Service destroyed and resources released")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
