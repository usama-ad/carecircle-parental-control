package com.example.carecirclechildapp.Activities

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.carecirclechildapp.services.WebRTCStreamingService
import com.example.carecirclechildapp.utils.PreferenceHelper

class ScreenCapturePermissionActivity : AppCompatActivity() {
    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            // Store permission granted state
            PreferenceHelper.setScreenCapturePermissionGranted(this, true)

            // Get display metrics
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getMetrics(metrics)

            // Start WebRTCStreamingService only if not already running
            if (!isServiceRunning(WebRTCStreamingService::class.java)) {
                val serviceIntent = Intent(this, WebRTCStreamingService::class.java).apply {
                    putExtra("resultCode", result.resultCode)
                    putExtra("data", result.data)
                    putExtra("width", metrics.widthPixels)
                    putExtra("height", metrics.heightPixels)
                    putExtra("density", metrics.densityDpi)
                }
                Log.d("ScreenshotPermission", "Data: ${result.data}, ResultCode: ${result.resultCode}")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try {
                        ContextCompat.startForegroundService(this, serviceIntent)
                    } catch (e: SecurityException) {
                        Log.e("ScreenshotPermission", "Failed to start service: ${e.message}")
                        finish()
                        return@registerForActivityResult
                    }
                } else {
                    startService(serviceIntent)
                }
            } else {
                Log.w("ScreenshotPermission", "Service already running")
            }

            // Proceed to MainActivity
            val mainIntent = Intent(this, MainActivity::class.java)
            mainIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(mainIntent)
            finish()
        } else {
            Log.w("ScreenshotPermission", "Screen capture permission denied")
            // Store permission denied state
            PreferenceHelper.setScreenCapturePermissionGranted(this, false)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check Android version and permission state
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE && // API 34 (Android 14)
            PreferenceHelper.isScreenCapturePermissionGranted(this)
        ) {
            // For older Android versions with permission already granted, skip permission request
            val mainIntent = Intent(this, MainActivity::class.java)
            mainIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(mainIntent)
            finish()
            return
        }

        // Request screen capture permission
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val captureIntent = projectionManager.createScreenCaptureIntent()
        screenCaptureLauncher.launch(captureIntent)
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }
}