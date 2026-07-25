package com.example.carecirclechildapp.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

object NotificationHelper {
    private const val CHANNEL_ID = "carecircle_alerts"
    fun showNotification(context: Context,title: String,message: String){
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            val channel = NotificationChannel(CHANNEL_ID,"CareCircle Alerts" , NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(
            context,CHANNEL_ID
        )
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true).build()

        manager.notify(System.currentTimeMillis().toInt(),notification)
    }


}