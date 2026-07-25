package com.example.carecircleparentapp.fragments

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.carecircleparentapp.R
import com.example.carecircleparentapp.adapters.ChildrenAdapter
import com.example.carecircleparentapp.databinding.FragmentDashboardBinding
import com.example.carecircleparentapp.modals.ChildMostUsedApp
import com.example.carecircleparentapp.utils.FirebaseUtils
import com.example.carecircleparentapp.utils.TimeFormat.formatMillis

class DashboardFragment : Fragment() {

    private lateinit var binding: FragmentDashboardBinding
    private lateinit var adapter: ChildrenAdapter
    private var childList = ArrayList<ChildMostUsedApp>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.alertsLL.setOnClickListener {
            findNavController().navigate(R.id.alertsFragment)
        }

        adapter = ChildrenAdapter(requireContext(), childList)
        binding.childrenRv.adapter = adapter
        binding.childrenRv.layoutManager = LinearLayoutManager(requireContext())
        getDataFromDb()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun getDataFromDb() {
        // Show progress bars when loading starts
        binding.childrenProgressBar.visibility = View.VISIBLE
        binding.alertsProgressBar.visibility = View.VISIBLE
        binding.screenTimeProgressBar.visibility = View.VISIBLE

        FirebaseUtils.getMostUsedAppForEachChildToday { children ->
            // Check if Fragment is attached before proceeding
            if (!isAdded || isDetached) {
                binding.childrenProgressBar.visibility = View.GONE
                Log.d("DashboardCheck", "Fragment not attached, skipping children update")
                return@getMostUsedAppForEachChildToday
            }
            childList.clear()
            childList.addAll(children)
            adapter.notifyDataSetChanged()
            binding.childrenProgressBar.visibility = View.GONE
            Log.d("DashboardCheck", "Children updated: ${childList.size}")
        }

        FirebaseUtils.getAlerts { alertsList ->
            // Check if Fragment is attached before proceeding
            if (!isAdded || isDetached) {
                binding.alertsProgressBar.visibility = View.GONE
                Log.d("DashboardCheck", "Fragment not attached, skipping alerts update")
                return@getAlerts
            }
            binding.numOfAlerts.text = alertsList.size.toString()
            binding.alertsProgressBar.visibility = View.GONE
            Log.d("DashboardCheck", "Alerts updated: ${alertsList.size}")
        }

        FirebaseUtils.getTotalScreenTimeAndMostUsedApp { totalMillis, mostUsedAppName, mostUsedMillis ->
            // Check if Fragment is attached before proceeding
            if (!isAdded || isDetached) {
                binding.screenTimeProgressBar.visibility = View.GONE
                Log.d("DashboardCheck", "Fragment not attached, skipping screen time update")
                return@getTotalScreenTimeAndMostUsedApp
            }
            binding.screenTime.text = formatMillis(totalMillis)
            binding.mostUsedApp.text = mostUsedAppName
            binding.mostUsedAppTime.text = formatMillis(mostUsedMillis)
            binding.screenTimeProgressBar.visibility = View.GONE
            Log.d("DashboardCheck", "Screen time and app updated")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }
}