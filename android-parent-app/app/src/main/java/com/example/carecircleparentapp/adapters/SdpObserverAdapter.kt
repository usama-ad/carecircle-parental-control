package com.example.carecircleparentapp.adapters

import livekit.org.webrtc.SdpObserver
import livekit.org.webrtc.SessionDescription

open class SdpObserverAdapter : SdpObserver {
    override fun onCreateSuccess(sdp: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(p0: String?) {}
    override fun onSetFailure(p0: String?) {}
}