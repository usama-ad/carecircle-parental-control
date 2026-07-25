package com.example.carecirclechildapp.modals

data class IceServerResponse(
    val identity: String,
    val iceServers: List<IceServer>
)

data class IceServer(
    val urls: String,
    val username: String? = null,
    val credential: String? = null
)
