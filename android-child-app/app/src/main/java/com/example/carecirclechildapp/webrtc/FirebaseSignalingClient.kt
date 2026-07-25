package com.example.carecirclechildapp.webrtc

import android.app.DownloadManager
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.util.Log
import com.example.carecirclechildapp.modals.IceServerResponse
import com.example.carecirclechildapp.utils.RetrofitClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.google.gson.Gson
import livekit.org.webrtc.*
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import kotlin.text.isBlank

object FirebaseSignalingClient {

    private const val TAG = "FirebaseSignaling"
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private var peerConnection: PeerConnection? = null
    private lateinit var videoTrack: VideoTrack
    private val childUid: String = FirebaseAuth.getInstance().currentUser!!.uid
    private var isCaller = true
    private var offerListener: ListenerRegistration? = null
    private var answerListener: ListenerRegistration? = null
    private var remoteIceListener: ListenerRegistration? = null

    private lateinit var signalingDoc: DocumentReference
    private lateinit var callerCandidatesCollection: CollectionReference
    private lateinit var calleeCandidatesCollection: CollectionReference
    private var isCleaningUp = false

    fun init(factory: PeerConnectionFactory, track: VideoTrack, isCaller: Boolean) {
        Log.d(TAG, "Init started with childUid=$childUid, isCaller=$isCaller")
        this.videoTrack = track
        this.isCaller = isCaller

        signalingDoc = firestore.collection("children_data")
            .document(childUid)
            .collection("signaling")
            .document("session")

        callerCandidatesCollection = signalingDoc.collection("callerCandidates")
        calleeCandidatesCollection = signalingDoc.collection("calleeCandidates")

        // Force cleanup so there's no old state
        forceCleanSignaling {
            createPeerConnection(factory)
            if (isCaller) {
                createOffer()
                listenForAnswer()
                scheduleOfferRecreation()
            } else {
                listenForOfferAndCreateAnswer()
            }
            listenForRemoteIceCandidates()
        }
    }

    private fun forceCleanSignaling(callback: () -> Unit) {
        Log.d(TAG, "Force cleaning signaling data...")
        signalingDoc.delete().addOnCompleteListener {
            callerCandidatesCollection.get().addOnSuccessListener { snap ->
                snap.documents.forEach { it.reference.delete() }
                calleeCandidatesCollection.get().addOnSuccessListener { snap2 ->
                    snap2.documents.forEach { it.reference.delete() }
                    signalingDoc.set(
                        mapOf(
                            "lastProcessedOfferTimestamp" to 0,
                            "lastProcessedAnswerTimestamp" to 0
                        )
                    ).addOnSuccessListener {
                        Log.d(TAG, "Signaling fully reset on child")
                        callback()
                    }.addOnFailureListener {
                        Log.e(TAG, "Failed to reset timestamps: ${it.message}")
                        callback()
                    }
                }
            }
        }
    }


