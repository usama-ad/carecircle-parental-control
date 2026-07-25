package com.example.carecirclechildapp.utils

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    const val BASE_URL = "https://twilio-turn-server-production.up.railway.app/"
    val instance : IceServerApi  by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        retrofit.create(IceServerApi::class.java)

    }

}