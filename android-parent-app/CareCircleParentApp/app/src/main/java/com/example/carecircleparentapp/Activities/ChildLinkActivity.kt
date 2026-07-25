package com.example.carecircleparentapp.Activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.carecircleparentapp.R
import com.example.carecircleparentapp.databinding.ActivityChildLinkBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class ChildLinkActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChildLinkBinding
    private val db = FirebaseFirestore.getInstance()

    private val qrLauncher = registerForActivityResult(ScanContract()) {
        if (it.contents != null) {
            val parentUid = FirebaseAuth.getInstance().currentUser?.uid
            val childUid = it.contents.trim()
            if (parentUid != null && childUid.isNotEmpty()) {
                linkChild(parentUid, childUid)
            } else {
                Toast.makeText(this, "Failed to Link Child", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityChildLinkBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Manual entry button
        binding.btnLinkChild.setOnClickListener {
            val parentUid = FirebaseAuth.getInstance().currentUser?.uid
            val childUid = binding.inputChildUid.text.toString().trim()
            if (parentUid != null && childUid.isNotEmpty()) {
                linkChild(parentUid, childUid)
            } else {
                Toast.makeText(this, "Please enter a valid Child ID", Toast.LENGTH_SHORT).show()
            }
        }

        // QR scanner button
        binding.btnScanQr.setOnClickListener {
            val options = ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt("Scan the Child's QR Code")
                setCameraId(0)
                setBeepEnabled(true)
                setBarcodeImageEnabled(true)
            }
            qrLauncher.launch(options)
        }
    }

    private fun linkChild(parentUid: String, childUid: String) {
        Log.d("ChildLinkActivity", "Checking Child UID: $childUid")

        db.collection("children_data").document(childUid).collection("profile").get().addOnSuccessListener { snapshots ->
            if (snapshots.isEmpty) {
                Toast.makeText(this, "Invalid Child ID. No such child exists.", Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }

            db.collection("children_data")
                    .document(childUid)
                    .collection("parent")
                    .get()
                    .addOnSuccessListener { snapshot ->
                        if (!snapshot.isEmpty) {
                            Toast.makeText(
                                this,
                                "This child is already linked to a parent.",
                                Toast.LENGTH_LONG
                            ).show()
                            return@addOnSuccessListener
                        }

                        // Proceed to link
                        val parentData = hashMapOf("uid" to parentUid)
                        val childData = hashMapOf("uid" to childUid)

                        db.collection("parents").document(parentUid)
                            .collection("children").document(childUid).set(childData)

                        db.collection("children_data").document(childUid)
                            .collection("parent").document(parentUid).set(parentData)
                            .addOnSuccessListener {
                                Toast.makeText(
                                    this,
                                    "Child Linked Successfully",
                                    Toast.LENGTH_LONG
                                ).show()
                                startActivity(Intent(this, MainActivity::class.java))
                                finish()
                            }
                            .addOnFailureListener {
                                Toast.makeText(
                                    this,
                                    "Failed to Link Child",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Error checking child link", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error verifying child", Toast.LENGTH_SHORT).show()
            }
    }
}
