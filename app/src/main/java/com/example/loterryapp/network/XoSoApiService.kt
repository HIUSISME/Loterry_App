package com.example.loterryapp.network

import com.example.loterryapp.network.models.ApiResponse
import com.google.gson.JsonElement
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface XoSoApiService {
    /**
     * Lấy danh sách kết quả xổ số theo vùng và ngày (YYYY-MM-DD).
     * region: MB, MT, MN, hoặc DIEN_TOAN (Điện toán 123, 6x36, Thần tài 4; mã trò nằm ở province.code)
     */
    @GET("api/v1/vietnam/draws")
    suspend fun getVietnamDraws(
        @Query("region") region: String? = null,
        @Query("date") date: String? = null,
        @Query("limit") limit: Int? = 1
    ): Response<ApiResponse<JsonElement>>

    /**
     * Lấy kết quả mới nhất toàn quốc (tổng hợp 3 miền & Vietlott)
     */
    @GET("api/latest-draws-vn")
    suspend fun getLatestDraws(): Response<JsonElement>

    /**
     * Lấy kết quả xổ số Vietlott (MEGA645, POWER655, MAX3D...)
     */
    @GET("api/vietlott/draws")
    suspend fun getVietlottDraws(
        @Query("type") type: String? = "MEGA645",
        @Query("limit") limit: Int? = 1,
        /** yyyy-MM-dd: chỉ lấy các kỳ quay của ngày này; null = các kỳ mới nhất */
        @Query("date") date: String? = null
    ): Response<ApiResponse<JsonElement>>

    /**
     * Danh sách các đài/tỉnh
     */
    @GET("api/v1/vietnam/provinces")
    suspend fun getProvinces(): Response<JsonElement>

    /**
     * Thống kê số nóng
     */
    @GET("api/stats/hot-numbers")
    suspend fun getHotNumbers(
        @Query("region") region: String? = "MB",
        @Query("limit") limit: Int? = 10
    ): Response<JsonElement>

    /**
     * Thống kê lô gan
     */
    @GET("api/stats/gap")
    suspend fun getGapStats(
        @Query("region") region: String? = "MB"
    ): Response<JsonElement>
}
