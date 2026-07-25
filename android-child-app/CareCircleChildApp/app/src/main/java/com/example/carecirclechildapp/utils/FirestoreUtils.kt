package com.example.carecirclechildapp.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import com.example.carecirclechildapp.modals.AppUsageData
import com.example.carecirclechildapp.modals.BlinkLog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FirestoreUtils {
    private val db by lazy { FirebaseFirestore.getInstance() }
    fun getChildId(): String? {
        return FirebaseAuth.getInstance().currentUser?.uid
    }

    fun uploadBlinkData(childId: String?, log: BlinkLog) {
        if (childId == null) {
            Log.e("FirestoreUtils", "Cannot upload blink data: childId is null")
            return
        }
        db.collection("children_data")
            .document(childId)
            .collection("eye_blink_logs")
            .add(log)
            .addOnSuccessListener {
                Log.d("FirestoreUtils", "Blink data uploaded successfully")
            }
            .addOnFailureListener { e ->
                Log.e("FirestoreUtils", "Failed to upload blink data: ${e.message}")
            }
    }


    fun uploadUsageStats(usageStats: List<AppUsageData>) {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val childId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val usageMap = mutableMapOf<String, Any>()

        usageStats.forEach { app ->
            usageMap[app.packageName] = mapOf(
                "packageName" to app.packageName,
                "appName" to app.appName,
                "usageTimeMillis" to app.usageTimeMillis,
                "lastUsedTime" to app.lastUsedTime,
                "date" to app.date
            )
        }

        db.collection("children_data")
            .document(childId)
            .collection("appUsage")
            .document(date)
            .set(usageMap, SetOptions.merge())
            .addOnSuccessListener {
                Log.d("Firestore", "Usage stats uploaded successfully")
            }
            .addOnFailureListener {
                Log.e("Firestore", "Failed to upload usage stats", it)
            }
    }

    fun getUsageStats(onResult: (List<AppUsageData>) -> Unit) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val childId = getChildId()
        if (childId == null) {
            Log.e("FirestoreUtils", "Cannot fetch usage stats: childId is null")
            onResult(emptyList())
            return
        }
            db.collection("children_data").document(childId).collection("appUsage").document(today)
                .get().addOnSuccessListener { snapshot ->
                    if (!snapshot.exists()) {
                        onResult(emptyList())
                        return@addOnSuccessListener
                    }
                    val usageMap = snapshot.data
                    val usageStats = mutableListOf<AppUsageData>()
                    usageMap?.forEach { (packageName, data) ->
                        val appData = data as? Map<*, *>
                        val appName = appData?.get("appName") as? String ?: ""
                        val usageTimeMillis = appData?.get("usageTimeMillis") as? Long ?: 0L
                        val lastUsedTime = appData?.get("lastUsedTime") as? Long ?: 0L
                        usageStats.add(
                            AppUsageData(
                                appName = appName,
                                packageName = packageName as String,
                                usageTimeMillis = usageTimeMillis,
                                lastUsedTime = lastUsedTime,
                                date = today
                            )
                        )


                    }
                    onResult(usageStats)
                    }
                .addOnFailureListener { e ->
                    Log.e("FirestoreUtils", "Failed to fetch usage stats: ${e.message}")
                    onResult(emptyList())

            }
    }


    fun getRestrictedApps(onResult: (List<String>) -> Unit): ListenerRegistration? {
        val childId = FirebaseAuth.getInstance().currentUser?.uid
        if (childId == null) {
            Log.e("FirestoreUtils", "Cannot fetch restricted apps: childId is null")
            onResult(emptyList())
            return null
        }
        val ref = FirebaseFirestore.getInstance().collection("children_data").document(childId).collection("restricted_apps")
        return ref.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("FirestoreUtils", "Failed to fetch restricted apps: ${error.message}")
                onResult(emptyList())
                return@addSnapshotListener
            }
            val packageList = snapshot?.documents?.mapNotNull { it.id } ?: emptyList()
            PreferenceHelper.setRestrictedAppsList(packageList)
            Log.d("FirestoreUtils", "Real-time restricted apps update: $packageList")
            onResult(packageList)
        }
    }

    fun sendAlertToFirebase(
        parentId: String,
        childId: String,
        alertType: String,
        content: String,
        packageName: String
    ) {
        val alertRef = db.collection("children_data").document(childId).collection("alerts")
        val alert = mapOf(
            "parentId" to parentId,
            "childId" to childId,
            "type" to alertType,
            "content" to content,
            "packageName" to packageName,
            "timestamp" to System.currentTimeMillis(),
            "date" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        )
        alertRef.add(alert)
            .addOnSuccessListener {
                Log.d("FirestoreUtils", "Alert sent successfully")
            }
            .addOnFailureListener { e ->
                Log.e("FirestoreUtils", "Failed to send alert: ${e.message}")
            }

    }

    fun listenToDeviceLock(onLockStatusChanged: (Boolean) -> Unit): ListenerRegistration {
        val childId = getChildId()
        if (childId == null) {
            Log.e("FirestoreUtils", "Cannot listen to device lock: childId is null")
            return db.collection("empty").addSnapshotListener { _, _ -> }
        }

        val ref = db.collection("children_data")
            .document(childId)
            .collection("settings")
            .document("device")

        return ref.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("FirestoreUtils", "Device lock listen failed: ${error.message}")
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val isLocked = snapshot.getBoolean("lockScreen") ?: false
                onLockStatusChanged(isLocked)
            } else {
                Log.d("FirestoreUtils", "Device lock data: null")
                onLockStatusChanged(false)
            }
        }
    }

    fun logScreenTime() {
        val screenTimeMillis = PreferenceHelper.getScreenTimeToday()
        val screenTimeMinutes = screenTimeMillis / 60000
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        sendScreenTimeToFirestore(date, screenTimeMinutes)
    }

    fun getParent(parentId: (String) -> Unit) {
        val childId = getChildId() ?: return
        val ref = db.collection("children_data").document(childId).collection("parent")
        ref.get().addOnSuccessListener { queryDocumentSnapshots ->
            if (!queryDocumentSnapshots.isEmpty) {
                for (document in queryDocumentSnapshots.documents) {
                    val parent = document.getString("uid")
                    parent?.let { parentId(it) }
                    Log.d("FirestoreUtils", "Parent: $parent")
                    break
                }

            }
        }.addOnFailureListener {
            Log.d("FirestoreUtils", "Parent: null ${it.message}")
        }
    }

    fun getChildName(name: (String) -> Unit) {
        val childId = getChildId() ?: return
        val ref = db.collection("children_data").document(childId).collection("profile")
        ref.get().addOnSuccessListener { querySnapshot ->
            if (!querySnapshot.isEmpty) {
                for (document in querySnapshot.documents) {
                    val childName = document.getString("childName")
                    childName?.let { it -> name(it) }
                    Log.d("FirestoreUtils", "Child Name: $childName")
                    break // Assuming only one profile document per childId
                }
            }
        }.addOnFailureListener {
            Log.d("FirestoreUtils", "Child Name: null ${it.message}")
        }
    }

    fun getChildEmail(email: (String) -> Unit) {
        val childId = getChildId() ?: return
        val ref = db.collection("children_data").document(childId).collection("profile")
        ref.get().addOnSuccessListener { querySnapshot ->
            if (!querySnapshot.isEmpty) {
                for (document in querySnapshot.documents) {
                    val childEmail = document.getString("email")
                    childEmail?.let { it -> email(it) }
                    Log.d("FirestoreUtils", "Child Email: $childEmail")
                    break // Assuming only one profile document per childId
                }
            }
        }
    }

    fun sendScreenTimeToFirestore(date: String, screenTimeMinutes: Long) {
        val childId = getChildId() ?: run {
            Log.e("FirestoreUtils", "Cannot send screen time: childId is null")
            return
        }
        val screenTimeData = mapOf("screenTime" to screenTimeMinutes)
        db.collection("children_data")
            .document(childId)
            .collection("screen_time")
            .document(date)
            .set(screenTimeData)
            .addOnSuccessListener {
                Log.d("FirestoreUtils", "Screen time uploaded successfully")
            }
            .addOnFailureListener { e ->
                Log.e("FirestoreUtils", "Failed to upload screen time: ${e.message}")
            }
    }


    @SuppressLint("QueryPermissionsNeeded")
    fun uploadInstalledApps(context: Context) {
        val childId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        try {
            val pm: PackageManager = context.packageManager
            val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledApplications(PackageManager.GET_META_DATA)
            }

            val appList = mutableListOf<Map<String, String>>()

            for (appInfo in packages) {
                if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0) continue

                val appName = pm.getApplicationLabel(appInfo).toString()
                val packageName = appInfo.packageName

                appList.add(
                    mapOf(
                        "name" to appName,
                        "package" to packageName
                    )
                )
            }

            db.collection("children_data").document(childId)
                .collection("deviceInfo").document("installedApps")
                .set(mapOf("apps" to appList))
                .addOnSuccessListener {
                    Log.d("AppUtils", "✅ Installed apps uploaded successfully")
                }
                .addOnFailureListener { e ->
                    Log.e("AppUtils", "❌ Failed to upload installed apps", e)
                }

        } catch (e: Exception) {
            Log.e("AppUtils", "Error fetching installed apps", e)
        }
    }

}
