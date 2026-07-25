package com.example.carecircleparentapp.Activities

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.carecircleparentapp.R
import com.example.carecircleparentapp.databinding.ActivityChildDetailsBinding
import com.example.carecircleparentapp.utils.FirebaseUtils
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import java.text.SimpleDateFormat
import java.util.*

class ChildDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChildDetailsBinding
    private lateinit var childUid: String
    private lateinit var childName: String

    private class HoursMinutesFormatter : ValueFormatter() {
        override fun getFormattedValue(value: Float): String {
            if (value <= 0f) return "" // Suppress label for zero values
            val totalMinutes = value.toLong()
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
        }
    }
    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChildDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        childUid = intent.getStringExtra("childId") ?: return
        childName = intent.getStringExtra("childName") ?: "Unknown"
        binding.childName.text = "Details for $childName"

        setupSpinner()
    }

    private fun setupSpinner() {
        binding.timeFilterSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                when (binding.timeFilterSpinner.selectedItem.toString()) {
                    "Today" -> {
                        loadTodayScreenTime()
                        loadTodayTopUsedApps()
                    }
                    "This Week" -> {
                        loadWeeklyScreenTimeChart()
                        loadTopUsedAppsText()
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun loadTodayScreenTime() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault()
        }.format(Date())

        // Calculate elapsed minutes since midnight
        val calendar = Calendar.getInstance()
        val elapsedMinutesToday = (calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)).toFloat()

        FirebaseUtils.db.collection("children_data")
            .document(childUid)
            .collection("screen_time")
            .document(today)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("screen time", "Listener error: $e")
                    return@addSnapshotListener
                }
                val firebaseMinutes = snapshot?.getLong("screenTime")?.toFloat() ?: 0f
                val minutes = minOf(firebaseMinutes, elapsedMinutesToday)
                Log.d("screen time", "Today: $minutes minutes (Firebase: $firebaseMinutes, Elapsed: $elapsedMinutesToday)")

                val entries = listOf(BarEntry(0f, minutes))
                val barDataSet = BarDataSet(entries, "Screen Time").apply {
                    color = ContextCompat.getColor(this@ChildDetailsActivity, R.color.teal_700)
                    valueTextSize = 12f
                    valueTextColor = Color.BLACK
                    valueFormatter = HoursMinutesFormatter()
                }

                val barData = BarData(barDataSet).apply { barWidth = 0.1f }

                binding.barChart.apply {
                    data = barData
                    setFitBars(true)
                    description.isEnabled = false
                    axisRight.isEnabled = false
                    setDrawValueAboveBar(true)
                    setDrawGridBackground(false)

                    xAxis.apply {
                        position = XAxis.XAxisPosition.BOTTOM
                        setDrawGridLines(false)
                        textSize = 12f
                        valueFormatter = IndexAxisValueFormatter(listOf("Today"))
                        granularity = 1f
                        labelCount = 1
                    }

                    axisLeft.apply {
                        axisMinimum = 0f
                        axisMaximum = 1440f
                        textSize = 12f
                        setDrawGridLines(true)
                    }

                    animateY(1000)
                    invalidate()
                }
            }
    }

    @SuppressLint("SetTextI18n")
    private fun loadTodayTopUsedApps() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        FirebaseUtils.db.collection("children_data")
            .document(childUid)
            .collection("appUsage")
            .document(today)
            .get()
            .addOnSuccessListener { snapshot ->
                val usageMap = snapshot.data ?: emptyMap<String, Any>()
                val apps = mutableMapOf<String, Long>()

                for ((_, value) in usageMap) {
                    val usage = value as? Map<*, *> ?: continue
                    val name = usage["appName"] as? String ?: continue
                    val millis = (usage["usageTimeMillis"] as? Number)?.toLong() ?: 0L
                    apps[name] = apps.getOrDefault(name, 0L) + millis
                }

                val sorted = apps.entries.sortedByDescending { it.value }.take(4)

                // Reset all TextViews to invisible
                binding.topUsedApp.visibility = View.GONE
                binding.topUsedAppTime.visibility = View.GONE
                binding.secTopUsedApp.visibility = View.GONE
                binding.secTopUsedAppTime.visibility = View.GONE
                binding.thirdTopUsedApp.visibility = View.GONE
                binding.thirdTopUsedAppTime.visibility = View.GONE
                binding.fourthTopUsedApp.visibility = View.GONE
                binding.fourthTopUsedAppTime.visibility = View.GONE

                // Set data for available apps
                sorted.getOrNull(0)?.let { (name, millis) ->
                    val hours = millis / (1000 * 60 * 60)
                    val minutes = (millis / (1000 * 60)) % 60
                    binding.topUsedApp.text = name
                    binding.topUsedAppTime.text = "${hours}h ${minutes}m"
                    binding.topUsedApp.visibility = View.VISIBLE
                    binding.topUsedAppTime.visibility = View.VISIBLE
                }
                sorted.getOrNull(1)?.let { (name, millis) ->
                    val hours = millis / (1000 * 60 * 60)
                    val minutes = (millis / (1000 * 60)) % 60
                    binding.secTopUsedApp.text = name
                    binding.secTopUsedAppTime.text = "${hours}h ${minutes}m"
                    binding.secTopUsedApp.visibility = View.VISIBLE
                    binding.secTopUsedAppTime.visibility = View.VISIBLE
                }
                sorted.getOrNull(2)?.let { (name, millis) ->
                    val hours = millis / (1000 * 60 * 60)
                    val minutes = (millis / (1000 * 60)) % 60
                    binding.thirdTopUsedApp.text = name
                    binding.thirdTopUsedAppTime.text = "${hours}h ${minutes}m"
                    binding.thirdTopUsedApp.visibility = View.VISIBLE
                    binding.thirdTopUsedAppTime.visibility = View.VISIBLE
                }
                sorted.getOrNull(3)?.let { (name, millis) ->
                    val hours = millis / (1000 * 60 * 60)
                    val minutes = (millis / (1000 * 60)) % 60
                    binding.fourthTopUsedApp.text = name
                    binding.fourthTopUsedAppTime.text = "${hours}h ${minutes}m"
                    binding.fourthTopUsedApp.visibility = View.VISIBLE
                    binding.fourthTopUsedAppTime.visibility = View.VISIBLE
                }

                // If no apps, show a placeholder
                if (sorted.isEmpty()) {
                    binding.topUsedApp.text = getString(R.string.no_data)
                    binding.topUsedApp.visibility = View.VISIBLE
                    binding.topUsedAppTime.visibility = View.GONE
                }
            }
            .addOnFailureListener { e ->
                Log.e("screen time", "Error fetching today top apps: $e")
                binding.topUsedApp.text = getString(R.string.error_loading_data)
                binding.topUsedApp.setTextColor(ContextCompat.getColor(this, R.color.red))
                binding.topUsedApp.visibility = View.VISIBLE
                binding.topUsedAppTime.visibility = View.GONE
                binding.secTopUsedApp.visibility = View.GONE
                binding.secTopUsedAppTime.visibility = View.GONE
                binding.thirdTopUsedApp.visibility = View.GONE
                binding.thirdTopUsedAppTime.visibility = View.GONE
                binding.fourthTopUsedApp.visibility = View.GONE
                binding.fourthTopUsedAppTime.visibility = View.GONE
            }
    }

    private fun loadWeeklyScreenTimeChart() {
        FirebaseUtils.getWeeklyScreenTimeForChild(childUid) { screenTimeMap ->
            Log.d("screen time", "Weekly screen time map: $screenTimeMap")
            val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
            val todayName = SimpleDateFormat("EEE", Locale.getDefault()).format(Date())
            val todayIndex = dayNames.indexOf(todayName)

            val entries = dayNames.mapIndexed { index, day ->
                BarEntry(index.toFloat(), screenTimeMap[day]?.toFloat() ?: 0f)
            }

            val dataSet = BarDataSet(entries, "Screen Time").apply {
                color = ContextCompat.getColor(this@ChildDetailsActivity, R.color.teal_700)
                valueTextSize = 12f
                valueTextColor = Color.BLACK
                valueFormatter = HoursMinutesFormatter()
            }

            val barData = BarData(dataSet).apply { barWidth = 0.6f }

            binding.barChart.apply {
                data = barData
                setFitBars(true)
                description.isEnabled = false
                axisRight.isEnabled = false
                setDrawValueAboveBar(true)
                setDrawGridBackground(false)

                xAxis.apply {
                    valueFormatter = IndexAxisValueFormatter(dayNames)
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false)
                    textSize = 12f
                    granularity = 1f
                    labelCount = dayNames.size
                }

                axisLeft.apply {
                    axisMinimum = 0f
                    axisMaximum = 1440f
                    textSize = 12f
                    setDrawGridLines(true)
                }

                animateY(1000)
                invalidate()
            }
        }
    }



    @SuppressLint("SetTextI18n")
    private fun loadTopUsedAppsText() {
        FirebaseUtils.getTopUsedAppsForChildThisWeek(childUid) { topApps ->
            // Reset all TextViews to invisible
            binding.topUsedApp.visibility = View.GONE
            binding.topUsedAppTime.visibility = View.GONE
            binding.secTopUsedApp.visibility = View.GONE
            binding.secTopUsedAppTime.visibility = View.GONE
            binding.thirdTopUsedApp.visibility = View.GONE
            binding.thirdTopUsedAppTime.visibility = View.GONE
            binding.fourthTopUsedApp.visibility = View.GONE
            binding.fourthTopUsedAppTime.visibility = View.GONE

            // Set data for available apps
            topApps.getOrNull(0)?.let { (appName, millis) ->
                val hours = millis / (1000 * 60 * 60)
                val minutes = (millis / (1000 * 60)) % 60
                binding.topUsedApp.text = appName
                binding.topUsedAppTime.text = "${hours}h ${minutes}m"
                binding.topUsedApp.visibility = View.VISIBLE
                binding.topUsedAppTime.visibility = View.VISIBLE
            }
            topApps.getOrNull(1)?.let { (appName, millis) ->
                val hours = millis / (1000 * 60 * 60)
                val minutes = (millis / (1000 * 60)) % 60
                binding.secTopUsedApp.text = appName
                binding.secTopUsedAppTime.text = "${hours}h ${minutes}m"
                binding.secTopUsedApp.visibility = View.VISIBLE
                binding.secTopUsedAppTime.visibility = View.VISIBLE
            }
            topApps.getOrNull(2)?.let { (appName, millis) ->
                val hours = millis / (1000 * 60 * 60)
                val minutes = (millis / (1000 * 60)) % 60
                binding.thirdTopUsedApp.text = appName
                binding.thirdTopUsedAppTime.text = "${hours}h ${minutes}m"
                binding.thirdTopUsedApp.visibility = View.VISIBLE
                binding.thirdTopUsedAppTime.visibility = View.VISIBLE
            }
            topApps.getOrNull(3)?.let { (appName, millis) ->
                val hours = millis / (1000 * 60 * 60)
                val minutes = (millis / (1000 * 60)) % 60
                binding.fourthTopUsedApp.text = appName
                binding.fourthTopUsedAppTime.text = "${hours}h ${minutes}m"
                binding.fourthTopUsedApp.visibility = View.VISIBLE
                binding.fourthTopUsedAppTime.visibility = View.VISIBLE
            }

            // If no apps, show a placeholder
            if (topApps.isEmpty()) {
                binding.topUsedApp.text = getString(R.string.no_data)
                binding.topUsedApp.visibility = View.VISIBLE
                binding.topUsedAppTime.visibility = View.GONE
            }
        }
    }
}