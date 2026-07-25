package com.example.carecirclechildapp.modals

data class BlinkLog(
    val timestamp: Long = 0L,
    val blinkCount: Int = 0,
    val durationWithoutBlink: Long = 0L,
    val fatigueDetected: Boolean = false
)