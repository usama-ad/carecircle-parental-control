package com.example.carecirclechildapp.utils

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.content.edit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PreferenceHelper {

    private const val PREF_NAME = "CareCirclePrefs"
    private const val KEY_FIRST_LAUNCH = "is_first_launch"
    private const val KEY_BLINK_WARNING_VISIBLE = "blink_warning_visible"
    private const val KEY_CLOSE_WARNING_VISIBLE = "close_warning_visible"
    private const val KEY_RESTRICTED_APP_WARNING_VISIBLE = "restricted_app_warning_visible"
    private const val KEY_RESTRICTED_APPS = "restricted_apps"
    private const val KEY_SCREEN_ON_TIME = "screen_on_time"
    private const val KEY_SCREEN_TIME_TODAY = "screen_time_today"
    private const val KEY_SCREEN_TIME_DATE = "screen_time_date"
    private const val KEY_LOCK_SCREEN_VISIBLE = "lock_screen_visible"
    private const val KEY_MANUAL_LOCK_VISIBLE = "manual_lock_visible"

    private lateinit var preferences: SharedPreferences

    fun init(context: Context) {
        preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }
    fun setFirstLaunch(isFirst: Boolean) {
        preferences.edit { putBoolean(KEY_FIRST_LAUNCH, isFirst) }
    }
    private const val KEY_SCREEN_CAPTURE_PERMISSION = "screen_capture_permission_granted"

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun setScreenCapturePermissionGranted(context: Context, granted: Boolean) {
        getPreferences(context).edit {
            putBoolean(KEY_SCREEN_CAPTURE_PERMISSION, granted)
        }
    }

    fun isScreenCapturePermissionGranted(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_SCREEN_CAPTURE_PERMISSION, false)
    }



    fun isBlinkWarningVisible(): Boolean {
        return preferences.getBoolean(KEY_BLINK_WARNING_VISIBLE, false)
    }

    fun setBlinkWarningVisible(visible: Boolean) {
        preferences.edit { putBoolean(KEY_BLINK_WARNING_VISIBLE, visible) }
    }

    fun isCloseWarningVisible(): Boolean {
        return preferences.getBoolean(KEY_CLOSE_WARNING_VISIBLE, false)
    }

    fun setCloseWarningVisible(visible: Boolean) {
        preferences.edit { putBoolean(KEY_CLOSE_WARNING_VISIBLE, visible) }
    }
    fun isRestrictedAppWarningVisible(): Boolean {
        return preferences.getBoolean(KEY_RESTRICTED_APP_WARNING_VISIBLE, false)
    }

    fun setRestrictedAppWarningVisible(visible: Boolean) {
        preferences.edit { putBoolean(KEY_RESTRICTED_APP_WARNING_VISIBLE, visible) }
    }

    fun setRestrictedAppsList(apps: List<String>) {
        preferences.edit {
            putStringSet(KEY_RESTRICTED_APPS, apps.toSet())
        }
    }

    private fun getCurrentDate(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    fun setScreenOnTime(time: Long) {
        preferences.edit { putLong(KEY_SCREEN_ON_TIME, time) }
    }

    fun getScreenOnTime(): Long {
        return preferences.getLong(KEY_SCREEN_ON_TIME, 0L)
    }

    fun addScreenTimeToday(durationSinceBoot: Long) {
        val currentDate = getCurrentDate()
        val storedDate = preferences.getString(KEY_SCREEN_TIME_DATE, null)

        // Reset if day changed
        if (storedDate != currentDate) {
            resetScreenTimeToday(currentDate)
            setLastRecordedBootTime(durationSinceBoot) // reset baseline
        }

        val lastRecordedBootTime = getLastRecordedBootTime()
        val diff = durationSinceBoot - lastRecordedBootTime

        if (diff > 0) { // Only add if time increased
            val current = getScreenTimeToday()
            preferences.edit { putLong(KEY_SCREEN_TIME_TODAY, current + diff) }
            setLastRecordedBootTime(durationSinceBoot) // update baseline
        }
    }

    // Helper for last recorded boot time
    private fun setLastRecordedBootTime(time: Long) {
        preferences.edit { putLong("KEY_LAST_RECORDED_BOOT_TIME", time) }
    }

    private fun getLastRecordedBootTime(): Long {
        return preferences.getLong("KEY_LAST_RECORDED_BOOT_TIME", 0L)
    }


    fun getScreenTimeToday(): Long {
        val currentDate = getCurrentDate()
        val storedDate = preferences.getString(KEY_SCREEN_TIME_DATE, null)

        // Reset if the stored date is not today
        if (storedDate != currentDate) {
            resetScreenTimeToday(currentDate)
        }
        return preferences.getLong(KEY_SCREEN_TIME_TODAY, 0L)
    }

    fun resetScreenTimeToday(newDate: String = getCurrentDate()) {
        preferences.edit {
            putLong(KEY_SCREEN_TIME_TODAY, 0L)
            putString(KEY_SCREEN_TIME_DATE, newDate)
        }
    }
    fun isLockScreenVisible(): Boolean {
        return preferences.getBoolean(KEY_LOCK_SCREEN_VISIBLE, false)
    }
    fun setManualLockScreenVisible(visible: Boolean) {
        preferences.edit { putBoolean(KEY_MANUAL_LOCK_VISIBLE, visible) }
    }

    fun isManualLockScreenVisible(): Boolean {
        return preferences.getBoolean(KEY_MANUAL_LOCK_VISIBLE, false)
    }

    fun setLockScreenVisible(visible: Boolean) {
        preferences.edit { putBoolean(KEY_LOCK_SCREEN_VISIBLE, visible) }
    }


}
