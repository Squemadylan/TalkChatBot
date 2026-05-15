package com.example.chatbot.data.network

import com.example.chatbot.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private const val CONNECT_TIMEOUT = 30L
    private const val READ_TIMEOUT = 120L
    private const val WRITE_TIMEOUT = 60L

    /**
     * 与 Retrofit 实例共用同一套超时与鉴权，供流式 SSE 等场景直接使用 OkHttp。
     * @param logBodies 流式响应勿打印 BODY，避免整段响应进日志、拖慢读取。
     */
    fun createOkHttpClient(apiKey: String, logBodies: Boolean): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)

        if (BuildConfig.DEBUG) {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = if (logBodies) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
            }
            builder.addInterceptor(loggingInterceptor)
        }

        return builder
            .addInterceptor { chain ->
                val original = chain.request()
                val rb = original.newBuilder()
                    .addHeader("Authorization", "Bearer $apiKey")
                if (original.header("Content-Type") == null) {
                    rb.addHeader("Content-Type", "application/json")
                }
                if (original.header("Accept") == null) {
                    rb.addHeader("Accept", "application/json")
                }
                chain.proceed(rb.build())
            }
            .retryOnConnectionFailure(true)
            .build()
    }

    fun create(baseUrl: String, apiKey: String): ApiService {
        val client = createOkHttpClient(apiKey, logBodies = BuildConfig.DEBUG)

        val processedBaseUrl = normalizeBaseUrl(baseUrl)

        return Retrofit.Builder()
            .baseUrl(processedBaseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    /** 与创建 Retrofit 实例时相同的 baseUrl 规范化，供拼接 chat/completions 等路径使用 */
    fun normalizeApiBaseUrl(baseUrl: String): String = normalizeBaseUrl(baseUrl)

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
