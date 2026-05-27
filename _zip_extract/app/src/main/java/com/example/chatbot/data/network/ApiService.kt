package com.example.chatbot.data.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Url

data class ChatRequest(
    val model: String,
    val messages: List<MessageRequest>,
    val temperature: Double,
    val max_tokens: Int
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
    val role: String,
    val content: String
)

interface ApiService {
    @Headers("Content-Type: application/json")
    @POST
    suspend fun sendMessage(
        @Url url: String,
        @Body body: ChatRequest
    ): Response<ChatResponse>
}
