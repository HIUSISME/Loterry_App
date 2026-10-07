package com.example.loterryapp.api

import com.google.gson.annotations.SerializedName

data class LotteryResponse(
    @SerializedName("status") val status: String?,
    @SerializedName("data") val data: List<LotteryDraw>?
)

data class LotteryDraw(
    @SerializedName("province") val province: String?,
    @SerializedName("date") val date: String?,
    @SerializedName("results") val results: LotteryResult?
)

data class LotteryResult(
    @SerializedName("special") val special: List<String>?,
    @SerializedName("g1") val g1: List<String>?,
    @SerializedName("g2") val g2: List<String>?,
    @SerializedName("g3") val g3: List<String>?,
    @SerializedName("g4") val g4: List<String>?,
    @SerializedName("g5") val g5: List<String>?,
    @SerializedName("g6") val g6: List<String>?,
    @SerializedName("g7") val g7: List<String>?,
    @SerializedName("g8") val g8: List<String>?
)
