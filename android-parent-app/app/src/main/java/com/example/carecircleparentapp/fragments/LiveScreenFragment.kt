package com.example.carecircleparentapp.fragments

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.annotation.RequiresApi
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.example.carecircleparentapp.R
import com.example.carecircleparentapp.databinding.FragmentLiveScreenBinding
import com.example.carecircleparentapp.modals.IceServerResponse
import com.example.carecircleparentapp.utils.FirebaseUtils
import com.google.firebase.firestore.*
import livekit.org.webrtc.*
import androidx.navigation.fragment.findNavController
import java.nio.ByteBuffer

class LiveScreenFragment : Fragment() {
    private lateinit var binding: FragmentLiveScreenBinding
    private lateinit var remoteRenderer: SurfaceViewRenderer
    private lateinit var eglBase: EglBase
    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null

    private var signalingDoc: DocumentReference? = null
    private lateinit var callerCandidatesCollection: CollectionReference
    private lateinit var calleeCandidatesCollection: CollectionReference

    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var childId: String
    private var offerListener: ListenerRegistration? = null
    private var remoteIceListener: ListenerRegistration? = null
    private val remoteIceCandidates = mutableListOf<IceCandidate>()
    private var parentDataChannel: DataChannel? = null
    private var remoteVideoTrack: VideoTrack? = null

    private val pingHandler = Handler(Looper.getMainLooper())
    private val pingIntervalMs = 5000L
    private var isUiVisible = true
    private val pingRunnable = object : Runnable {
        override fun run() {
            try {
                if (parentDataChannel?.state() == DataChannel.State.OPEN) {
                    val buf = DataChannel.Buffer(ByteBuffer.wrap("ping".toByteArray()), false)
                    parentDataChannel?.send(buf)
                    Log.d("WebRTC", "Parent ping sent")
                }
            } catch (e: Exception) {
                Log.e("WebRTC", "Failed to send ping: ${e.message}")
            } finally {
                pingHandler.postDelayed(this, pingIntervalMs)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentLiveScreenBinding.inflate(inflater, container, false)
        return binding.root
    }


    @SuppressLint("SetTextI18n")
    @RequiresApi(Build.VERSION_CODES.S)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnStart.isVisible = false
        binding.spinnerProgressBar.visibility = View.VISIBLE // Show spinner progress bar
        setupSpinner { selectedChildId ->
            if (!isAdded || isDetached) {
                binding.spinnerProgressBar.visibility = View.GONE
                Log.d("WebRTC", "Fragment not attached, skipping spinner update")
                return@setupSpinner
            }
            childId = selectedChildId
            binding.btnStart.isVisible = true
            binding.spinnerProgressBar.visibility = View.GONE // Hide spinner progress bar
            Log.d("WebRTC", "onViewCreated: $selectedChildId")
        }
        remoteRenderer = binding.remoteView
        remoteRenderer.setZOrderMediaOverlay(false)
        remoteRenderer.setZOrderOnTop(false)
        remoteRenderer.visibility = View.VISIBLE
        Log.d("WebRTC", "SurfaceViewRenderer initialized with visibility: ${remoteRenderer.visibility}")

        eglBase = EglBase.create()
        try {
            remoteRenderer.init(eglBase.eglBaseContext, object : RendererCommon.RendererEvents {
                override fun onFirstFrameRendered() {
                    Log.d("WebRTC", "✅ First video frame rendered")
                }

                override fun onFrameResolutionChanged(width: Int, height: Int, rotation: Int) {
                    Log.d("WebRTC", "Frame resolution changed: ${width}x$height, rotation: $rotation")
                }
            })
            remoteRenderer.setEnableHardwareScaler(false)
            Log.d("WebRTC", "SurfaceViewRenderer initialized with EGL context")
        } catch (e: Exception) {
            Log.e("WebRTC", "Failed to initialize SurfaceViewRenderer: ${e.message}")
        }
        setupPeerConnectionFactory()
        binding.btnStart.setOnClickListener {
            if (binding.btnStart.text == "Stop Monitoring") {
                stopMonitoring()
                binding.btnStart.text = "Start Monitoring"
            } else {
                binding.webrtcProgressBar.visibility = View.VISIBLE // Show WebRTC progress bar
                startMonitoring()
                binding.btnStart.text = "Stop Monitoring"
            }
        }

        // Add tap listener to toggle UI visibility
        binding.remoteViewContainer.setOnClickListener {
            Log.d("RemoteCheck", "onViewCreated: touched remote")
            toggleUiVisibility()
        }
    }

    private fun toggleUiVisibility() {
        isUiVisible = !isUiVisible
        val duration = 300L

        if (isUiVisible) {
            binding.uiOverlay.apply {
                visibility = View.VISIBLE
                alpha = 0f
                animate().alpha(1f).setDuration(duration).start()
            }
        } else {
            binding.uiOverlay.animate()
                .alpha(0f)
                .setDuration(duration)
                .withEndAction { binding.uiOverlay.visibility = View.GONE }
                .start()
        }
    }


    private fun setupSpinner(childIdCallback: (String) -> Unit) {
        FirebaseUtils.getChildNamesList { list ->
            if (!isAdded || isDetached) {
                binding.spinnerProgressBar.visibility = View.GONE
                Log.d("WebRTC", "Fragment not attached, skipping child list update")
                return@getChildNamesList
            }
            Log.d("WebRTC", "Child name-ID pairs: $list")

            val childNames = list.map { it.first }
            val nameToIdMap = list.associate { it.first to it.second }

            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, childNames)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerChildren.adapter = adapter

            binding.spinnerChildren.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val selectedName = childNames[position]
                    val selectedChildId = nameToIdMap[selectedName]
                    if (selectedChildId == null) {
                        Log.e("WebRTC", "No child ID found for name: $selectedName")
                        return
                    }
                    childIdCallback(selectedChildId)
                    Log.d("WebRTC", "Selected child: $selectedName, ID: $selectedChildId")
                }
                override fun onNothingSelected(p0: AdapterView<*>?) {
                }
            }

