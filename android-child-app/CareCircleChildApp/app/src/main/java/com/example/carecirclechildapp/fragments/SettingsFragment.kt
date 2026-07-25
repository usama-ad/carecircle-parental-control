package com.example.carecirclechildapp.fragments

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.carecirclechildapp.Activities.LoginActivity
import com.example.carecirclechildapp.Activities.ParentLinkActivity
import com.example.carecirclechildapp.databinding.FragmentSettingsBinding
import com.example.carecirclechildapp.services.AppUsageMonitorService
import com.example.carecirclechildapp.services.FaceDistanceService
import com.example.carecirclechildapp.services.LockMonitorService
import com.example.carecirclechildapp.services.ScreenTimeService
import com.example.carecirclechildapp.services.WebRTCStreamingService
import com.example.carecirclechildapp.utils.AppUsageHelper
import com.example.carecirclechildapp.utils.FirestoreUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val appsList = mutableListOf<String>()
    private var deviceLockListener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadDataFromFirebase()

        binding.btnLinkToParent.setOnClickListener {
            safeStartActivity(ParentLinkActivity::class.java)
        }

        binding.btnLogout.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setCancelable(false)
                .setPositiveButton("Yes") { dialog, _ ->
                    // Stop all running services
                    stopAllServices()
                    // Sign out from Firebase
                    FirebaseAuth.getInstance().signOut()
                    safeStartActivity(LoginActivity::class.java, clearTask = true)
                    dialog.dismiss()
                }
                .setNeutralButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }
    }

    private fun stopAllServices() {
        // Stop usage tracking or other services
        try {
            val screenServiceIntent = Intent(requireContext(), ScreenTimeService::class.java) // Adjust service class name
            val usageServiceIntent = Intent(requireContext(), AppUsageMonitorService::class.java)
            val lockMonitorService = Intent(requireContext(), LockMonitorService::class.java)
            val faceDistanceService = Intent(requireContext(), FaceDistanceService::class.java)
            val webRtc = Intent(requireContext(), WebRTCStreamingService::class.java)

            requireContext().stopService(screenServiceIntent)
            requireContext().stopService(usageServiceIntent)
            requireContext().stopService(lockMonitorService)
            requireContext().stopService(faceDistanceService)
            requireContext().stopService(webRtc)

        } catch (e: Exception) {
            e.printStackTrace()
            showErrorToast("Error stopping services")
        }
    }

    @SuppressLint("SetTextI18n")
    private fun loadDataFromFirebase() {
        if (!isAdded) return
        showLoading(true)

        // Load child name
        FirestoreUtils.getChildName { childName ->
            if (!isAdded) return@getChildName
            binding.username.text = childName
        }

        // Load child email
        FirestoreUtils.getChildEmail { childEmail ->
            if (!isAdded) return@getChildEmail
            binding.email.text = childEmail
        }

        // Listen to device lock status
        deviceLockListener = FirestoreUtils.listenToDeviceLock { isLocked ->
            if (!isAdded) return@listenToDeviceLock
            binding.tvLockStatus.text = if (isLocked) "Locked" else "Unlocked"
        }

        // Load parent info
        FirestoreUtils.getParent { parentId ->
            if (!isAdded) return@getParent
            FirebaseFirestore.getInstance().collection("parents").document(parentId)
                .get()
                .addOnSuccessListener { snapshot ->
                    if (!isAdded) return@addOnSuccessListener
                    if (snapshot == null || !snapshot.exists()) {
                        binding.btnLinkToParent.visibility = View.VISIBLE
                    } else {
                        binding.btnLinkToParent.visibility = View.INVISIBLE
                        binding.parentName.text = snapshot.getString("username")
                    }
                }
        }

        // Load restricted apps spinner
        FirestoreUtils.getRestrictedApps { restrictedApps ->
            if (!isAdded) return@getRestrictedApps
            appsList.clear()
            for (app in restrictedApps) {
                val appName = AppUsageHelper.getAppNameFromPackage(requireContext(), app)
                appsList.add(appName)
            }

            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, appsList)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerRestrictedApps.adapter = adapter

            binding.spinnerRestrictedApps.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                    if (!isAdded) return
                    val selectedItem = appsList[pos]
                    showErrorToast("$selectedItem is restricted")
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }

        // Load alert switches and schedule
        val selectedChildId = FirestoreUtils.getChildId() ?: return showLoading(false)
        FirebaseFirestore.getInstance()
            .collection("children_data")
            .document(selectedChildId)
            .collection("settings")
            .document("device")
            .get()
            .addOnSuccessListener { doc ->
                if (!isAdded) return@addOnSuccessListener
                doc.get("alerts")?.let { alertsMap ->
                    val alerts = alertsMap as Map<String, Boolean>
                    binding.tvEyeStrainAlert.isChecked = alerts["eyeStrain"] ?: false
                    binding.tvFaceDistanceAlert.isChecked = alerts["faceDistance"] ?: false
                    binding.tvRestrictedAppAlert.isChecked = alerts["restrictedApp"] ?: false
                }
                val schedule = doc.get("schedule") as? Map<String, Any>
                schedule?.let {
                    val startTime = schedule["start"] as? String ?: "00:00"
                    val endTime = schedule["end"] as? String ?: "00:00"
                    binding.tvSchedule.text = "$startTime - $endTime"
                }

                // Make switches read-only for child
                binding.tvEyeStrainAlert.isEnabled = false
                binding.tvFaceDistanceAlert.isEnabled = false
                binding.tvRestrictedAppAlert.isEnabled = false

                showLoading(false)
            }
            .addOnFailureListener {
                if (!isAdded) return@addOnFailureListener
                showErrorToast("Failed to load settings")
                showLoading(false)
            }
    }

    private fun safeStartActivity(target: Class<*>, clearTask: Boolean = false) {
        try {
            val intent = Intent(requireContext(), target)
            if (clearTask) {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            showErrorToast("Navigation error")
        }
    }

    private fun showErrorToast(message: String) {
        if (!isAdded) return
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    private fun showLoading(show: Boolean) {
        if (!isAdded) return
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.contentLayout.visibility = if (show) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        deviceLockListener?.remove() // Clean up Firestore listener
        _binding = null
        super.onDestroyView()
    }
}