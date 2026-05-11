package com.example.chatbot.data.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private const val CONNECT_TIMEOUT = 30L
    private const val READ_TIMEOUT = 60L
    private const val WRITE_TIMEOUT = 60L

    fun create(baseUrl: String, apiKey: String): ApiService {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                try {
                    val request = chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer $apiKey")
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Accept", "application/json")
                        .build()
                    chain.proceed(request)
                } catch (e: Exception) {
                    throw e
                }
            }
            .retryOnConnectionFailure(true)
            .build()

        val processedBaseUrl = normalizeBaseUrl(baseUrl)

        return Retrofit.Builder()
            .baseUrl(processedBaseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    private fun normalizeBaseUrl(baseUrl: String): String {
        var url = baseUrl.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        if (!url.endsWith("/")) {
            url = "$url/"
        }
        return url
    }
}
