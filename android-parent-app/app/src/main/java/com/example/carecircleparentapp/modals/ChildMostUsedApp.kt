package com.example.carecircleparentapp.modals

data class ChildMostUsedApp(
    val childName: String,
    val childId: String,
    val mostUsedAppName: String?,
    val mostUsedMillis: Long,
    val totalScreenTimeMillis: Long
)
