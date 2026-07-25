package com.example.carecirclechildapp.Activities

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.example.carecirclechildapp.R
import com.example.carecirclechildapp.databinding.ActivityManualLockScreenBinding
import com.example.carecirclechildapp.utils.PreferenceHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class ManualLockScreen : AppCompatActivity() {
    private lateinit var binding: ActivityManualLockScreenBinding
    private lateinit var unlockReceiver: BroadcastReceiver
    private lateinit var firestoreListener: ListenerRegistration
    private lateinit var childId: String
    private val db = FirebaseFirestore.getInstance()
    private var isFinishingManually = false // Flag to prevent relaunch during finish

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PreferenceHelper.setManualLockScreenVisible(true)
        binding = ActivityManualLockScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        childId = FirebaseAuth.getInstance().currentUser!!.uid
        if (childId.isEmpty()) {
            Log.e("ManualLockScreen", "Child ID not found, finishing activity")
            Toast.makeText(this, "Error: Child ID not found", Toast.LENGTH_SHORT).show()
            finishManually()
            return
        }

        // Make activity full-screen and show over lock screen
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        hideSystemUI()

        // Disable back button
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Do nothing
            }
        })

        // Disable predictive back gesture for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) { /* Do nothing */ }
        }

        // Register receiver for unlock broadcast
        unlockReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "com.example.carecirclechildapp.ACTION_UNLOCK") {
                    Log.d("ManualLockScreen", "Unlock broadcast received")
                    finishManually()
                }
            }
        }
        registerReceiver(
            unlockReceiver,
            IntentFilter("com.example.carecirclechildapp.ACTION_UNLOCK"),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) RECEIVER_EXPORTED else 0
        )

        // Setup Firestore real-time listener
        setupFirestoreListener()
    }

    private fun setupFirestoreListener() {
        firestoreListener = db.collection("children_data")
            .document(childId)
            .collection("settings")
            .document("device")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("ManualLockScreen", "Firestore listener error: ${error.message}")
                    Toast.makeText(this, "Error listening for lock state", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val isLocked = snapshot.getBoolean("lockScreen") ?: false
                    Log.d("ManualLockScreen", "Firestore lockScreen state: $isLocked")
                    if (!isLocked && !isFinishingManually) {
                        Log.d("ManualLockScreen", "lockScreen is false, finishing activity")
                        finishManually()
                    }
                }
            }
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    WindowManager.LayoutParams.FLAG_FULLSCREEN or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    )
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!isFinishingManually) {
            moveToFront()
        }
    }

    override fun onPause() {
        super.onPause()
        // Only relaunch if not finishing and lock is still active
        if (PreferenceHelper.isManualLockScreenVisible() && !isFinishingManually) {
            bringBackLockScreen()
        }
    }

    private fun bringBackLockScreen() {
        val intent = Intent(this, ManualLockScreen::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
            putExtra("childId", childId)
        }
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isFinishingManually && PreferenceHelper.isManualLockScreenVisible()) {
                startActivity(intent)
            }
        }, 200) // Increased delay to avoid rapid relaunch
    }

    private fun moveToFront() {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        activityManager.appTasks?.forEach { it.moveToFront() }
    }

    private fun finishManually() {
        isFinishingManually = true
        PreferenceHelper.setManualLockScreenVisible(false)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        PreferenceHelper.setManualLockScreenVisible(false)
        unregisterReceiver(unlockReceiver)
        firestoreListener.remove()
    }
}