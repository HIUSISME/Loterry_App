package com.example.loterryapp.network

import com.example.loterryapp.BuildConfig

object AppConfig {
    const val BASE_URL = "https://xosoapi.online/"
    // Key được đọc từ local.properties lúc build (xem app/build.gradle.kts)
    val API_KEY: String = BuildConfig.XOSO_API_KEY
    val GEMINI_API_KEY: String = BuildConfig.GEMINI_API_KEY
    val GEMINI_MODEL: String = BuildConfig.GEMINI_MODEL
}
