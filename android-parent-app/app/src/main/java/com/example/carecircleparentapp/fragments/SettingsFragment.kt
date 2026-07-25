package com.example.carecircleparentapp.fragments

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.example.carecircleparentapp.Activities.AppSettingsActivity
import com.example.carecircleparentapp.Activities.ChildLinkActivity
import com.example.carecircleparentapp.Activities.LoginActivity
import com.example.carecircleparentapp.R
import com.example.carecircleparentapp.databinding.FragmentSettingsBinding
import com.example.carecircleparentapp.utils.FirebaseUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SettingsFragment : Fragment() {
    private lateinit var binding: FragmentSettingsBinding
    private lateinit var childId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.linearLayoutAppSettings.isVisible = false
        binding.userProgressBar.visibility = View.VISIBLE // Show user progress bar
        binding.spinnerProgressBar.visibility = View.VISIBLE // Show spinner progress bar
        fetchUserDetails()
        binding.linkNewChild.setOnClickListener {
            val intent = Intent(requireContext(), ChildLinkActivity::class.java)
            startActivity(intent)
        }

        binding.linearLayoutAppSettings.setOnClickListener {
            val intent = Intent(requireContext(), AppSettingsActivity::class.java)
            intent.putExtra("childId", childId)
            startActivity(intent)
        }
        binding.logoutButton.setOnClickListener {
            val alertDialog = AlertDialog.Builder(requireContext())
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setCancelable(false)
                .setPositiveButton("Yes") { dialog, _ ->
                    FirebaseUtils.auth.signOut()
                    val intent = Intent(requireContext(), LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    dialog.dismiss()
                }
                .setNeutralButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                }
            alertDialog.show()
        }
    }

    private fun fetchUserDetails() {
        // Parent details (same as before)
        FirebaseUtils.db.collection("parents")
            .document(FirebaseAuth.getInstance().currentUser!!.uid).get()
            .addOnSuccessListener { userInfo ->
                if (!isAdded || isDetached) return@addOnSuccessListener
                binding.username.text = userInfo.getString("username")
                binding.parentEmail.text = userInfo.getString("email")
                binding.userProgressBar.visibility = View.GONE
            }.addOnFailureListener {
                if (!isAdded || isDetached) return@addOnFailureListener
                binding.userProgressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Failed to load user details", Toast.LENGTH_SHORT).show()
            }

        // Children list (realtime)
        FirebaseUtils.db.collection("parents")
            .document(FirebaseAuth.getInstance().currentUser!!.uid)
            .collection("children")
            .addSnapshotListener { snapshot, error ->
                if (!isAdded || isDetached) return@addSnapshotListener
                if (error != null || snapshot == null) {
                    binding.linearLayoutAppSettings.isVisible = false
                    binding.spinnerProgressBar.visibility = View.GONE
                    return@addSnapshotListener
                }

                val childIds = snapshot.map { it.id }
                if (childIds.isEmpty()) {
                    binding.linearLayoutAppSettings.isVisible = false
                    binding.spinnerProgressBar.visibility = View.GONE
                    return@addSnapshotListener
                }

                val childIdToName = mutableMapOf<String, String>()

                // For each child, listen to their profile realtime
                childIds.forEach { cid ->
                    FirebaseUtils.db.collection("children_data")
                        .document(cid)
                        .collection("profile")
                        .limit(1)
                        .addSnapshotListener { profileSnap, e ->
                            if (e != null || profileSnap == null || profileSnap.isEmpty) return@addSnapshotListener

                            val profileDoc = profileSnap.documents.first()
                            val childName = profileDoc.getString("childName") ?: cid

                            childIdToName[cid] = childName

                            // Update spinner immediately whenever a name comes in/changes
                            val namesList = childIds.map { id -> childIdToName[id] ?: id }

                            binding.linearLayoutAppSettings.isVisible = namesList.isNotEmpty()

                            val spinnerAdapter = ArrayAdapter(
                                requireContext(),
                                R.layout.item_spinner_selected,
                                namesList
                            )
                            spinnerAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown)
                            binding.spinnerChildren.adapter = spinnerAdapter

                            // Restore previous selection if still valid
                            if (::childId.isInitialized && childIds.contains(childId)) {
                                binding.spinnerChildren.setSelection(childIds.indexOf(childId))
                            } else {
                                binding.spinnerChildren.setSelection(0)
                                childId = childIds[0]
                            }

                            binding.spinnerChildren.onItemSelectedListener =
                                object : AdapterView.OnItemSelectedListener {
                                    override fun onItemSelected(
                                        parent: AdapterView<*>?,
                                        view: View?,
                                        position: Int,
                                        id: Long
                                    ) {
                                        if (!isAdded || isDetached) return
                                        childId = childIds[position]
                                        Toast.makeText(
                                            requireContext(),
                                            "Selected: ${childIdToName[childId]}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    override fun onNothingSelected(p0: AdapterView<*>?) {}
                                }

                            binding.spinnerProgressBar.visibility = View.GONE
                        }
                }
            }
    }



    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }
}