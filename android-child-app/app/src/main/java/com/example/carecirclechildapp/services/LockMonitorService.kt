package com.example.carecirclechildapp.services

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.carecirclechildapp.Activities.LockScreenActivity
import com.example.carecirclechildapp.utils.FirestoreUtils
import com.example.carecirclechildapp.utils.PreferenceHelper
import com.google.firebase.auth.FirebaseAuth

class LockMonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val lockCheckInterval = 100L
    private lateinit var  parentId : String
    private lateinit var childName : String
    private var isAlertSent = false
    private var parentIdFetched = false
    private var childNameFetched = false

    override fun onCreate() {
        super.onCreate()
        Log.d("LockMonitorService", "Service started")
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
    }


    private fun maybeStartObserving() {
        if (parentIdFetched && childNameFetched) {
            observeLockStatus()
        }
    }


    // Periodically bring lock screen to front if still active
    private val lockScreenReloader = object : Runnable {
        override fun run() {
            if (PreferenceHelper.isLockScreenVisible()) {
                showLockScreen()
                handler.postDelayed(this, lockCheckInterval)
            }
        }


    }
    private fun sendLockAlert() {
        FirestoreUtils.sendAlertToFirebase(parentId, FirebaseAuth.getInstance().currentUser!!.uid,"Lock Screen" , "Child's $childName screen has been locked", "")
    }

    private fun observeLockStatus() {
        FirestoreUtils.listenToDeviceLock { isLocked ->
            Log.d("LockMonitorService", "isLocked: $isLocked")
            if (isLocked) {
                if (!isAlertSent){
                    sendLockAlert()
                    isAlertSent = true
                }
                showLockScreen()
                handler.post(lockScreenReloader)
            } else {
                isAlertSent = false
                dismissLockScreen()
                handler.removeCallbacks(lockScreenReloader)
            }
        }
    }

    private fun showLockScreen() {
        val intent = Intent(this, LockScreenActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
        }

        if (!PreferenceHelper.isLockScreenVisible()) {
            PreferenceHelper.setLockScreenVisible(true)
        }

        startActivity(intent)
    }

    private fun dismissLockScreen() {
        PreferenceHelper.setLockScreenVisible(false)
    }

    private fun startForegroundService() {
        val channelId = "lock_monitor_channel"
        val channelName = "Lock Monitor Service"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_MIN
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Monitoring Lock Status")
            .setContentText("CareCircle lock screen is active")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()

        startForeground(105, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