            binding.spinnerProgressBar.visibility = View.GONE // Hide spinner progress bar
        }
    }

    private fun setupPeerConnectionFactory() {
        try {
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(requireContext())
                    .createInitializationOptions()
            )
            val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
            val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

            peerConnectionFactory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory()
            Log.d("WebRTC", "PeerConnectionFactory initialized")
        } catch (e: Exception) {
            Log.e("WebRTC", "Failed to initialize PeerConnectionFactory: ${e.message}")
        }
    }

    private fun cleanUpSignalingData(callback: () -> Unit) {
        Log.d("WebRTC", "Cleaning up signaling data")
        signalingDoc?.get()?.addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val timestamp = snapshot.getLong("timestamp") ?: 0
                val currentTime = System.currentTimeMillis()
                if (currentTime - timestamp > 30_000) { // 30 seconds
                    signalingDoc?.delete()
                    callerCandidatesCollection.get().addOnSuccessListener { snap ->
                        snap.documents.forEach { it.reference.delete() }
                        calleeCandidatesCollection.get().addOnSuccessListener { snap ->
                            snap.documents.forEach { it.reference.delete() }
                            Log.d("WebRTC", "Signaling data cleaned up")
                            callback()
                        }
                    }
                } else {
                    Log.d("WebRTC", "Skipping cleanup, active session detected")
                    callback()
                }
            } else {
                Log.d("WebRTC", "No signaling data to clean")
                callback()
            }
        }?.addOnFailureListener {
            Log.e("WebRTC", "Failed to check signaling data: ${it.message}")
            callback()
        }
    }

    private fun startMonitoring() {
        // Reinitialize the renderer
        try {
            remoteRenderer = binding.remoteView
            remoteRenderer.setZOrderMediaOverlay(true)
            remoteRenderer.setZOrderOnTop(true)
            remoteRenderer.visibility = View.VISIBLE
            remoteRenderer.init(eglBase.eglBaseContext, object : RendererCommon.RendererEvents {
                override fun onFirstFrameRendered() {
                    Log.d("WebRTC", "✅ First video frame rendered")
                }

                override fun onFrameResolutionChanged(width: Int, height: Int, rotation: Int) {
                    Log.d("WebRTC", "Frame resolution changed: ${width}x$height, rotation: $rotation")
                }
            })
            remoteRenderer.setEnableHardwareScaler(false)
            Log.d("WebRTC", "SurfaceViewRenderer reinitialized")
        } catch (e: Exception) {
            Log.e("WebRTC", "Failed to reinitialize SurfaceViewRenderer: ${e.message}")
        }

        // Set up signaling
        signalingDoc = firestore.collection("children_data")
            .document(childId)
            .collection("signaling")
            .document("session")

        callerCandidatesCollection = signalingDoc?.collection("callerCandidates")!!
        calleeCandidatesCollection = signalingDoc?.collection("calleeCandidates")!!

        // Force clean signaling data
        forceCleanSignaling {
            createPeerConnection()
            Handler(Looper.getMainLooper()).postDelayed({
                listenForOffer()
                listenForRemoteIceCandidates()
            }, 2000)
        }

        // Retry if no connection
        Handler(Looper.getMainLooper()).postDelayed({
            if (peerConnection?.iceConnectionState() != PeerConnection.IceConnectionState.CONNECTED &&
                peerConnection?.iceConnectionState() != PeerConnection.IceConnectionState.COMPLETED) {
                Log.w("WebRTC", "Connection not established, retrying offer")
                listenForOffer()
            }
        }, 10000)
    }

    private fun forceCleanSignaling(callback: () -> Unit) {
        Log.d("WebRTC", "Force cleaning signaling data...")
        signalingDoc?.delete()?.addOnCompleteListener {
            callerCandidatesCollection.get().addOnSuccessListener { snap ->
                snap.documents.forEach { it.reference.delete() }
                calleeCandidatesCollection.get().addOnSuccessListener { snap2 ->
                    snap2.documents.forEach { it.reference.delete() }
                    signalingDoc?.set(
                        mapOf(
                            "lastProcessedOfferTimestamp" to 0,
                            "lastProcessedAnswerTimestamp" to 0
                        )
                    )?.addOnSuccessListener {
                        Log.d("WebRTC", "Signaling fully reset")
                        callback()
                    }?.addOnFailureListener {
                        Log.e("WebRTC", "Failed to reset timestamps: ${it.message}")
                        callback()
                    }
                }
            }
        }
    }

    private fun createPeerConnection() {
        binding.webrtcProgressBar.visibility = View.VISIBLE // Show WebRTC progress bar
        FirebaseUtils.fetchIceServersFromFirestore(childId) { iceServers ->
            if (!isAdded || isDetached) {
                binding.webrtcProgressBar.visibility = View.GONE
                Log.d("WebRTC", "Fragment not attached, skipping ICE servers update")
                return@fetchIceServersFromFirestore
            }
            binding.webrtcProgressBar.visibility = View.GONE // Hide WebRTC progress bar
            Log.d("ServersList", "createPeerConnection: $iceServers ")

            val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
                iceTransportsType = PeerConnection.IceTransportsType.ALL
                iceCandidatePoolSize = 10
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            }

            try {
                peerConnection = peerConnectionFactory.createPeerConnection(
                    rtcConfig,
                    object : PeerConnection.Observer {
                        override fun onIceCandidate(candidate: IceCandidate) {
                            Log.d("WebRTC", "📡 ICE Candidate: ${candidate.sdp}")
                            if (candidate.sdp.contains("typ relay")) {
                                Log.d("WebRTC", "✅ Relay candidate generated")
                            }
                            calleeCandidatesCollection.add(
                                mapOf(
                                    "sdpMid" to candidate.sdpMid,
                                    "sdpMLineIndex" to candidate.sdpMLineIndex,
                                    "candidate" to candidate.sdp,
                                    "timestamp" to System.currentTimeMillis()
                                )
                            ).addOnFailureListener {
                                Log.e("WebRTC", "Failed to send ICE candidate: ${it.message}")
                            }
                        }

                        override fun onDataChannel(channel: DataChannel?) {
                            Log.d("WebRTC", "Parent datachannel opened: ${channel?.label()}")
                            parentDataChannel = channel
                            parentDataChannel?.registerObserver(object : DataChannel.Observer {
                                override fun onBufferedAmountChange(previousAmount: Long) {}
                                override fun onStateChange() {
                                    Log.d(
                                        "WebRTC",
                                        "Parent datachannel state: ${parentDataChannel?.state()}"
                                    )
                                    if (parentDataChannel?.state() == DataChannel.State.OPEN) {
                                        pingHandler.removeCallbacksAndMessages(null)
                                        pingHandler.post(pingRunnable)
                                    } else {
                                        pingHandler.removeCallbacksAndMessages(null)
                                    }
                                }

                                override fun onMessage(buffer: DataChannel.Buffer) {
                                    try {
                                        val bytes = ByteArray(buffer.data.remaining())
                                        buffer.data.get(bytes)
                                        Log.d(
                                            "WebRTC",
                                            "Parent got message on datachannel: ${String(bytes)}"
                                        )
                                    } catch (ex: Exception) {
                                        Log.e(
                                            "WebRTC",
                                            "Parent datachannel read error: ${ex.message}"
                                        )
                                    }
                                }
                            })
                        }

                        override fun onTrack(transceiver: RtpTransceiver?) {
                            val track = transceiver?.receiver?.track()
                            if (track is VideoTrack) {
                                remoteVideoTrack = track
                                Log.d("WebRTC", "🎥 Received VideoTrack: ${track.id()}, enabled: ${track.enabled()}")
                                if (!isAdded || isDetached) {
                                    Log.d("WebRTC", "Fragment not attached, skipping video track update")
                                    return
                                }
                                activity?.runOnUiThread {
                                    if (!isAdded || isDetached) {
                                        Log.d("WebRTC", "Fragment not attached, skipping renderer update")
                                        return@runOnUiThread
                                    }
                                    try {
                                        // Remove any existing sink to avoid conflicts
                                        remoteVideoTrack?.removeSink(remoteRenderer)
                                        // Add the new sink
                                        remoteVideoTrack?.addSink(remoteRenderer)
                                        remoteVideoTrack?.setEnabled(true)
                                        Log.d("WebRTC", "📺 Sink added to remoteRenderer, track state: ${track.state()}")
                                    } catch (e: Exception) {
                                        Log.e("WebRTC", "Failed to add sink to renderer: ${e.message}")
                                    }
                                }
                            }
                        }

                        override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                            Log.d("WebRTC", "ICE connection state: $newState")
                            if (newState == PeerConnection.IceConnectionState.FAILED || newState == PeerConnection.IceConnectionState.DISCONNECTED) {
                                Log.e(
                                    "WebRTC",
                                    "ICE connection failed/disconnected, restarting ICE"
                                )
                                peerConnection?.restartIce()
                            }
                        }

                        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                            Log.d("WebRTC", "ICE Gathering State: $state")
                        }

                        override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                            Log.d("WebRTC", "Signaling state: $state")
                        }

                        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
                        override fun onAddStream(stream: MediaStream?) {}
                        override fun onRemoveStream(stream: MediaStream?) {}
                        override fun onRenegotiationNeeded() {}
                        override fun onAddTrack(
                            receiver: RtpReceiver?,
                            mediaStreams: Array<out MediaStream>?
                        ) {}

                        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {}
                        override fun onIceConnectionReceivingChange(receiving: Boolean) {}
                    })
                Log.d("WebRTC", "PeerConnection created")
            } catch (e: Exception) {
                Log.e("WebRTC", "Failed to create PeerConnection: ${e.message}")
            }

            peerConnection?.addTransceiver(
                MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
                RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
            )

            try {
                parentDataChannel =
                    peerConnection?.createDataChannel("parent-dc", DataChannel.Init())
                parentDataChannel?.registerObserver(object : DataChannel.Observer {
                    override fun onBufferedAmountChange(previousAmount: Long) {}
                    override fun onStateChange() {
                        Log.d(
                            "WebRTC",
                            "Parent created datachannel state: ${parentDataChannel?.state()}"
                        )
                        if (parentDataChannel?.state() == DataChannel.State.OPEN) {
                            pingHandler.removeCallbacksAndMessages(null)
                            pingHandler.post(pingRunnable)
                        }
                    }

                    override fun onMessage(buffer: DataChannel.Buffer) {
                        try {
                            val bytes = ByteArray(buffer.data.remaining())
                            buffer.data.get(bytes)
                            Log.d("WebRTC", "Parent local datachannel received: ${String(bytes)}")
                        } catch (ex: Exception) {
                            Log.e("WebRTC", "Parent datachannel read error: ${ex.message}")
                        }
                    }
                })
            } catch (e: Exception) {
                Log.w("WebRTC", "Failed to create local datachannel: ${e.message}")
            }

            peerConnection?.createDataChannel("dummy", DataChannel.Init())
        }
    }

    private fun listenForOffer() {
        remoteIceCandidates.clear()
        offerListener?.remove()
        offerListener = signalingDoc?.addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.e("WebRTC", "Error listening for offer: ${e.message}")
                Handler(Looper.getMainLooper()).postDelayed({ listenForOffer() }, 3000)
                return@addSnapshotListener
            }
            if (snapshot == null || !snapshot.exists()) {
                Log.w("WebRTC", "Offer snapshot is null or does not exist")
                return@addSnapshotListener
            }

            val offer = snapshot.get("offer") as? Map<*, *> ?: return@addSnapshotListener
            val sdp = offer["sdp"] as? String ?: return@addSnapshotListener
            val type = offer["type"] as? String ?: return@addSnapshotListener
            val timestamp = snapshot.getLong("timestamp") ?: return@addSnapshotListener

            // Only process new offers based on timestamp
            val lastProcessedOfferTimestamp = snapshot.getLong("lastProcessedOfferTimestamp") ?: 0
            if (timestamp <= lastProcessedOfferTimestamp) {
                Log.d("WebRTC", "Offer already processed, skipping.")
                return@addSnapshotListener
            }

            val sessionDescription = SessionDescription(SessionDescription.Type.fromCanonicalForm(type), sdp)
            peerConnection?.setRemoteDescription(object : SdpObserver {
                override fun onSetSuccess() {
                    Log.d("WebRTC", "✅ Remote offer set. Creating answer...")
                    createAnswer()
                    remoteIceCandidates.forEach { candidate ->
                        peerConnection?.addIceCandidate(candidate)
                        Log.d("WebRTC", "✅ Applied buffered ICE candidate: ${candidate.sdp}")
                    }
                    remoteIceCandidates.clear()
                    // Update last processed offer timestamp
                    signalingDoc?.update("lastProcessedOfferTimestamp", timestamp)
                }

                override fun onSetFailure(error: String?) {
                    Log.e("WebRTC", "❌ Failed to set remote description: $error")
                }

                override fun onCreateSuccess(desc: SessionDescription?) {}
                override fun onCreateFailure(error: String?) {}
            }, sessionDescription)
        }
    }

    private fun createAnswer() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("offerToReceiveVideo", "true"))
        }

        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        Log.d("WebRTC", "✅ Local description set (answer)")
                        signalingDoc?.update(
                            mapOf(
                                "answer" to mapOf(
                                    "type" to sdp?.type?.canonicalForm(),
                                    "sdp" to sdp?.description,
                                    "timestamp" to System.currentTimeMillis()
                                )
                            )
                        )?.addOnSuccessListener {
                            Log.d("WebRTC", "✅ Answer uploaded to Firestore")
                        }?.addOnFailureListener {
                            Log.e("WebRTC", "❌ Failed to upload answer: ${it.message}")
                            Handler(Looper.getMainLooper()).postDelayed({ createAnswer() }, 3000)
                        }
                    }

                    override fun onSetFailure(error: String?) {
                        Log.e("WebRTC", "❌ Failed to set local description: $error")
                    }

                    override fun onCreateSuccess(desc: SessionDescription?) {}
                    override fun onCreateFailure(error: String?) {}
                }, sdp)
            }

            override fun onCreateFailure(error: String?) {
                Log.e("WebRTC", "❌ Failed to create answer: $error")
                Handler(Looper.getMainLooper()).postDelayed({ createAnswer() }, 3000)
            }

            override fun onSetSuccess() {}
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    private fun listenForRemoteIceCandidates() {
        remoteIceListener?.remove()
        remoteIceListener = callerCandidatesCollection.addSnapshotListener { snapshots, e ->
            if (e != null) {
                Log.e("WebRTC", "Error listening for remote ICE candidates: ${e.message}")
                return@addSnapshotListener
            }
            if (snapshots == null) {
                Log.w("WebRTC", "Remote ICE candidates snapshot is null")
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
                        Log.d("WebRTC", "✅ Added remote ICE candidate: ${candidate.sdp}")
                    } else {
                        Log.w("WebRTC", "Remote description not set yet, buffering ICE candidate")
                        remoteIceCandidates.add(candidate)
                    }
                }
            }
        }
    }

    private fun stopMonitoring() {
        Log.d("WebRTC", "Stopping monitoring")

        // Remove listeners
        offerListener?.remove()
        remoteIceListener?.remove()
        pingHandler.removeCallbacksAndMessages(null)
        remoteIceCandidates.clear()

        // Close and dispose of peer connection
        peerConnection?.close()
        peerConnection = null

        // Close data channel
        try {
            parentDataChannel?.close()
            parentDataChannel = null
        } catch (e: Exception) {
            Log.e("WebRTC", "Error closing datachannel: ${e.message}")
        }

        // Clean up renderer and video track
        try {
            remoteVideoTrack?.removeSink(remoteRenderer)
            remoteVideoTrack?.dispose()
            remoteVideoTrack = null
            remoteRenderer.clearImage()
            remoteRenderer.release() // Release the renderer
            Log.d("WebRTC", "Renderer and video track released")
        } catch (e: Exception) {
            Log.e("WebRTC", "Failed to release renderer: ${e.message}")
        }

        // Clean up signaling data
        cleanUpSignalingData {}
        binding.webrtcProgressBar.visibility = View.GONE // Hide WebRTC progress bar
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopMonitoring()
        try {
            eglBase.release()
            Log.d("WebRTC", "EGL context released")
        } catch (e: Exception) {
            Log.e("WebRTC", "Failed to release EGL context: ${e.message}")
        }
        try {
            peerConnectionFactory.dispose()
            Log.d("WebRTC", "PeerConnectionFactory disposed")
        } catch (e: Exception) {
            Log.e("WebRTC", "Failed to dispose PeerConnectionFactory: ${e.message}")
        }
    }
}