package com.example.carecirclechildapp.Activities

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.carecirclechildapp.databinding.ActivityParentLinkBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import androidx.core.graphics.set
import androidx.core.graphics.createBitmap

class ParentLinkActivity : AppCompatActivity() {
    private lateinit var binding: ActivityParentLinkBinding
    private var childId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityParentLinkBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // ✅ Safe UID fetch
        childId = FirebaseAuth.getInstance().currentUser?.uid
        if (childId.isNullOrEmpty()) {
            Toast.makeText(this, "Error: No user found. Please log in again.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        binding.tvDeviceId.text = childId

        // ✅ Copy UID
        binding.btnCopyId.setOnClickListener {
            val uid = binding.tvDeviceId.text.toString().trim()
            if (uid.isNotEmpty()) {
                val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clipData = android.content.ClipData.newPlainText("childUid", uid)
                clipboardManager.setPrimaryClip(clipData)
                Toast.makeText(this, "Device ID copied to clipboard", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Device ID not available", Toast.LENGTH_SHORT).show()
            }
        }

        // ✅ Show QR
        binding.btnShowQr.setOnClickListener {
            val uid = childId ?: ""
            if (uid.isEmpty()) {
                Toast.makeText(this, "Error: Cannot generate QR for empty ID", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showQrCode(uid)?.let {
                binding.ivQrCode.setImageBitmap(it)
                binding.ivQrCode.visibility = View.VISIBLE
                binding.tvQrInstruction.visibility = View.VISIBLE
            } ?: run {
                Toast.makeText(this, "Failed to generate QR code", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showQrCode(childId: String): Bitmap? {
        return try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(childId, BarcodeFormat.QR_CODE, 512, 512)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap[x, y] = if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                }
            }
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
