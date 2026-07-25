package com.example.carecirclechildapp.Activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.navigation.findNavController
import com.google.firebase.firestore.ListenerRegistration
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.navigation.ui.setupWithNavController
import com.example.carecirclechildapp.R
import com.example.carecirclechildapp.databinding.ActivityMainBinding
import com.example.carecirclechildapp.services.AppUsageMonitorService
import com.example.carecirclechildapp.services.FaceDistanceService
import com.example.carecirclechildapp.services.LockMonitorService
import com.example.carecirclechildapp.services.ManualLockService
import com.example.carecirclechildapp.services.ScreenTimeService
import com.example.carecirclechildapp.utils.FirestoreUtils
//import com.example.carecirclechildapp.utils.ScreenTimeUploadScheduler
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var lockStatus: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Setup view binding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
      supportActionBar?.hide()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }
        // Setup navigation
        val navView: BottomNavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        navView.setupWithNavController(navController)

        startAllServices()

        FirestoreUtils.uploadInstalledApps(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }

    }


    override fun onDestroy() {
        super.onDestroy()
    }
    private fun startAllServices() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this,Intent(this, FaceDistanceService::class.java))
            ContextCompat.startForegroundService(this,Intent(this, AppUsageMonitorService::class.java))
            ContextCompat.startForegroundService(this, Intent(this, ScreenTimeService::class.java))
//            ContextCompat.startForegroundService(this,Intent(this, LockMonitorService::class.java))
            ContextCompat.startForegroundService(this, Intent(this, ManualLockService::class.java))
        } else {
            startService(Intent(this, FaceDistanceService::class.java))
            startService(Intent(this, AppUsageMonitorService::class.java))
            startService(Intent(this, ScreenTimeService::class.java))
//            startService(Intent(this, LockMonitorService::class.java))
            startService(Intent(this, ManualLockService::class.java))
        }
    }
}
