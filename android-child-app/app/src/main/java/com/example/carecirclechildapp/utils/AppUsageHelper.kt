package com.example.carecirclechildapp.utils

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.example.carecirclechildapp.modals.AppUsageData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppUsageHelper {

    fun getUsageStats(context: Context): List<AppUsageData> {
        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 1000 * 60 * 60 // 1 hour back

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, startTime, endTime
        )

        return stats
            .filter { it.totalTimeInForeground > 0 }
            .map {
                val appName = getAppNameFromPackage(context, it.packageName)
                AppUsageData(
                    packageName = it.packageName,
                    appName = appName,
                    usageTimeMillis = it.totalTimeInForeground,
                    lastUsedTime = it.lastTimeUsed,
                    date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                )
            }
    }

    fun getTopForegroundApp(context: Context): String? {
        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 5000 // last 5 sec window

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, startTime, endTime
        )

        val recentUsage = stats.maxByOrNull { it.lastTimeUsed }
        val topPackage = recentUsage?.packageName
        Log.d("TopApp", "Foreground: $topPackage")
        return topPackage
    }

    fun getAppNameFromPackage(context: Context, packageName: String): String {
        return try {
            val packageManager = context.packageManager
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() } // fallback
        }
    }


}

























