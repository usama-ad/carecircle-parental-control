package com.example.carecirclechildapp.Activities

import android.os.Bundle
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.carecirclechildapp.databinding.ActivityCloseScreenWarningBinding
import com.example.carecirclechildapp.utils.PreferenceHelper

class CloseScreenWarning : AppCompatActivity() {
    private lateinit var binding: ActivityCloseScreenWarningBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityCloseScreenWarningBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Log.d("Warning", "CloseScreenWarning launched")

        PreferenceHelper.setCloseWarningVisible(true)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.gotItButton.setOnClickListener {
            PreferenceHelper.setCloseWarningVisible(false)
            finish()

        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                PreferenceHelper.setCloseWarningVisible(false)
                finish()
            }
        })
    }

    override fun onStop() {
        super.onStop()
        PreferenceHelper.setCloseWarningVisible(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        PreferenceHelper.setCloseWarningVisible(false)
    }
}
