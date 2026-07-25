package com.example.carecircleparentapp.Activities

import android.annotation.SuppressLint
import android.app.TimePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.carecircleparentapp.R
import com.example.carecircleparentapp.adapters.AppsAdapter
import com.example.carecircleparentapp.databinding.ActivityAppSettingsActivityBinding
import com.example.carecircleparentapp.modals.AppItem
import com.example.carecircleparentapp.utils.FirebaseUtils
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.*

class AppSettingsActivity : AppCompatActivity() {
    private lateinit var tvSchedule: TextView

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private lateinit var switchEyeStrain: com.google.android.material.switchmaterial.SwitchMaterial

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private lateinit var switchFaceDistance: com.google.android.material.switchmaterial.SwitchMaterial

    private lateinit var btnSave: Button
    private lateinit var adapter: AppsAdapter
    private lateinit var appsList: ArrayList<AppItem>
    private val db = FirebaseFirestore.getInstance()
    private lateinit var selectedChildId: String
    private var startTime: String = "00:00"
    private var endTime: String = "00:00"
    private var isLocked: Boolean = false
    private lateinit var binding: ActivityAppSettingsActivityBinding

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("AppSettings", "onCreate: started")
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityAppSettingsActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        selectedChildId = intent.getStringExtra("childId").toString()
        Log.d("AppSettings", "onCreate: selectedChildId=$selectedChildId")
        binding.selectedChild.text = selectedChildId
        tvSchedule = binding.tvSchedule
        switchEyeStrain = binding.switchEyeStrain
        switchFaceDistance = binding.switchFaceDistance
        btnSave = binding.saveChangesButton
        appsList = ArrayList()
        adapter = AppsAdapter(this, appsList, selectedChildId)

        // Set initial lock button text based on isLocked state
        binding.lockButton.text = if (isLocked) "Unlock" else "Lock"

