package com.example.carecirclechildapp.services

import android.app.*
import android.content.Intent
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.carecirclechildapp.Activities.RestrictedAppActivity
import com.example.carecirclechildapp.utils.AppUsageHelper
import com.example.carecirclechildapp.utils.FirestoreUtils
import com.example.carecirclechildapp.utils.PreferenceHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration

class AppUsageMonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var restrictedApps: List<String> = emptyList()
    private val checkInterval: Long = 2000 // 2 seconds
    private val uploadInterval: Long = 15 * 60 * 1000 // 15 minutes
    private var lastUploadTime = 0L
    private var listenerRegistration: ListenerRegistration? = null
    private var parentId: String = ""
    private var childName: String = ""
    private val childId = FirebaseAuth.getInstance().currentUser?.uid

    override fun onCreate() {
        super.onCreate()
        try {
            Log.d("CrashTest", "AppUsageMonitorService created")

            FirestoreUtils.getParent { fetchedParent ->
                parentId = fetchedParent ?: ""
                Log.d("parentCheck", "onCreate: $parentId")
            }

            FirestoreUtils.getChildName { fetchedName ->
                childName = fetchedName ?: ""
                Log.d("parentCheck", "onCreate: $childName")
            }

            startForegroundService()
            startMonitoring()

        } catch (e: Exception) {
            Log.e("CrashTest", "Service crash: ${e.message}", e)
        }
    }

    private fun startMonitoring() {
        Log.d("servicecheck", "startMonitoring: monitoring started")
        listenerRegistration = FirestoreUtils.getRestrictedApps { fetchedList ->
            restrictedApps = fetchedList ?: emptyList()
            Log.d("RestrictedApps", "Fetched: $restrictedApps")
        }

        handler.post(object : Runnable {
            override fun run() {
                try {
                    val usageStats = AppUsageHelper.getUsageStats(this@AppUsageMonitorService) ?: emptyList()
                    val topApp = AppUsageHelper.getTopForegroundApp(this@AppUsageMonitorService)

                    Log.d("servicecheck", "Usage Stats count: ${usageStats.size}")
                    Log.d("servicecheck", "Top App: $topApp")

                    // Upload usage every 15 mins
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastUploadTime > uploadInterval) {
                        Log.d("servicecheck", "Uploading usage stats")
                        FirestoreUtils.uploadUsageStats(usageStats)
                        Log.d("servicecheck", "Usage stats uploaded")
                        lastUploadTime = currentTime
                    }

                    // Check for restricted app
                    if (!restrictedApps.isNullOrEmpty() && !topApp.isNullOrBlank()) {
                        if (restrictedApps.contains(topApp)) {
                            val appName = AppUsageHelper.getAppNameFromPackage(this@AppUsageMonitorService, topApp)
                            Log.d("servicecheck", "Restricted app detected: $appName")
                            Log.d("servicecheck", "Warning visible: ${PreferenceHelper.isRestrictedAppWarningVisible()}")

                            if (!PreferenceHelper.isRestrictedAppWarningVisible()) {
                                if (!parentId.isNullOrBlank() && !childId.isNullOrBlank()) {
                                    FirestoreUtils.sendAlertToFirebase(
                                        parentId,
                                        childId!!,
                                        "Restricted App Opened",
                                        "Child ${childName.ifBlank { "Unknown" }} tried to access $appName",
                                        topApp
                                    )
                                    showRestrictedAppWarning(topApp)
                                } else {
                                    Log.e("servicecheck", "Parent ID or Child ID is null/blank! Alert not sent.")
                                }
                            }
                        }
                    }

                } catch (e: Exception) {
                    Log.e("servicecheck", "Error in monitoring loop: ${e.message}", e)
                }

                handler.postDelayed(this, checkInterval)
            }
        })
    }

    private fun showRestrictedAppWarning(packageName: String) {
        try {
            val appName = AppUsageHelper.getAppNameFromPackage(this@AppUsageMonitorService, packageName)
            PreferenceHelper.setRestrictedAppWarningVisible(true)

            val intent = Intent(this, RestrictedAppActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("packageName", appName)
            }

            Handler(Looper.getMainLooper()).post {
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e("servicecheck", "Failed to start RestrictedAppActivity: ${e.message}", e)
                }
            }

        } catch (e: Exception) {
            Log.e("servicecheck", "showRestrictedAppWarning failed: ${e.message}", e)
        }
    }

    private fun startForegroundService() {
        Log.d("servicecheck", "startForegroundService: foreground started")
        val channelId = "app_usage_monitor_channel"
        val channelName = "App Usage Monitoring"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("App Usage Monitor")
            .setContentText("Monitoring app usage and restrictions")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .build()

        startForeground(102, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        listenerRegistration?.remove()
        handler.removeCallbacksAndMessages(null)
    }
}
