package com.example.chatbot.data.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Url

data class ChatRequest(
    val model: String,
    val messages: List<MessageRequest>,
    val temperature: Double,
    @SerializedName("max_tokens")
    val maxTokens: Int,
    /** 显式关闭流式，避免部分兼容网关默认 SSE 导致非 JSON 正文无法解析 */
    val stream: Boolean = false
)

data class MessageRequest(
    val role: String,
    val content: String
)

data class ChatResponse(
    val choices: List<Choice>
)

data class Choice(
    val message: MessageResponse
)

data class MessageResponse(
    val role: String? = null,
    val content: String? = null
)

interface ApiService {
    @Headers("Content-Type: application/json")
    @POST
    suspend fun sendMessage(
        @Url url: String,
        @Body body: ChatRequest
    ): ChatResponse
}