        setupSchedulePicker()
        binding.installedAppsProgressBar.visibility = View.VISIBLE
        loadSettings()
        setupLockButton()
        fetchApps()
        setupSaveButton()
    }

    @SuppressLint("SetTextI18n")
    private fun setupSchedulePicker() {
        tvSchedule.text = "$startTime - $endTime"
        tvSchedule.setOnClickListener {
            pickTime(true) { start ->
                startTime = start
                pickTime(false) { end ->
                    endTime = end
                    tvSchedule.text = "${formatForDisplay(startTime)} - ${formatForDisplay(endTime)}"
                }
            }
        }
    }


    @SuppressLint("DefaultLocale")
    private fun pickTime(isStart: Boolean, callback: (String) -> Unit) {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)

        val dialog = TimePickerDialog(
            this,
            { _, h, m ->
                // Always store in 24h "HH:mm"
                val stored = String.format("%02d:%02d", h, m)
                callback(stored)
            },
            hour,
            minute,
            false
        )
        dialog.show()
    }


    private fun formatForDisplay(time24: String): String {
        return try {
            val sdf24 = SimpleDateFormat("HH:mm", Locale.getDefault())
            val sdf12 = SimpleDateFormat("hh:mm a", Locale.getDefault())
            val date = sdf24.parse(time24)
            if (date != null) sdf12.format(date) else time24
        } catch (e: Exception) {
            time24
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun fetchApps() {
        val installedAppsRef = db.collection("children_data")
            .document(selectedChildId)
            .collection("deviceInfo")
            .document("installedApps")

        val restrictedAppsRef = db.collection("children_data")
            .document(selectedChildId)
            .collection("restricted_apps")

        restrictedAppsRef.get().addOnSuccessListener { restrictedSnapshot ->
            if (isFinishing || isDestroyed) return@addOnSuccessListener
            val restrictedPackages = restrictedSnapshot.documents.map { it.id }.toSet()
            Log.d("AppSettings", "Restricted apps fetched: ${restrictedPackages.size}")

            installedAppsRef.get().addOnSuccessListener { installedDoc ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener
                val apps = installedDoc.get("apps") as? List<Map<String, String>> ?: emptyList()
                appsList.clear()

                for (appMap in apps) {
                    val name = appMap["name"] ?: ""
                    val pkg = appMap["package"] ?: ""
                    val isRestricted = restrictedPackages.contains(pkg)
                    appsList.add(AppItem(name, pkg, isRestricted))
                }

                adapter = AppsAdapter(this, appsList, selectedChildId)
                binding.childAppsRv.adapter = adapter
                binding.childAppsRv.layoutManager = LinearLayoutManager(this)
                binding.childAppsRv.isNestedScrollingEnabled = false
                adapter.notifyDataSetChanged()
                binding.installedAppsProgressBar.visibility = View.GONE
            }.addOnFailureListener { e ->
                if (!isFinishing && !isDestroyed) {
                    binding.installedAppsProgressBar.visibility = View.GONE
                    Log.e("AppSettings", "Failed to fetch installed apps: ${e.message}")
                    Toast.makeText(this, "Failed to load installed apps", Toast.LENGTH_SHORT).show()
                }
            }
        }.addOnFailureListener { e ->
            if (!isFinishing && !isDestroyed) {
                binding.installedAppsProgressBar.visibility = View.GONE
                Log.e("AppSettings", "Failed to fetch restricted apps: ${e.message}")
                Toast.makeText(this, "Failed to load restricted apps", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun loadSettings() {
        db.collection("children_data").document(selectedChildId)
            .collection("settings").document("device")
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val schedule = document.get("schedule") as? Map<*, *>
                    val alerts = document.get("alerts") as? Map<*, *>
                    isLocked = document.getBoolean("lockScreen") ?: false

                    switchEyeStrain.isChecked = alerts?.get("eyeStrain") as? Boolean ?: false
                    switchFaceDistance.isChecked = alerts?.get("faceDistance") as? Boolean ?: false
                    binding.switchRestrictedApp.isChecked = alerts?.get("restrictedApp") as? Boolean ?: false

                    startTime = schedule?.get("start") as? String ?: ""
                    endTime = schedule?.get("end") as? String ?: ""
                    binding.tvSchedule.text = "${formatForDisplay(startTime)} - ${formatForDisplay(endTime)}"
                    binding.lockButton.text = if (isLocked) "Unlock" else "Lock"
                }
            }
            .addOnFailureListener { e ->
                Log.e("AppSettings", "Error loading settings: ${e.message}")
            }
    }

    private fun setupSaveButton() {
        btnSave.setOnClickListener {
            val settings = hashMapOf(
                "schedule" to hashMapOf(
                    "start" to startTime, // stored in 24h format
                    "end" to endTime
                ),
                "alerts" to hashMapOf(
                    "eyeStrain" to switchEyeStrain.isChecked,
                    "faceDistance" to switchFaceDistance.isChecked,
                    "restrictedApp" to binding.switchRestrictedApp.isChecked
                )
            )

            db.collection("children_data").document(selectedChildId)
                .collection("settings").document("device")
                .set(settings, SetOptions.merge())
                .addOnSuccessListener {
                    if (!isFinishing && !isDestroyed) {
                        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    if (!isFinishing && !isDestroyed) {
                        Log.e("AppSettings", "Error saving settings: ${e.message}")
                        Toast.makeText(this, "Failed to save", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setupLockButton() {
        binding.lockButton.setOnClickListener {
            isLocked = !isLocked // Toggle the lock state
            val lockCommand = hashMapOf(
                "lockScreen" to isLocked,
                "timestamp" to System.currentTimeMillis()
            )

            db.collection("children_data").document(selectedChildId)
                .collection("settings").document("device")
                .set(lockCommand, SetOptions.merge())
                .addOnSuccessListener {
                    if (!isFinishing && !isDestroyed) {
                        binding.lockButton.text = if (isLocked) "Unlock" else "Lock"
                        Toast.makeText(this, if (isLocked) "Screen locked" else "Screen unlocked", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    if (!isFinishing && !isDestroyed) {
                        isLocked = !isLocked // Revert the state on failure
                        Log.e("AppSettings", "Error updating lock state: ${e.message}")
                        Toast.makeText(this, "Failed to update lock state", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }
}