package com.example.carecircleparentapp.utils

import com.example.carecircleparentapp.modals.IceServerResponse
import retrofit2.Call
import retrofit2.http.GET

interface IceServerApi {
    @GET("ice")
    fun getIceServers() : Call<IceServerResponse>
}