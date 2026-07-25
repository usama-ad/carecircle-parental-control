package com.example.carecircleparentapp.fragments

import android.annotation.SuppressLint
import android.icu.util.Calendar
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.carecircleparentapp.adapters.AlertsAdapter
import com.example.carecircleparentapp.databinding.FragmentAlertsBinding
import com.example.carecircleparentapp.modals.AlertModel
import com.example.carecircleparentapp.utils.FirebaseUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AlertsFragment : Fragment() {

    private lateinit var binding: FragmentAlertsBinding

    private val todayAlertsList = ArrayList<AlertModel>()
    private val yesterdayAlertsList = ArrayList<AlertModel>()
    private lateinit var todayAlertsAdapter: AlertsAdapter
    private lateinit var yesterdayAlertsAdapter: AlertsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("alertCheck", "onCreate: created")
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        todayAlertsAdapter = AlertsAdapter(requireContext(), todayAlertsList)
        yesterdayAlertsAdapter = AlertsAdapter(requireContext(), yesterdayAlertsList)

        binding.todayAlertsRv.adapter = todayAlertsAdapter
        binding.yesterdayAlertsRv.adapter = yesterdayAlertsAdapter
        binding.todayAlertsRv.layoutManager = LinearLayoutManager(requireContext())
        binding.yesterdayAlertsRv.layoutManager = LinearLayoutManager(requireContext())

        // Ensure visibility
        binding.yesterdayAlertsRv.visibility = View.VISIBLE
        Log.d("alertCheck", "Yesterday RV Visibility: ${binding.yesterdayAlertsRv.visibility}")

        getAlerts()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentAlertsBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun getAlerts() {
        todayAlertsList.clear()
        yesterdayAlertsList.clear()

        // Show progress bars when loading starts
        binding.todayProgressBar.visibility = View.VISIBLE
        binding.yesterdayProgressBar.visibility = View.VISIBLE

        FirebaseUtils.getAlerts { alertsList ->
            // Check if Fragment is attached before proceeding
            if (!isAdded || isDetached) {
                // Hide progress bars if Fragment is not attached
                binding.todayProgressBar.visibility = View.GONE
                binding.yesterdayProgressBar.visibility = View.GONE
                Log.d("alertCheck", "Fragment not attached, skipping update")
                return@getAlerts
            }

            val now = Calendar.getInstance()
            // Start of today
            val todayStartCal = now.clone() as Calendar
            todayStartCal.set(Calendar.HOUR_OF_DAY, 0)
            todayStartCal.set(Calendar.MINUTE, 0)
            todayStartCal.set(Calendar.SECOND, 0)
            todayStartCal.set(Calendar.MILLISECOND, 0)
            val todayStart = todayStartCal.timeInMillis
            // Start of yesterday
            val yesterdayStartCal = now.clone() as Calendar
            yesterdayStartCal.add(Calendar.DAY_OF_YEAR, -1)
            yesterdayStartCal.set(Calendar.HOUR_OF_DAY, 0)
            yesterdayStartCal.set(Calendar.MINUTE, 0)
            yesterdayStartCal.set(Calendar.SECOND, 0)
            yesterdayStartCal.set(Calendar.MILLISECOND, 0)
            val yesterdayStart = yesterdayStartCal.timeInMillis
            for (alert in alertsList) {
                when {
                    alert.timestamp >= todayStart -> todayAlertsList.add(alert)
                    alert.timestamp in yesterdayStart until todayStart -> yesterdayAlertsList.add(alert)
                }
            }

            // Update today alerts and hide its progress bar
            if (todayAlertsList.isNotEmpty()) {
                todayAlertsAdapter.notifyDataSetChanged()
                binding.todayProgressBar.visibility = View.GONE
                Log.d("alertCheck", "Today alerts updated: ${todayAlertsList.size}")
            } else {
                binding.todayProgressBar.visibility = View.GONE
                Log.d("alertCheck", "No today alerts")
            }

            // Update yesterday alerts and hide its progress bar
            if (yesterdayAlertsList.isNotEmpty()) {
                yesterdayAlertsAdapter.notifyDataSetChanged()
                binding.yesterdayProgressBar.visibility = View.GONE
                Log.d("alertCheck", "Yesterday alerts updated: ${yesterdayAlertsList.size}")
            } else {
                binding.yesterdayProgressBar.visibility = View.GONE
                Log.d("alertCheck", "No yesterday alerts")
            }
        }
    }
}