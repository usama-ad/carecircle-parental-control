package com.example.carecirclechildapp.utils

import com.example.carecirclechildapp.modals.IceServerResponse
import retrofit2.Call
import retrofit2.http.GET

interface IceServerApi {
    @GET("ice")
    fun getIceServers(): Call<IceServerResponse>

}