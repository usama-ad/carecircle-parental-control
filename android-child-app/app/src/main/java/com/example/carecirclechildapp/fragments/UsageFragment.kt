package com.example.carecirclechildapp.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.carecirclechildapp.R
import com.example.carecirclechildapp.databinding.FragmentUsageBinding
import com.example.carecirclechildapp.utils.FirestoreUtils

class UsageFragment : Fragment() {
    private var _binding: FragmentUsageBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUsageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadChildUsage()
    }

    @SuppressLint("SetTextI18n")
    private fun loadChildUsage() {
        // Show progress bar
        binding.progressBar.visibility = View.VISIBLE
        binding.contentLayout.visibility = View.GONE

        FirestoreUtils.getUsageStats { usageStats ->
            if (!isAdded) return@getUsageStats // avoid crash if fragment detached

            binding.progressBar.visibility = View.GONE
            binding.contentLayout.visibility = View.VISIBLE

            if (usageStats.isEmpty()) {
                binding.topMostApp.text = "No data found"
                binding.topMostTime.text = "--"
                binding.secTopMostApp.text = "No data found"
                binding.secTopMostTime.text = "--"
                binding.thirdTopMostApp.text = "No data found"
                binding.thirdTopMostTime.text = "--"
                binding.fourthTopMostApp.text = "No data found"
                binding.fourthTopMostTime.text = "--"
                binding.dailyTime.text = "--"
                return@getUsageStats
            }

            val sorted = usageStats.sortedByDescending { it.usageTimeMillis }

            fun bindAppData(index: Int, appNameView: View, timeView: View) {
                if (index < sorted.size) {
                    val app = sorted[index]
                    when (appNameView) {
                        binding.topMostApp -> binding.topMostApp.text = app.appName
                        binding.secTopMostApp -> binding.secTopMostApp.text = app.appName
                        binding.thirdTopMostApp -> binding.thirdTopMostApp.text = app.appName
                        binding.fourthTopMostApp -> binding.fourthTopMostApp.text = app.appName
                    }
                    when (timeView) {
                        binding.topMostTime -> binding.topMostTime.text = timeFormatter(app.usageTimeMillis)
                        binding.secTopMostTime -> binding.secTopMostTime.text = timeFormatter(app.usageTimeMillis)
                        binding.thirdTopMostTime -> binding.thirdTopMostTime.text = timeFormatter(app.usageTimeMillis)
                        binding.fourthTopMostTime -> binding.fourthTopMostTime.text = timeFormatter(app.usageTimeMillis)
                    }
                } else {
                    when (appNameView) {
                        binding.topMostApp -> binding.topMostApp.text = "No data found"
                        binding.secTopMostApp -> binding.secTopMostApp.text = "No data found"
                        binding.thirdTopMostApp -> binding.thirdTopMostApp.text = "No data found"
                        binding.fourthTopMostApp -> binding.fourthTopMostApp.text = "No data found"
                    }
                    when (timeView) {
                        binding.topMostTime -> binding.topMostTime.text = "--"
                        binding.secTopMostTime -> binding.secTopMostTime.text = "--"
                        binding.thirdTopMostTime -> binding.thirdTopMostTime.text = "--"
                        binding.fourthTopMostTime -> binding.fourthTopMostTime.text = "--"
                    }
                }
            }

            // Bind top 4 apps safely
            bindAppData(0, binding.topMostApp, binding.topMostTime)
            bindAppData(1, binding.secTopMostApp, binding.secTopMostTime)
            bindAppData(2, binding.thirdTopMostApp, binding.thirdTopMostTime)
            bindAppData(3, binding.fourthTopMostApp, binding.fourthTopMostTime)

            // Total daily usage
            val totalTime = usageStats.sumOf { it.usageTimeMillis }
            binding.dailyTime.text = timeFormatter(totalTime)
        }
    }

    @SuppressLint("DefaultLocale")
    private fun timeFormatter(time: Long): String {
        val hours = time / (1000 * 60 * 60)
        val minutes = (time % (1000 * 60 * 60)) / (1000 * 60)

        return if (hours > 0) {
            String.format("%02dh:%02dm", hours, minutes)
        } else {
            String.format("%02dm", minutes)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