    private fun cleanUpSignalingData(callback: () -> Unit) {
        Log.d(TAG, "Cleaning up signaling data")
        isCleaningUp = true
        signalingDoc.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val timestamp = snapshot.getLong("timestamp") ?: 0
                val currentTime = System.currentTimeMillis()
                if (currentTime - timestamp > 30_000) { // 30 seconds
                    signalingDoc.delete().addOnSuccessListener {
                        callerCandidatesCollection.get().addOnSuccessListener { snap ->
                            snap.documents.forEach { it.reference.delete() }
                            calleeCandidatesCollection.get().addOnSuccessListener { snap ->
                                snap.documents.forEach { it.reference.delete() }
                                Log.d(TAG, "Signaling data cleaned up")
                                callback()
                            }.addOnFailureListener {
                                Log.e(TAG, "Failed to clean callee candidates: ${it.message}")
                                callback()
                            }
                        }.addOnFailureListener {
                            Log.e(TAG, "Failed to clean caller candidates: ${it.message}")
                            callback()
                        }
                    }.addOnFailureListener {
                        Log.e(TAG, "Failed to delete signaling doc: ${it.message}")
                        callback()
                    }
                } else {
                    Log.d(TAG, "Skipping cleanup, active session detected")
                    signalingDoc.update("lastProcessedAnswerTimestamp", 0)
                    callback()
                }
            } else {
                Log.d(TAG, "No signaling data to clean")
                callback()
            }
        }.addOnFailureListener {
            Log.e(TAG, "Failed to check signaling data: ${it.message}")
            callback()
        }
    }

    private fun createPeerConnection(factory: PeerConnectionFactory) {
        fetchIceServers { iceServers ->
            // Convert each IceServer object to a Map<String, Any>
            val iceServerMaps = iceServers.map { server ->
                val map = mutableMapOf<String, Any>()
                map["urls"] = server.urls
                server.username?.let { map["username"] = it }
                server.password?.let { map["credential"] = it }
                map
            }

            // Store the list in Firestore under a known document (e.g., shared)
            FirebaseFirestore.getInstance().collection("children_data").document(childUid)
                .collection("ice_servers")
                .document("shared")
                .set(mapOf("iceServers" to iceServerMaps))
                .addOnSuccessListener {
                    Log.d(TAG, "ICE servers set successfully in Firestore.")
                }
                .addOnFailureListener {
                    Log.e(TAG, "Failed to set ICE servers", it)
                }
            Log.d("ServerList", "Fetched ICE servers: $iceServers")
            val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
                iceTransportsType = PeerConnection.IceTransportsType.ALL
                iceCandidatePoolSize = 10
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            }
            peerConnection =
                factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
                    override fun onIceCandidate(candidate: IceCandidate) {
                        Log.d(TAG, "ICE Candidate generated: ${candidate.sdp}")
                        if (candidate.sdp.contains("typ relay")) {
                            Log.d(TAG, "✅ Relay candidate generated")
                        }
                        addIceCandidateToFirestore(candidate)
                    }

                    override fun onTrack(transceiver: RtpTransceiver?) {
                        Log.d(TAG, "Remote track received: ${transceiver?.receiver?.track()?.id()}")
                    }

                    override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                        Log.d(TAG, "ICE connection state changed: $newState")
                        if (newState == PeerConnection.IceConnectionState.FAILED || newState == PeerConnection.IceConnectionState.DISCONNECTED) {
                            Log.e(TAG, "ICE connection failed/disconnected, restarting session")
                            close()
                            init(factory, videoTrack, isCaller)
                        }
                    }

                    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                        Log.d(TAG, "ICE Gathering State: $state")
                    }

                    override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                        Log.d(TAG, "Signaling state changed: $state")
                    }

                    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
                    override fun onAddStream(stream: MediaStream?) {}
                    override fun onRemoveStream(stream: MediaStream?) {}
                    override fun onDataChannel(channel: DataChannel?) {}
                    override fun onRenegotiationNeeded() {}
                    override fun onAddTrack(
                        receiver: RtpReceiver?,
                        mediaStreams: Array<out MediaStream>?
                    ) {
                    }

                    override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {}
                    override fun onIceConnectionReceivingChange(receiving: Boolean) {}
                })




            if (peerConnection == null) {
                Log.e(TAG, "PeerConnection creation failed!")
                return@fetchIceServers
            }

            peerConnection?.addTrack(videoTrack, listOf("screenStream"))
            Log.d(TAG, "Video track added: ${videoTrack.id()}")
        }
    }

    private fun createOffer() {
        Log.d(TAG, "createOffer() called")
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("offerToReceiveVideo", "true"))
        }

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                Log.d(TAG, "Offer created: ${sdp?.description}")
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        Log.d(TAG, "Local description set")
                        signalingDoc.set(
                            mapOf(
                                "offer" to mapOf(
                                    "sdp" to sdp?.description,
                                    "type" to sdp?.type?.canonicalForm()
                                ),
                                "timestamp" to System.currentTimeMillis()
                            ),
                            SetOptions.merge()
                        ).addOnSuccessListener {
                            Log.d(TAG, "Offer stored in Firestore")
                        }.addOnFailureListener {
                            Log.e(TAG, "Failed to store offer: ${it.message}")
                            Handler(Looper.getMainLooper()).postDelayed({ createOffer() }, 3000)
                        }
                    }

                    override fun onSetFailure(error: String?) {
                        Log.e(TAG, "Failed to set local description: $error")
                    }

                    override fun onCreateSuccess(sdp: SessionDescription?) {}
                    override fun onCreateFailure(error: String?) {}
                }, sdp)
            }

            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "createOffer failed: $error")
                Handler(Looper.getMainLooper()).postDelayed({ createOffer() }, 3000)
            }

            override fun onSetSuccess() {}
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    private fun scheduleOfferRecreation() {
        Handler(Looper.getMainLooper()).postDelayed({
            if (peerConnection?.remoteDescription == null) {
                Log.d(TAG, "No answer received, recreating offer")
                createOffer()
                scheduleOfferRecreation()
            }
        }, 10000) // Recreate offer every 15 seconds if no answer
    }

    private fun listenForAnswer() {
        answerListener?.remove()
        answerListener = signalingDoc.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error listening for answer: ${error.message}")
                Handler(Looper.getMainLooper()).postDelayed({ listenForAnswer() }, 3000)
                return@addSnapshotListener
            }
            if (snapshot == null || !snapshot.exists()) {
                Log.w(TAG, "Answer snapshot is null or does not exist")
                return@addSnapshotListener
            }

            val data = snapshot.data ?: return@addSnapshotListener
            val answerMap = data["answer"] as? Map<*, *> ?: return@addSnapshotListener
            val sdp = answerMap["sdp"] as? String ?: return@addSnapshotListener
            val type = answerMap["type"] as? String ?: return@addSnapshotListener
            val timestamp = answerMap["timestamp"] as? Long ?: return@addSnapshotListener

            // Only process new answers based on timestamp
            val currentTimestamp = data["timestamp"] as? Long ?: return@addSnapshotListener
            if (timestamp <= (data["lastProcessedAnswerTimestamp"] as? Long ?: 0)) {
                Log.d(TAG, "Answer already processed, skipping.")
                return@addSnapshotListener
            }

            val answer = SessionDescription(SessionDescription.Type.fromCanonicalForm(type), sdp)
            peerConnection?.setRemoteDescription(object : SdpObserver {
                override fun onSetSuccess() {
                    Log.d(TAG, "📡 Answer applied on child side")
                    // Update last processed answer timestamp
                    signalingDoc.update("lastProcessedAnswerTimestamp", currentTimestamp)
                }

                override fun onSetFailure(error: String?) {
                    Log.e(TAG, "❌ Failed to set answer: $error")
                }

                override fun onCreateSuccess(sdp: SessionDescription?) {}
                override fun onCreateFailure(error: String?) {}
            }, answer)
        }
    }

    private fun listenForOfferAndCreateAnswer() {
        offerListener?.remove()
        offerListener = signalingDoc.addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.e(TAG, "Error listening for offer: ${e.message}")
                Handler(Looper.getMainLooper()).postDelayed({ listenForOfferAndCreateAnswer() }, 3000)
                return@addSnapshotListener
            }
            if (snapshot == null || !snapshot.exists()) {
                Log.w(TAG, "Offer snapshot is null or does not exist")
                return@addSnapshotListener
            }

            val offer = snapshot.get("offer") as? Map<*, *> ?: return@addSnapshotListener
            val sdp = offer["sdp"] as? String ?: return@addSnapshotListener
            val sessionDescription = SessionDescription(SessionDescription.Type.OFFER, sdp)

            peerConnection?.setRemoteDescription(object : SdpObserver {
                override fun onSetSuccess() {
                    Log.d(TAG, "Remote offer set. Creating answer...")
                    peerConnection?.createAnswer(object : SdpObserver {
                        override fun onCreateSuccess(sdp: SessionDescription?) {
                            Log.d(TAG, "Answer created")
                            peerConnection?.setLocalDescription(object : SdpObserver {
                                override fun onSetSuccess() {
                                    Log.d(TAG, "Local answer set")
                                    signalingDoc.update(
                                        "answer", mapOf(
                                            "sdp" to sdp?.description,
                                            "type" to sdp?.type?.canonicalForm(),
                                            "timestamp" to System.currentTimeMillis()
                                        )
                                    ).addOnSuccessListener {
                                        Log.d(TAG, "Answer stored in Firestore")
                                    }.addOnFailureListener {
                                        Log.e(TAG, "Failed to store answer: ${it.message}")
                                    }
                                }

                                override fun onSetFailure(error: String?) {
                                    Log.e(TAG, "Failed to set local answer: $error")
                                }

                                override fun onCreateSuccess(sdp: SessionDescription?) {}
                                override fun onCreateFailure(error: String?) {}
                            }, sdp)
                        }

                        override fun onCreateFailure(error: String?) {
                            Log.e(TAG, "createAnswer failed: $error")
                        }

                        override fun onSetSuccess() {}
                        override fun onSetFailure(error: String?) {}
                    }, MediaConstraints())
                }

                override fun onSetFailure(error: String?) {
                    Log.e(TAG, "Failed to set remote offer: $error")
                }

                override fun onCreateSuccess(sdp: SessionDescription?) {}
                override fun onCreateFailure(error: String?) {}
            }, sessionDescription)
        }
    }

    private fun addIceCandidateToFirestore(candidate: IceCandidate) {
        val candidateMap = mapOf(
            "sdpMid" to candidate.sdpMid,
            "sdpMLineIndex" to candidate.sdpMLineIndex,
            "candidate" to candidate.sdp,
            "timestamp" to System.currentTimeMillis()
        )

        val targetCollection = if (isCaller) callerCandidatesCollection else calleeCandidatesCollection
        targetCollection.add(candidateMap)
            .addOnSuccessListener { Log.d(TAG, "ICE candidate sent to Firestore") }
            .addOnFailureListener { Log.e(TAG, "Failed to send ICE candidate: ${it.message}") }
    }

    private fun listenForRemoteIceCandidates() {
        remoteIceListener?.remove()
        val remoteCollection = if (isCaller) calleeCandidatesCollection else callerCandidatesCollection
        remoteIceListener = remoteCollection.addSnapshotListener { snapshots, e ->
            if (e != null) {
                Log.e(TAG, "Error listening for remote ICE candidates: ${e.message}")
                return@addSnapshotListener
            }
            if (snapshots == null) {
                Log.w(TAG, "Remote ICE candidates snapshot is null")
                return@addSnapshotListener
            }
            snapshots.documentChanges.forEach { change ->
                if (change.type == DocumentChange.Type.ADDED) {
                    val data = change.document.data
                    val candidate = IceCandidate(
                        data["sdpMid"] as String,
                        (data["sdpMLineIndex"] as Long).toInt(),
                        data["candidate"] as String
                    )
                    if (peerConnection?.remoteDescription != null) {
                        peerConnection?.addIceCandidate(candidate)
                        Log.d(TAG, "✅ Remote ICE candidate added: ${candidate.sdp}")
                    } else {
                        Log.w(TAG, "Remote description not set yet, skipping ICE candidate")
                    }
                }
            }
        }
    }

    fun close(onComplete: () -> Unit = {}) {
        Log.d(TAG, "Closing FirebaseSignalingClient")
        isCleaningUp = true
        peerConnection?.close()
        peerConnection = null
        offerListener?.remove()
        answerListener?.remove()
        remoteIceListener?.remove()
        cleanUpSignalingData {
            isCleaningUp = false
            onComplete()
        }
    }

    fun fetchIceServers(callback: (List<PeerConnection.IceServer>) -> Unit) {
        val call = RetrofitClient.instance.getIceServers()
        call.enqueue(object : retrofit2.Callback<IceServerResponse> {
            override fun onResponse(
                call: retrofit2.Call<IceServerResponse>,
                response: retrofit2.Response<IceServerResponse>
            ) {
                if (response.isSuccessful) {
                    val iceServers = response.body()?.iceServers?.mapNotNull { server ->
                        val url = server.urls
                        if (url.isEmpty()) return@mapNotNull null
                        val builder = PeerConnection.IceServer.builder(url)
                        server.username?.let { builder.setUsername(it) }
                        server.credential?.let { builder.setPassword(it) }
                        builder.setTlsCertPolicy(PeerConnection.TlsCertPolicy.TLS_CERT_POLICY_INSECURE_NO_CHECK)
                        builder.createIceServer()
                    } ?: emptyList()
                    Log.d(TAG, "Fetched ICE servers: $iceServers")
                    callback(iceServers)
                } else {
                    Log.e(TAG, "Failed to fetch ICE servers: ${response.code()}")
                    // Fallback to default STUN server
                    callback(listOf(
                        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302")
                            .createIceServer()
                    ))
                }
            }

            override fun onFailure(call: retrofit2.Call<IceServerResponse>, t: Throwable) {
                Log.e(TAG, "Failed to fetch ICE servers", t)
                // Fallback to default STUN server
                callback(listOf(
                    PeerConnection.IceServer.builder("stun:stun.l.google.com:19302")
                        .createIceServer()
                ))
            }
        })
    }



}