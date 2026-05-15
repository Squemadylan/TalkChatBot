package com.example.chatbot.viewmodel

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.example.chatbot.App
import com.example.chatbot.data.repository.ApiConfigRepository
import com.example.chatbot.data.repository.CharacterRepository
import com.example.chatbot.data.repository.MessageRepository
import com.example.chatbot.data.model.Message
import com.example.chatbot.data.network.ApiService
import com.example.chatbot.data.network.ChatRequest
import com.example.chatbot.data.network.MessageRequest
import com.example.chatbot.data.network.OpenAiChatResponseReader
import com.example.chatbot.data.network.RetrofitClient
import com.example.chatbot.ui.chat.MemoryHubRow
import com.example.chatbot.util.UserPromptPlaceholders
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.util.concurrent.TimeoutException
import kotlin.jvm.Volatile

class ChatViewModel(
    private val messageRepository: MessageRepository,
    private val apiConfigRepository: ApiConfigRepository,
    private val characterRepository: CharacterRepository
) : ViewModel() {

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage

    /** 流式结束后通知 UI：对该 id 的助手气泡再跑一次 Markdown（流式过程中用纯文本避免闪烁） */
    private val _assistantMarkdownRefresh = MutableLiveData<Long>()
    val assistantMarkdownRefresh: LiveData<Long> = _assistantMarkdownRefresh

    @Volatile
    private var activeStreamingAssistantMessageId: Long = -1L

    /** 当前正在流式接收的助手消息行 id；无则 ≤0 */
    fun streamingAssistantMessageIdForUi(): Long = activeStreamingAssistantMessageId

    val memoryHubRows: LiveData<List<MemoryHubRow>> = combine(
        characterRepository.allCharacters,
        messageRepository.getAllMessagesFlow()
    ) { characters, messages ->
        val lastByChar = messages
            .groupBy { it.characterId }
            .mapValues { (_, list) -> list.maxByOrNull { it.timestamp } }
        characters.map { ch ->
            MemoryHubRow(ch, lastByChar[ch.id])
        }
    }
        .flowOn(Dispatchers.Default)
        .asLiveData()

    private val activeCharacterId = MutableLiveData(0L)

    val messagesForActiveCharacter: LiveData<List<Message>> = activeCharacterId.switchMap { id ->
        if (id == 0L) {
            val empty = MutableLiveData<List<Message>>()
            empty.value = emptyList()
            empty
        } else {
            messageRepository.getMessagesByCharacterId(id).asLiveData()
        }
    }

    fun setActiveCharacterId(id: Long) {
        activeCharacterId.value = id
    }

    private var lastNetworkCheck: Long = 0
    private var lastNetworkState: Boolean = true

    fun deleteAllMessages() {
        viewModelScope.launch(Dispatchers.IO) {
            messageRepository.deleteAllMessages()
        }
    }

    fun deleteMessagesForCharacter(characterId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            messageRepository.deleteMessagesByCharacterId(characterId)
        }
    }

    /** 删除该角色的全部聊天记录与角色本体 */
    fun deleteCharacterWithMessages(characterId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            messageRepository.deleteMessagesByCharacterId(characterId)
            characterRepository.deleteCharacterById(characterId)
        }
    }

    fun sendMessage(characterId: Long, content: String, characterPrompt: String, context: Context? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.postValue(true)
            _errorMessage.postValue(null)

            if (!isNetworkAvailable(context)) {
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "网络不可用，请检查网络连接"
                }
                _isLoading.postValue(false)
                return@launch
            }

            val userMessage = Message(characterId = characterId, content = content, isUser = true)
            messageRepository.insertMessage(userMessage)
            sendApiRequestAndUpdateMessage(characterId, characterPrompt, context)
        }
    }

    fun sendGreetingMessage(characterId: Long, greeting: String, characterPrompt: String, context: Context? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            _errorMessage.postValue(null)
            val greetingMessage = Message(characterId = characterId, content = greeting, isUser = false)
            messageRepository.insertMessage(greetingMessage)
            // 仅展示角色配置的主开场白，不自动请求大模型生成第二条
        }
    }

    private suspend fun sendApiRequestAndUpdateMessage(characterId: Long, characterPrompt: String, context: Context?) {
        val config = apiConfigRepository.getApiConfig()
        if (config == null || config.baseUrl.isEmpty() || config.apiKey.isEmpty()) {
            withContext(Dispatchers.Main) {
                _errorMessage.value = "请先配置大模型API"
            }
            _isLoading.postValue(false)
            return
        }

        if (!isValidUrl(config.baseUrl)) {
            withContext(Dispatchers.Main) {
                _errorMessage.value = "API地址格式不正确"
            }
            _isLoading.postValue(false)
            return
        }

        val accumulated = StringBuilder()
        var receivedChunk = false
        var assistantRowId = -1L

        try {
            val apiService = RetrofitClient.create(config.baseUrl, config.apiKey)

            val appCtx = context?.applicationContext
            val prefs = appCtx?.getSharedPreferences(App.PREFS_NAME, Context.MODE_PRIVATE)
            val strength = prefs?.getInt(App.KEY_MEMORY_CONTEXT_COUNT, 5)?.coerceIn(0, 10) ?: 5

            val userDisplayName = prefs?.getString(App.KEY_USER_DISPLAY_NAME, null)?.trim()
                ?.takeIf { it.isNotEmpty() } ?: "用户"
            val userPersona = prefs?.getString(App.KEY_USER_PERSONA, null)?.trim().orEmpty()
            val charName = characterRepository.getCharacterById(characterId)?.name?.trim().orEmpty()
            fun plug(src: String) =
                UserPromptPlaceholders.apply(src, userDisplayName, userPersona, charName)

            val messages = mutableListOf<MessageRequest>()
            if (characterPrompt.isNotEmpty()) {
                messages.add(MessageRequest("system", plug(characterPrompt)))
            }
            if (strength > 0) {
                val hist = messageRepository.getRecentMessagesChronological(characterId, strength)
                for (m in hist) {
                    messages.add(
                        MessageRequest(
                            role = if (m.isUser) "user" else "assistant",
                            content = plug(m.content)
                        )
                    )
                }
            }

            val request = ChatRequest(
                model = config.model,
                messages = messages,
                temperature = config.temperature,
                max_tokens = config.maxTokens,
                stream = true
            )

            val base = RetrofitClient.normalizeApiBaseUrl(config.baseUrl)
            assistantRowId = messageRepository.insertMessage(
                Message(characterId = characterId, content = "", isUser = false)
            )
            activeStreamingAssistantMessageId = assistantRowId

            val gson = Gson()
            val streamClient = RetrofitClient.createOkHttpClient(config.apiKey, logBodies = false)
            val jsonMediaType = "application/json; charset=utf-8".toMediaType()

            val httpRequest = Request.Builder()
                .url("${base}chat/completions")
                .post(gson.toJson(request).toRequestBody(jsonMediaType))
                .header("Accept", "text/event-stream, application/json")
                .build()

            val httpResponse = streamClient.newCall(httpRequest).execute()
            try {
                val streamResult = OpenAiChatResponseReader.consume(httpResponse, gson) { piece ->
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        messageRepository.updateMessageContent(assistantRowId, accumulated.toString())
                        if (!receivedChunk) {
                            receivedChunk = true
                            _isLoading.postValue(false)
                        }
                    }
                }

                val textOut = accumulated.toString().ifBlank { streamResult.getOrNull().orEmpty() }

                if (textOut.isBlank()) {
                    messageRepository.deleteMessageById(assistantRowId)
                    clearStreamingAssistantIfMatches(assistantRowId)
                    if (!persistNonStreamingReply(characterId, request, apiService, base)) {
                        withContext(Dispatchers.Main) {
                            _errorMessage.value = streamResult.exceptionOrNull()?.message
                                ?: "API返回内容为空"
                        }
                    }
                } else if (streamResult.isFailure) {
                    withContext(Dispatchers.Main) {
                        _errorMessage.value =
                            "回复可能未完整接收：${streamResult.exceptionOrNull()?.message ?: ""}"
                    }
                }
            } finally {
                httpResponse.close()
            }
        } catch (e: HttpException) {
            cleanupEmptyAssistantPlaceholder(assistantRowId, accumulated, receivedChunk)
            val errorMsg = when (e.code()) {
                400 -> "请求参数错误"
                401 -> "API密钥无效"
                403 -> "无访问权限"
                404 -> "API地址不存在"
                429 -> "请求过于频繁，请稍后重试"
                500 -> "服务器内部错误"
                else -> "API请求失败: ${e.code()}"
            }
            withContext(Dispatchers.Main) {
                _errorMessage.value = errorMsg
            }
        } catch (e: TimeoutException) {
            cleanupEmptyAssistantPlaceholder(assistantRowId, accumulated, receivedChunk)
            withContext(Dispatchers.Main) {
                _errorMessage.value = "请求超时，请检查网络或API地址"
            }
        } catch (e: java.net.UnknownHostException) {
            cleanupEmptyAssistantPlaceholder(assistantRowId, accumulated, receivedChunk)
            withContext(Dispatchers.Main) {
                _errorMessage.value = "无法解析域名，请检查API地址"
            }
        } catch (e: java.net.SocketTimeoutException) {
            cleanupEmptyAssistantPlaceholder(assistantRowId, accumulated, receivedChunk)
            withContext(Dispatchers.Main) {
                _errorMessage.value = "连接超时，请检查网络"
            }
        } catch (e: Exception) {
            cleanupEmptyAssistantPlaceholder(assistantRowId, accumulated, receivedChunk)
            withContext(Dispatchers.Main) {
                _errorMessage.value = "网络请求失败: ${e.message}"
            }
        } finally {
            val sid = activeStreamingAssistantMessageId
            activeStreamingAssistantMessageId = -1L
            _isLoading.postValue(false)
            if (sid > 0L) {
                _assistantMarkdownRefresh.postValue(sid)
            }
        }
    }

    private fun clearStreamingAssistantIfMatches(messageId: Long) {
        if (activeStreamingAssistantMessageId == messageId) {
            activeStreamingAssistantMessageId = -1L
        }
    }

    private suspend fun cleanupEmptyAssistantPlaceholder(
        assistantRowId: Long,
        accumulated: StringBuilder,
        receivedChunk: Boolean
    ) {
        if (assistantRowId < 0L) return
        if (receivedChunk || accumulated.isNotEmpty()) return
        runCatching { messageRepository.deleteMessageById(assistantRowId) }
        clearStreamingAssistantIfMatches(assistantRowId)
    }

    private suspend fun persistNonStreamingReply(
        characterId: Long,
        request: ChatRequest,
        apiService: ApiService,
        base: String
    ): Boolean {
        val responseBody = apiService.sendMessage(
            "${base}chat/completions",
            request.copy(stream = false)
        )
        if (responseBody.choices.isEmpty()) return false
        val text = responseBody.choices[0].message.content
        if (text.isNullOrBlank()) return false
        messageRepository.insertMessage(
            Message(characterId = characterId, content = text, isUser = false)
        )
        return true
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

class ChatViewModelFactory(
    private val messageRepository: MessageRepository,
    private val apiConfigRepository: ApiConfigRepository,
    private val characterRepository: CharacterRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(messageRepository, apiConfigRepository, characterRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
