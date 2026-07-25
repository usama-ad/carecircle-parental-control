package com.example.carecirclechildapp.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.carecirclechildapp.R
import com.example.carecirclechildapp.databinding.FragmentHomeBinding
import com.example.carecirclechildapp.utils.FirestoreUtils
import com.google.firebase.firestore.FirebaseFirestore

class HomeFragment : Fragment() {
    private var binding: FragmentHomeBinding? = null
    private var childId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding?.root
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding?.let { safeBinding ->
            // Show progress bar
            safeBinding.progressBar.visibility = View.VISIBLE

            FirestoreUtils.getChildName { childName ->
                if (isAdded) {
                    safeBinding.childName.text = childName.takeIf { it.isNotBlank() } ?: "Unknown Child"
                    // Hide progress bar after loading child name
                    safeBinding.progressBar.visibility = View.GONE
                }
            }
            FirestoreUtils.getParent { parentId ->
                if (!isAdded) return@getParent
                FirebaseFirestore.getInstance().collection("parents").document(parentId)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        if (!isAdded) return@addOnSuccessListener
                        if (snapshot == null || !snapshot.exists()){
                            safeBinding.parentName.text = " "
                        }else{
                            safeBinding.parentName.text = snapshot.getString("username")
                        }
                    }
            }
            childId = FirestoreUtils.getChildId()
            if (childId != null) {
                loadAllowedUsage(childId!!)
            } else {
                if (isAdded) {
                    safeBinding.allowedUsage.text = "Error: Child ID not found"
                    safeBinding.progressBar.visibility = View.GONE
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun loadAllowedUsage(selectedChildId: String) {
        binding?.let { safeBinding ->
            val db = FirebaseFirestore.getInstance()
            db.collection("children_data")
                .document(selectedChildId)
                .collection("settings")
                .document("device")
                .get()
                .addOnSuccessListener { document ->
                    if (isAdded) {
                        if (document.exists()) {
                            val schedule = document.get("schedule") as? Map<*, *>
                            val start = schedule?.get("start") as? String
                            val end = schedule?.get("end") as? String
                            if (!start.isNullOrBlank() && !end.isNullOrBlank()) {
                                safeBinding.allowedUsageTime.text = "$start - $end"
                            } else {
                                safeBinding.allowedUsage.text = "Allowed Usage: Not set"
                            }
                        } else {
                            safeBinding.allowedUsage.text = "Allowed Usage: Not set"
                        }
                        // Hide progress bar after loading usage
                        safeBinding.progressBar.visibility = View.GONE
                    }
                }
                .addOnFailureListener {
                    if (isAdded) {
                        safeBinding.allowedUsage.text = "Error loading usage"
                        safeBinding.progressBar.visibility = View.GONE
                    }
                }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}