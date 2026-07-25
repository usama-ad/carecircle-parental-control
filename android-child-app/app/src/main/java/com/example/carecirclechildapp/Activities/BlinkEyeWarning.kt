package com.example.carecirclechildapp.Activities

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.carecirclechildapp.R
import com.example.carecirclechildapp.databinding.ActivityBlinkEyeWarningBinding
import com.example.carecirclechildapp.utils.PreferenceHelper

class BlinkEyeWarning : AppCompatActivity() {
    private lateinit var binding: ActivityBlinkEyeWarningBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityBlinkEyeWarningBinding.inflate(layoutInflater)
        setContentView(binding.root)
        PreferenceHelper.setBlinkWarningVisible(true)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }


        binding.gotItButton.setOnClickListener {
            PreferenceHelper.setBlinkWarningVisible(false)
            finish()
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                PreferenceHelper.setBlinkWarningVisible(false)
                finish()
            }
        })
    }

    override fun onStop() {
        super.onStop()
        PreferenceHelper.setBlinkWarningVisible(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        PreferenceHelper.setBlinkWarningVisible(false)
    }



}