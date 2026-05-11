package com.example.chatbot.viewmodel

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.util.concurrent.TimeoutException

class ChatViewModel(
    private val messageRepository: MessageRepository,
    private val apiConfigRepository: ApiConfigRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private var lastNetworkCheck: Long = 0
    private var lastNetworkState: Boolean = true

    fun getMessages(characterId: Long) = messageRepository.getMessagesByCharacterId(characterId)

    fun sendMessage(characterId: Long, content: String, characterPrompt: String, context: Context? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _errorMessage.value = null

            if (!isNetworkAvailable(context)) {
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "网络不可用，请检查网络连接"
                }
                _isLoading.value = false
                return@launch
            }

            val userMessage = Message(characterId = characterId, content = content, isUser = true)
            messageRepository.insertMessage(userMessage)

            val config = apiConfigRepository.getApiConfig()
            if (config == null || config.baseUrl.isEmpty() || config.apiKey.isEmpty()) {
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "请先配置API"
                }
                _isLoading.value = false
                return@launch
            }

            if (!isValidUrl(config.baseUrl)) {
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "API地址格式不正确"
                }
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
                        withContext(Dispatchers.Main) {
                            _errorMessage.value = "API返回数据为空"
                        }
                    }
                } else {
                    val errorMsg = when (response.code()) {
                        400 -> "请求参数错误"
                        401 -> "API密钥无效"
                        403 -> "无访问权限"
                        404 -> "API地址不存在"
                        429 -> "请求过于频繁，请稍后重试"
                        500 -> "服务器内部错误"
                        else -> "API请求失败: ${response.code()}"
                    }
                    withContext(Dispatchers.Main) {
                        _errorMessage.value = errorMsg
                    }
                }
            } catch (e: TimeoutException) {
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "请求超时，请检查网络或API地址"
                }
            } catch (e: java.net.UnknownHostException) {
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "无法解析域名，请检查API地址"
                }
            } catch (e: java.net.SocketTimeoutException) {
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "连接超时，请检查网络"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "网络请求失败: ${e.message}"
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun isNetworkAvailable(context: Context?): Boolean {
        if (context == null) return true

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastNetworkCheck < NETWORK_CHECK_INTERVAL) {
            return lastNetworkState
        }
        lastNetworkCheck = currentTime

        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

            lastNetworkState = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            lastNetworkState
        } catch (e: Exception) {
            true
        }
    }

    private fun isValidUrl(url: String): Boolean {
        return try {
            val urlPattern = Regex("^(https?://)?([\\w.-]+)+(:[0-9]+)?(/.*)?$")
            urlPattern.matches(url) || url.startsWith("http://") || url.startsWith("https://")
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        private const val NETWORK_CHECK_INTERVAL = 5000L
    }
}
