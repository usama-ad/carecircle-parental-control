package com.example.carecirclechildapp.modals

data class AppUsageData(
    val appName: String = "",
    val packageName: String = "",
    val usageTimeMillis: Long = 0L,
    val lastUsedTime: Long = 0L,
    val date: String = ""
)