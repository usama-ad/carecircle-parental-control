package com.example.carecircleparentapp.Activities

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment // <-- Add this import
import androidx.navigation.ui.setupWithNavController
import com.example.carecircleparentapp.R
import com.example.carecircleparentapp.databinding.ActivityMainBinding
import com.example.carecircleparentapp.utils.FirebaseUtils
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()
        val navView: BottomNavigationView = binding.navView

        // FIX: Retrieve the NavController via the NavHostFragment
        // instead of using findNavController() directly
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        val navController = navHostFragment.navController

        navView.setupWithNavController(navController)

        navView.selectedItemId = R.id.dashboardFragment
        FirebaseUtils.getFCMToken {
            Log.d("TokenCheck", "onCreate: $it")
        }


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }
    }
}