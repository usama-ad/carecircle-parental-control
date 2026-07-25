package com.example.carecirclechildapp.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.carecirclechildapp.Activities.LockScreenActivity
import com.example.carecirclechildapp.Activities.ManualLockScreen
import com.example.carecirclechildapp.utils.FirestoreUtils
import com.example.carecirclechildapp.utils.PreferenceHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ManualLockService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var parentId: String
    private lateinit var childName: String
    private var isAlertSent = false
    private var parentIdFetched = false
    private var childNameFetched = false

    private var isLockScreenActive = false
    private var lockListener: ListenerRegistration? = null

    // States stored from Firestore
    private var isManuallyLocked = false
    private var scheduleStart = "00:00"
    private var scheduleEnd = "00:00"

    override fun onCreate() {
        super.onCreate()
        Log.d("ManualLockService", "Unified Service started")
        startForegroundService()
        FirestoreUtils.getParent {
            parentId = it
            parentIdFetched = true
            maybeStartObserving()
        }
        FirestoreUtils.getChildName {
            childName = it
            childNameFetched = true
            maybeStartObserving()
        }

        // Start the timer that checks the clock every minute for the schedule
        handler.post(scheduleChecker)
    }

    private fun maybeStartObserving() {
        if (parentIdFetched && childNameFetched) {
            observeLockStatus()
        }
    }

    // 1. Instantly reacts to Firestore changes (Parent presses save)
    private fun observeLockStatus() {
        val childId = FirebaseAuth.getInstance().currentUser!!.uid
        lockListener = FirebaseFirestore.getInstance()
            .collection("children_data")
            .document(childId)
            .collection("settings")
            .document("device")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("ManualLockService", "Error checking lock status", error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    isManuallyLocked = snapshot.getBoolean("lockScreen") ?: false

                    val schedule = snapshot.get("schedule") as? Map<*, *>
                    scheduleStart = schedule?.get("start") as? String ?: "00:00"
                    scheduleEnd = schedule?.get("end") as? String ?: "00:00"

                    evaluateLockState()
                }
            }
    }

    // 2. Checks the clock every 60 seconds to see if a schedule started/ended
    private val scheduleChecker = object : Runnable {
        override fun run() {
            evaluateLockState()
            handler.postDelayed(this, 60000) // Check every 1 minute
        }
    }

    // 3. The master logic: Should the device be locked right now?
    private fun evaluateLockState() {
        val isScheduleActive = isTimeInSchedule(scheduleStart, scheduleEnd)
        val shouldBeLocked = isManuallyLocked || isScheduleActive

        if (shouldBeLocked && !isLockScreenActive) {
            if (!isAlertSent) {
                val reason = if (isManuallyLocked) "manually locked" else "locked by schedule"
                FirestoreUtils.sendAlertToFirebase(
                    parentId,
                    FirebaseAuth.getInstance().currentUser!!.uid,
                    "Screen Locked",
                    "Child's $childName screen has been $reason",
                    ""
                )
                isAlertSent = true
            }
            // Pass the data here! Manual takes priority over schedule.
            showLockScreen(isManuallyLocked, scheduleEnd)
            isLockScreenActive = true

        } else if (!shouldBeLocked && isLockScreenActive) {
            isAlertSent = false
            dismissLockScreen()
            isLockScreenActive = false
        }
    }

    // Helper: Checks if current time is between start and end time
    private fun isTimeInSchedule(start: String, end: String): Boolean {
        if (start == "00:00" && end == "00:00") return false

        return try {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            val now = Calendar.getInstance()
            val currentTime = String.format("%02d:%02d", now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))

            val startTime = sdf.parse(start)
            val endTime = sdf.parse(end)
            val current = sdf.parse(currentTime)

            if (startTime != null && endTime != null && current != null) {
                if (startTime.before(endTime)) {
                    // Standard schedule (e.g., 14:00 to 18:00)
                    current.after(startTime) && current.before(endTime)
                } else {
                    // Overnight schedule crossing midnight (e.g., 22:00 to 06:00)
                    current.after(startTime) || current.before(endTime)
                }
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    // Update the method signature to accept the lock type and end time
    private fun showLockScreen(isManual: Boolean, endTime: String = "00:00") {
        val intent = Intent(this, LockScreenActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
            // Pass the data to the Activity
            putExtra("IS_MANUAL_LOCK", isManual)
            putExtra("SCHEDULE_END_TIME", endTime)
        }
        PreferenceHelper.setManualLockScreenVisible(true)
        startActivity(intent)
    }

    private fun dismissLockScreen() {
        PreferenceHelper.setLockScreenVisible(false)
        val intent = Intent("com.example.carecirclechildapp.ACTION_UNLOCK")
        sendBroadcast(intent)
    }

    private fun startForegroundService() {
        val channelId = "lock_monitor_channel"
        val channelName = "Device Lock Service"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_MIN
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("CareCircle Monitoring")
            .setContentText("Keeping device rules active")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()

        startForeground(106, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        lockListener?.remove()
        handler.removeCallbacks(scheduleChecker)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}