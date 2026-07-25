package com.example.carecirclechildapp.Activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.carecirclechildapp.databinding.ActivityRestrictedAppBinding
import com.example.carecirclechildapp.utils.PreferenceHelper

class RestrictedAppActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRestrictedAppBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRestrictedAppBinding.inflate(layoutInflater)
        setContentView(binding.root)

        PreferenceHelper.setRestrictedAppWarningVisible(true)

        // ✅ Safely get restricted app name
        val restrictedApp = intent.getStringExtra("packageName") ?: "Unknown App"
        binding.restrictedAppName.text = restrictedApp

        // ✅ Warning message if app unknown
        if (restrictedApp == "Unknown App") {
            binding.restrictedAppName.visibility = View.GONE
        }

        binding.okButton.setOnClickListener {
            closeRestrictedApp()
            PreferenceHelper.setRestrictedAppWarningVisible(false)
            finish()
        }
    }

    private fun closeRestrictedApp() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        PreferenceHelper.setRestrictedAppWarningVisible(false)
    }
}
