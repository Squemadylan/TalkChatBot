package com.example.chatbot.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatbot.data.repository.ApiConfigRepository
import com.example.chatbot.data.repository.MessageRepository
import com.example.chatbot.data.model.Message
import com.example.chatbot.data.network.ChatRequest
import com.example.chatbot.data.network.ChatResponse
import com.example.chatbot.data.network.MessageRequest
import com.example.chatbot.data.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.Response

class ChatViewModel(
    private val messageRepository: MessageRepository,
    private val apiConfigRepository: ApiConfigRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    fun getMessages(characterId: Long) = messageRepository.getMessagesByCharacterId(characterId)

    fun sendMessage(characterId: Long, content: String, characterPrompt: String) = viewModelScope.launch(Dispatchers.IO) {
        _isLoading.value = true
        _errorMessage.value = null

        val userMessage = Message(characterId = characterId, content = content, isUser = true)
        messageRepository.insertMessage(userMessage)

        val config = apiConfigRepository.getApiConfig()
        if (config == null || config.baseUrl.isEmpty() || config.apiKey.isEmpty()) {
            _errorMessage.value = "请先配置API"
            _isLoading.value = false
            return@launch
        }

        try {
            val apiService = RetrofitClient.create(config.baseUrl, config.apiKey)

            val messages = mutableListOf<MessageRequest>()
            if (characterPrompt.isNotEmpty()) {
                messages.add(MessageRequest("system", characterPrompt))
            }
            messages.add(MessageRequest("user", content))

            val request = ChatRequest(
                model = config.model,
                messages = messages,
                temperature = config.temperature,
                max_tokens = config.maxTokens
            )

            val response: Response<ChatResponse> = apiService.sendMessage(
                "${config.baseUrl}chat/completions",
                request
            )

            if (response.isSuccessful) {
                val responseBody = response.body()
                if (responseBody != null && responseBody.choices.isNotEmpty()) {
                    val assistantMessage = Message(
                        characterId = characterId,
                        content = responseBody.choices[0].message.content,
                        isUser = false
                    )
                    messageRepository.insertMessage(assistantMessage)
                } else {
                    _errorMessage.value = "API返回数据为空"
                }
            } else {
                _errorMessage.value = "API请求失败: ${response.code()}"
            }
        } catch (e: Exception) {
            _errorMessage.value = "网络请求失败: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }
}
