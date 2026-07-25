package com.example.carecirclechildapp.Activities

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.window.OnBackInvokedDispatcher
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.example.carecirclechildapp.databinding.ActivityLockScreenBinding
import com.example.carecirclechildapp.utils.PreferenceHelper
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class LockScreenActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLockScreenBinding
    private var countDownTimer: CountDownTimer? = null
    private var isFinishingManually = false
    private val childId by lazy { FirebaseAuth.getInstance().currentUser?.uid ?: "" }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PreferenceHelper.setLockScreenVisible(true)

        binding = ActivityLockScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (childId.isEmpty()) {
            finishManually()
            return
        }

        setupFullScreen()
        disableBackButtons()
        setupUnlockReceiver()

        // Read the instructions sent by the Service
        handleIntentData(intent)
    }

    // If the screen is already open and receives a new command from the Service
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent?.let { handleIntentData(it) }
    }

    private fun handleIntentData(intent: Intent) {
        val isManual = intent.getBooleanExtra("IS_MANUAL_LOCK", true)
        val endTime = intent.getStringExtra("SCHEDULE_END_TIME") ?: "00:00"

        if (isManual) {
            // It's a manual lock -> Hide the timer
            binding.timerCard.visibility = View.GONE
            binding.titleText.text = "Device Locked"
            binding.instructionText.text = "This device has been locked by your parent."
            countDownTimer?.cancel()
        } else {
            // It's a scheduled lock -> Show the timer
            binding.timerCard.visibility = View.VISIBLE
            binding.titleText.text = "Time for a break"
            binding.instructionText.text = "Your device will unlock in:"
            startCountdown(endTime)
        }
    }

    private fun startCountdown(endTimeString: String) {
        countDownTimer?.cancel()

        try {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            val now = Calendar.getInstance()
            val endCalendar = Calendar.getInstance()

            val endDate = sdf.parse(endTimeString)
            if (endDate != null) {
                val endParsed = Calendar.getInstance().apply { time = endDate }
                endCalendar.set(Calendar.HOUR_OF_DAY, endParsed.get(Calendar.HOUR_OF_DAY))
                endCalendar.set(Calendar.MINUTE, endParsed.get(Calendar.MINUTE))
                endCalendar.set(Calendar.SECOND, 0)

                // If the end time is earlier than right now, it unlocks tomorrow morning
                if (endCalendar.before(now)) {
                    endCalendar.add(Calendar.DATE, 1)
                }

                val remainingMillis = endCalendar.timeInMillis - now.timeInMillis

                if (remainingMillis > 0) {
                    countDownTimer = object : CountDownTimer(remainingMillis, 1000) {
                        override fun onTick(millisUntilFinished: Long) {
                            val hours = millisUntilFinished / (1000 * 60 * 60)
                            val minutes = (millisUntilFinished % (1000 * 60 * 60)) / (1000 * 60)
                            val seconds = (millisUntilFinished % (1000 * 60)) / 1000

                            binding.hoursLeft.text = String.format("%02d", hours)
                            binding.minutesLeft.text = String.format("%02d", minutes)
                            binding.secondsLeft.text = String.format("%02d", seconds)
                        }

                        override fun onFinish() {
                            binding.hoursLeft.text = "00"
                            binding.minutesLeft.text = "00"
                            binding.secondsLeft.text = "00"
                            // The Service will automatically unlock the device when the clock hits the end time
                        }
                    }.start()
                }
            }
        } catch (e: Exception) {
            Log.e("LockScreenActivity", "Error parsing time", e)
            binding.timerCard.visibility = View.GONE
        }
    }

    private fun setupFullScreen() {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
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

    private fun disableBackButtons() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {}
        })

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) {}
        }
    }

    private fun setupUnlockReceiver() {
        val unlockReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "com.carecircle.UNLOCK_SCREEN") {
                    finishManually()
                }
            }
        }
        registerReceiver(
            unlockReceiver,
            IntentFilter("com.carecircle.UNLOCK_SCREEN"),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) RECEIVER_EXPORTED else 0
        )
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!isFinishingManually) {
            moveToFront()
        }
    }

    override fun onPause() {
        super.onPause()
        if (PreferenceHelper.isLockScreenVisible() && !isFinishingManually) {
            bringBackLockScreen()
        }
    }

    private fun bringBackLockScreen() {
        val relaunchIntent = Intent(this, LockScreenActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
            putExtra("IS_MANUAL_LOCK", intent.getBooleanExtra("IS_MANUAL_LOCK", true))
            putExtra("SCHEDULE_END_TIME", intent.getStringExtra("SCHEDULE_END_TIME"))
        }
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isFinishingManually && PreferenceHelper.isLockScreenVisible()) {
                startActivity(relaunchIntent)
            }
        }, 200)
    }

    private fun moveToFront() {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        activityManager.appTasks?.forEach { it.moveToFront() }
    }

    private fun finishManually() {
        isFinishingManually = true
        PreferenceHelper.setLockScreenVisible(false)
        countDownTimer?.cancel()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        PreferenceHelper.setLockScreenVisible(false)
        countDownTimer?.cancel()
    }
}