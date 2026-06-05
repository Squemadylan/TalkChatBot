package com.example.chatbot.memory

import com.example.chatbot.data.model.ApiConfig
import com.example.chatbot.data.network.RetrofitClient
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 4 层记忆共用的 LLM 调用工具。和 LongTermMemoryManager 里那套保持一致：
 * 用 OkHttp 直发 /chat/completions，非流式，max_tokens 由调用方控制。
 */
object LlmCall {

    private val gson = Gson()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    suspend fun callJsonText(
        apiConfig: ApiConfig,
        systemPrompt: String,
        userPrompt: String,
        maxTokens: Int = 1500,
        temperature: Double = 0.4
    ): String = withContext(Dispatchers.IO) {
        val base = RetrofitClient.normalizeApiBaseUrl(apiConfig.baseUrl)
        val req = mapOf(
            "model" to apiConfig.model,
            "messages" to listOf(
                mapOf("role" to "system", "content" to systemPrompt),
                mapOf("role" to "user", "content" to userPrompt)
            ),
            "temperature" to temperature.coerceIn(0.0, 2.0),
            "max_tokens" to maxTokens.coerceAtLeast(64),
            "stream" to false
        )
        val http = Request.Builder()
            .url("${base}chat/completions")
            .post(gson.toJson(req).toRequestBody(jsonType))
            .build()
        val client = RetrofitClient.createOkHttpClient(apiConfig.apiKey, logBodies = false)
        client.newCall(http).execute().use { resp ->
            if (!resp.isSuccessful) return@use ""
            val body = resp.body?.string() ?: return@use ""
            val parsed = gson.fromJson(body, Map::class.java)
            val choices = parsed["choices"] as? List<*>
            val c0 = choices?.firstOrNull() as? Map<*, *>
            val msg = c0?.get("message") as? Map<*, *>
            (msg?.get("content") as? String).orEmpty()
        }
    }
}
