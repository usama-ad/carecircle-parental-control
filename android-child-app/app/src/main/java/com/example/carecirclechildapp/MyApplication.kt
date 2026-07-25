package com.example.carecirclechildapp
import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.carecirclechildapp.Activities.UsageAccessPermissionActivity
import com.example.carecirclechildapp.services.FaceDistanceService
import com.example.carecirclechildapp.utils.PermissionHelper
import com.example.carecirclechildapp.utils.PreferenceHelper

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PreferenceHelper.init(this)

    }

    fun hasAllPermissions(): Boolean {
        return PermissionHelper.hasCameraPermission(this) &&
                PermissionHelper.hasUsageStatsPermission(this) &&
                PermissionHelper.hasOverlayPermission(this)
    }

}
