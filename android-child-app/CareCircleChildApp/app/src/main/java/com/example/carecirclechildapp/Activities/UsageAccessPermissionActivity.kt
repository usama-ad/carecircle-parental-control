package com.example.carecirclechildapp.Activities

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.example.carecirclechildapp.R
import com.example.carecirclechildapp.utils.PermissionHelper
import com.example.carecirclechildapp.utils.PreferenceHelper

class UsageAccessPermissionActivity : AppCompatActivity() {

    private val requestCameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_usage_access_permission)

        findViewById<Button>(R.id.grant_permission_button).setOnClickListener {
            requestAllPermissions()
        }
    }

    private fun requestAllPermissions() {
        if (!PermissionHelper.hasCameraPermission(this)) {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }

        if (!PermissionHelper.hasUsageStatsPermission(this)) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            Toast.makeText(this, "Grant Usage Access", Toast.LENGTH_SHORT).show()
        }

        if (!PermissionHelper.hasOverlayPermission(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:$packageName".toUri())
            startActivity(intent)
            Toast.makeText(this, "Grant Overlay Permission", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        if (PermissionHelper.hasCameraPermission(this)
            && PermissionHelper.hasUsageStatsPermission(this)
            && PermissionHelper.hasOverlayPermission(this)) {

            PreferenceHelper.setFirstLaunch(false)
            startActivity(Intent(this, ScreenCapturePermissionActivity::class.java))
            finish()
        }
    }
}
