package com.example.loterryapp.api

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface LotteryApiService {
    @GET("api/v1/vietnam/draws")
    fun getLotteryResults(
        @Header("X-API-Key") apiKey: String,
        @Query("region") region: String,
        @Query("limit") limit: Int = 1
    ): Call<LotteryResponse>
}
