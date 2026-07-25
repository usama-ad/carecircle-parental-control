package com.example.carecirclechildapp.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.carecirclechildapp.utils.FirestoreUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class ScreenTimeService : Service() {
    private lateinit var executor: ScheduledExecutorService
    private val db = FirebaseFirestore.getInstance()
    private val childUid = FirebaseAuth.getInstance().currentUser?.uid
    private var lastUploadTime: Long = 0L
    private var accumulatedMinutes: Long = 0L
    private var currentDate: String? = null
    override fun onCreate() {
        super.onCreate()
        Log.d("ScreenTimeService", "Service created")
        startForegroundService()
        executor = Executors.newSingleThreadScheduledExecutor()
        Log.d("ScreenTimeService", "Child UID: $childUid")
        currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault()
        }.format(Date())
        // Schedule uploads every 5 minutes
        executor.scheduleWithFixedDelay({ uploadScreenTime() }, 0, 5, TimeUnit.MINUTES)
    }

    private fun startForegroundService() {
        val channelId = "screen_time_channel"
        val channelName = "Screen Time Tracker"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, channelName, NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Screen Time Tracking")
            .setContentText("Monitoring device usage time")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .build()

        startForeground(103, notification)
    }

    private fun uploadScreenTime() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault()
        }.format(Date())

        // Reset if day changed
        if (today != currentDate) {
            accumulatedMinutes = 0L
            lastUploadTime = 0L
            currentDate = today
            Log.d("ScreenTimeService", "Reset screen time for new day: $today")
        }

        // Calculate screen time since last upload
        val minutes = calculateScreenTime()
        accumulatedMinutes += minutes

        // Cap to elapsed time since midnight
        val calendar = Calendar.getInstance()
        val elapsedMinutesToday = (calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)).toLong()
        val cappedMinutes = minOf(accumulatedMinutes, elapsedMinutesToday)

        if (childUid != null) {
            db.collection("children_data")
                .document(childUid)
                .collection("screen_time")
                .document(today)
                .set(mapOf("screenTime" to cappedMinutes))
                .addOnSuccessListener {
                    Log.d("ScreenTimeService", "Uploaded $cappedMinutes minutes for $today")
                    lastUploadTime = System.currentTimeMillis()
                }
                .addOnFailureListener { e ->
                    Log.e("ScreenTimeService", "Error uploading for $today: $e")
                }
        }
    }

    private fun calculateScreenTime(): Long {
        val usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = if (lastUploadTime == 0L) endTime - 5 * 60 * 1000 else lastUploadTime

        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
        var totalTime = 0L
        stats?.forEach { stat ->
            if (stat.packageName == packageName) { // Track only this app's usage
                totalTime += stat.totalTimeInForeground / (1000 * 60) // Convert ms to minutes
            }
        }
        return totalTime
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        executor.shutdown()
        uploadScreenTime() // Final upload
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}