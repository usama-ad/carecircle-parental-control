package com.example.carecircleparentapp.utils

object TimeFormat {
    fun formatMillis(millis: Long): String {
        val hours = millis / (1000 * 60 * 60)
        val minutes = (millis / (1000 * 60)) % 60
        return "${hours}h ${minutes}m"
    }
}