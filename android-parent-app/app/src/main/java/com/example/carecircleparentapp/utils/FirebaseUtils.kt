package com.example.carecircleparentapp.utils

import android.annotation.SuppressLint
import android.util.Log
import com.example.carecircleparentapp.modals.AlertModel
import com.example.carecircleparentapp.modals.ChildMostUsedApp
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.messaging.FirebaseMessaging
import livekit.org.webrtc.PeerConnection
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object FirebaseUtils {
    @SuppressLint("StaticFieldLeak")
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val parentId = auth.currentUser?.uid

    fun getAlerts(onResult: (ArrayList<AlertModel>) -> Unit) {
        val alertsList = ArrayList<AlertModel>()
        if (parentId == null) {
            onResult(alertsList)
            return
        }

        db.collection("parents").document(parentId).collection("children").get()
            .addOnSuccessListener { childUidSnapshot ->
                val children = childUidSnapshot.documents
                if (children.isEmpty()) {
                    onResult(alertsList)
                    return@addOnSuccessListener
                }

                var completedChildren = 0

                for (child in children) {
                    val childId = child.id
                    db.collection("children_data").document(childId).collection("alerts").get()
                        .addOnSuccessListener { alertDocs ->
                            for (alertDoc in alertDocs) {
                                val alert = alertDoc.toObject(AlertModel::class.java)
                                alertsList.add(alert)
                            }
                            completedChildren++
                            if (completedChildren == children.size) {
                                onResult(alertsList)
                            }
                        }
                        .addOnFailureListener {
                            completedChildren++
                            if (completedChildren == children.size) {
                                onResult(alertsList)
                            }
                        }
                }
            }
            .addOnFailureListener {
                onResult(alertsList)
            }
    }
    fun getTotalScreenTimeAndMostUsedApp(
        onResult: (totalMillis: Long, mostUsedAppName: String?, mostUsedMillis: Long) -> Unit
    ) {
        var totalScreenTimeMillis = 0L
        val appUsageMap = mutableMapOf<String, Long>()
        var childrenProcessed = 0

        fun deriveDisplayName(usage: Map<*, *>): String {
            val rawAppName = (usage["appName"] as? String).takeIf { !it.isNullOrBlank() }
            if (!rawAppName.isNullOrBlank()) {
                return rawAppName
            }
            val pkg = (usage["packageName"] as? String) ?: return "Unknown"

            return pkg.substringAfterLast('.', "Unknown").replaceFirstChar { it.uppercase() }
        }


        db.collection("parents").document(parentId.toString()).collection("children").get()
            .addOnSuccessListener { childDocs ->
                val totalChildren = childDocs.size()
                if (totalChildren == 0) {
                    onResult(0L, null, 0L)
                    return@addOnSuccessListener
                }

                fun tryFinalize() {
                    if (childrenProcessed == totalChildren) {
                        val mostUsedEntry = appUsageMap.maxByOrNull { it.value }
                        val mostUsedAppName = mostUsedEntry?.key
                        val mostUsedMillis = mostUsedEntry?.value ?: 0L
                        onResult(totalScreenTimeMillis, mostUsedAppName, mostUsedMillis)
                    }
                }

                for (childDoc in childDocs) {
                    val childId = childDoc.id

                    db.collection("children_data")
                        .document(childId)
                        .collection("appUsage")
                        .get()
                        .addOnSuccessListener { usageDates ->
                            if (usageDates.isEmpty) {
                                // No usage for this child, still count
                                childrenProcessed++
                                tryFinalize()
                                return@addOnSuccessListener
                            }

                            val dateTasks = usageDates.documents.map { dateDoc ->
                                db.collection("children_data")
                                    .document(childId)
                                    .collection("appUsage")
                                    .document(dateDoc.id)
                                    .get()
                            }

                            Tasks.whenAllSuccess<DocumentSnapshot>(dateTasks)
                                .addOnSuccessListener { snapshots ->
                                    for (snapshot in snapshots) {
                                        val usageMap = snapshot.data ?: continue
                                        for ((_, value) in usageMap) {
                                            val usage = value as? Map<*, *> ?: continue
                                            val usageMillis = (usage["usageTimeMillis"] as? Number)?.toLong() ?: 0L
                                            val displayName = deriveDisplayName(usage)

                                            totalScreenTimeMillis += usageMillis
                                            appUsageMap[displayName] = appUsageMap.getOrDefault(displayName, 0L) + usageMillis
                                        }
                                    }
                                    childrenProcessed++
                                    tryFinalize()
                                }
                                .addOnFailureListener {
                                    // Even if one date batch fails, count the child so we don't hang forever
                                    childrenProcessed++
                                    tryFinalize()
                                }
                        }
                        .addOnFailureListener {
                            // Could not fetch this child's usage; count it to avoid stalling
                            childrenProcessed++
                            tryFinalize()
                        }
                }
            }
            .addOnFailureListener {
                // Parent/children fetch failed; return zero as fallback
                onResult(0L, null, 0L)
            }
    }

    fun getMostUsedAppForEachChildToday(
        onComplete: (List<ChildMostUsedApp>) -> Unit
    ) {
        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        Log.d("dateCheck", "getMostUsedAppForEachChildToday: $todayDate")
        val resultList = mutableListOf<ChildMostUsedApp>()
        var childrenProcessed = 0

        db.collection("parents").document(parentId.toString()).collection("children").get()
            .addOnSuccessListener { childDocs ->
                val totalChildren = childDocs.size()
                if (totalChildren == 0) {
                    onComplete(emptyList())
                    return@addOnSuccessListener
                }

                for (childDoc in childDocs) {
                    val childId = childDoc.id

                    val profileRef = db.collection("children_data")
                        .document(childId)
                        .collection("profile")

                    profileRef.get().addOnSuccessListener { profileSnapshot ->
                        val profileDoc = profileSnapshot.documents.firstOrNull()
                        val childName = profileDoc?.getString("childName") ?: "Unknown"
                        Log.d("dateCheck", "getMostUsedAppForEachChildToday: $childName")

                        val usageRef = db.collection("children_data")
                            .document(childId)
                            .collection("appUsage")
                            .document(todayDate)

                        usageRef.get().addOnSuccessListener { usageSnap ->
                            val usageData = usageSnap.data ?: emptyMap<String, Any>()

                            var maxUsageMillis = 0L
                            var mostUsedAppName: String? = null
                            var totalScreenTimeMillis = 0L

                            for ((_, value) in usageData) {
                                val usage = value as? Map<*, *> ?: continue
                                val millis = (usage["usageTimeMillis"] as? Number)?.toLong() ?: 0L
                                Log.d("dateCheck", "getMostUsedAppForEachChildToday: $millis ")
                                val appName = usage["appName"] as? String ?: continue
                                totalScreenTimeMillis += millis
                                Log.d("dateCheck", "getMostUsedAppForEachChildToday tital: $totalScreenTimeMillis ")
                                if (millis > maxUsageMillis) {
                                    maxUsageMillis = millis
                                    mostUsedAppName = appName
                                    Log.d("dateCheck", " mostapp: $mostUsedAppName ")
                                    Log.d("dateCheck", "getMostUsedA $maxUsageMillis ")
                                }
                            }

                            resultList.add(
                                ChildMostUsedApp(
                                    childName = childName,
                                    childId = childId,
                                    mostUsedAppName = mostUsedAppName,
                                    mostUsedMillis = maxUsageMillis,
                                    totalScreenTimeMillis = totalScreenTimeMillis
                                )
                            )
                            childrenProcessed++
                            if (childrenProcessed == totalChildren) {
                                onComplete(resultList)
                            }
                        }.addOnFailureListener {
                            childrenProcessed++
                            if (childrenProcessed == totalChildren) {
                                onComplete(resultList)
                            }
                        }
                    }.addOnFailureListener {
                        childrenProcessed++
                        if (childrenProcessed == totalChildren) {
                            onComplete(resultList)
                        }
                    }
                }
            }
            .addOnFailureListener {
                onComplete(emptyList())
            }
    }

    fun getWeeklyScreenTimeForChild(
        childId: String,
        onResult: (Map<String, Long>) -> Unit
    ) {
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault()
        }
        val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val result = mutableMapOf<String, Long>().withDefault { 0L }
        var fetchedDays = 0

        // Move to most recent Monday
        while (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            calendar.add(Calendar.DATE, -1)
        }

        // Get today's index
        val todayCalendar = Calendar.getInstance()
        val todayIndex = when (todayCalendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            Calendar.SATURDAY -> 5
            Calendar.SUNDAY -> 6
            else -> 0
        }

        // Set up listeners for Monday to today
        val listeners = mutableListOf<ListenerRegistration>()
        for (i in 0..todayIndex) {
            val dateKey = dateFormat.format(calendar.time)
            val dayLabel = dayNames[i]

            val listener = db.collection("children_data")
                .document(childId)
                .collection("screen_time")
                .document(dateKey)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        Log.e("screen time", "Error fetching $dateKey ($dayLabel): $e")
                        result[dayLabel] = 0L
                    } else {
                        val minutes = snapshot?.getLong("screenTime") ?: 0L
                        result[dayLabel] = maxOf(0L, minutes)
                        Log.d("screen time", "Fetched $dateKey ($dayLabel): $minutes minutes")
                    }
                    fetchedDays++
                    if (fetchedDays == todayIndex + 1) {
                        // Fill remaining days with 0
                        for (j in todayIndex + 1 until dayNames.size) {
                            result[dayNames[j]] = 0L
                        }
                        val sortedResult = dayNames.associateWith { result.getOrDefault(it, 0L) }
                        onResult(sortedResult)
                        // Remove listeners to prevent memory leaks
                        listeners.forEach { it.remove() }
                    }
                }
            listeners.add(listener)
            calendar.add(Calendar.DATE, 1)
        }
    }

    fun getTopUsedAppsForChildThisWeek(
        childId: String,
        onResult: (List<Pair<String, Long>>) -> Unit
    ) {
        val calendar = Calendar.getInstance()
        calendar.firstDayOfWeek = Calendar.MONDAY
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val appMap = mutableMapOf<String, Long>()
        var completedDays = 0

        for (i in 0..6) {
            val date = dateFormat.format(calendar.time)
            db.collection("children_data")
                .document(childId)
                .collection("appUsage")
                .document(date)
                .get()
                .addOnSuccessListener { snapshot ->
                    val usageMap = snapshot.data ?: emptyMap()
                    for ((_, value) in usageMap) {
                        val usage = value as? Map<*, *> ?: continue
                        val millis = (usage["usageTimeMillis"] as? Number)?.toLong() ?: 0L
                        val appName = (usage["appName"] as? String) ?: continue
                        appMap[appName] = appMap.getOrDefault(appName, 0L) + millis
                    }
                    completedDays++
                    if (completedDays == 7) {
                        val sorted = appMap.entries.sortedByDescending { it.value }.take(4)
                            .map { Pair(it.key, it.value) }
                        onResult(sorted)
                    }
                }
                .addOnFailureListener { e ->
                    completedDays++
                    if (completedDays == 7) {
                        val sorted = appMap.entries.sortedByDescending { it.value }.take(4)
                            .map { Pair(it.key, it.value) }
                        onResult(sorted)
                    }
                }

            calendar.add(Calendar.DATE, 1)
        }
    }
    fun fetchIceServersFromFirestore(childId: String , callback: (List<PeerConnection.IceServer>) -> Unit) {
        FirebaseFirestore.getInstance().collection("children_data").document(childId)
            .collection("ice_servers")
            .document("shared") // Ensure this matches the child's upload document
            .get()
            .addOnSuccessListener { document ->
                val iceServers = mutableListOf<PeerConnection.IceServer>()
                val serverList = document.get("iceServers") as? List<Map<String, Any>>

                if (serverList != null) {
                    for (map in serverList) {
                        val urls = map["urls"]
                        val username = map["username"] as? String
                        val credential = map["credential"] as? String

                        val iceServer = when (urls) {
                            is String -> PeerConnection.IceServer.builder(urls)
                                .apply {
                                    username?.let { setUsername(it) }
                                    credential?.let { setPassword(it) }
                                }
                                .createIceServer()

                            is List<*> -> PeerConnection.IceServer.builder(urls.filterIsInstance<String>())
                                .apply {
                                    username?.let { setUsername(it) }
                                    credential?.let { setPassword(it) }
                                }
                                .createIceServer()

                            else -> null
                        }

                        iceServer?.let { iceServers.add(it) }
                    }
                }

                callback(iceServers)
            }
            .addOnFailureListener { exception ->
                Log.e("fetchIceServers", "Error fetching ICE servers", exception)
                callback(listOf(
                    PeerConnection.IceServer.builder("stun:stun.l.google.com:19302")
                        .createIceServer()
                ))
            }
    }

    fun getChildNamesList(onResult: (List<Pair<String, String>>) -> Unit) {
        val childNameIdPairs = mutableListOf<Pair<String, String>>()

        if (parentId == null) {
            onResult(emptyList())
            return
        }

        db.collection("parents").document(parentId)
            .collection("children")
            .addSnapshotListener { childrenList, error ->
                if (error != null) {
                    onResult(emptyList())
                    return@addSnapshotListener
                }

                if (childrenList == null || childrenList.isEmpty) {
                    onResult(emptyList())
                    return@addSnapshotListener
                }

                childNameIdPairs.clear()

                // Use a counter to ensure we collect all profiles before calling onResult
                var pendingQueries = childrenList.size()
                if (pendingQueries == 0) {
                    onResult(emptyList())
                    return@addSnapshotListener
                }

                for (child in childrenList) {
                    val childId = child.id

                    db.collection("children_data")
                        .document(childId)
                        .collection("profile")
                        .addSnapshotListener { profileSnap, e ->
                            if (e != null) {
                                // Handle error for this profile, but continue with others
                                pendingQueries--
                                if (pendingQueries == 0) {
                                    onResult(ArrayList(childNameIdPairs))
                                }
                                return@addSnapshotListener
                            }

                            if (profileSnap != null && !profileSnap.isEmpty) {
                                // Get the first profile doc (since ID is random)
                                val profileDoc = profileSnap.documents.first()
                                val name = profileDoc.getString("childName")

                                if (name != null && !childNameIdPairs.any { it.first == name }) {
                                    childNameIdPairs.add(Pair(name, childId))
                                }
                            }

                            pendingQueries--
                            if (pendingQueries == 0) {
                                onResult(ArrayList(childNameIdPairs))
                            }
                        }
                }
            }
    }


    fun getFCMToken(callback: (String?) -> Unit) {
        FirebaseMessaging.getInstance().token.addOnSuccessListener {  token ->
            if (token.isEmpty()){
                return@addOnSuccessListener
            }
            callback(token)
            db.collection("parents").document(parentId.toString()).update("fcmToken", token).addOnSuccessListener {
                Log.d("TAG","token updated")
            }
        }
    }

}





