package com.example.carecirclechildapp.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.carecirclechildapp.utils.FirestoreUtils
import com.example.carecirclechildapp.utils.PreferenceHelper

class ScreenReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val currentTime = System.currentTimeMillis()

        when (action) {
            Intent.ACTION_SCREEN_ON -> {
                PreferenceHelper.setScreenOnTime(currentTime)
                Log.d("ScreenReceiver", "Screen ON at $currentTime")
            }

            Intent.ACTION_SCREEN_OFF -> {
                val onTime = PreferenceHelper.getScreenOnTime()
                val sessionDuration = currentTime - onTime
                PreferenceHelper.addScreenTimeToday(sessionDuration)
                Log.d("ScreenReceiver", "Screen OFF, session duration: $sessionDuration ms")
            }

            Intent.ACTION_USER_PRESENT -> {
                Log.d("ScreenReceiver", "User unlocked device")
            }
        }
    }
}
