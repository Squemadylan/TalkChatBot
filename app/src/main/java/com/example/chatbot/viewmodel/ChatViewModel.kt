package com.example.chatbot.viewmodel

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import android.widget.Toast
import com.example.chatbot.R
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.example.chatbot.App
import com.example.chatbot.BuildConfig
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
import com.example.chatbot.util.LongTermMemoryManager
import com.example.chatbot.memory.MemoryConfig
import com.example.chatbot.memory.MemoryPipeline
import com.example.chatbot.memory.PromptBuilder
import com.example.chatbot.util.ModelDefaultTokens
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
        if (id > 0L) {
            cleanupStaleAssistantPlaceholders(id)
        }
    }

    private fun cleanupStaleAssistantPlaceholders(characterId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                messageRepository.markStaleStreamingMessagesFailed(
                    characterId = characterId,
                    activeMessageId = activeStreamingAssistantMessageId
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cleanup stale assistant placeholders", e)
            }
        }
    }

    private var lastNetworkCheck: Long = 0
    private var lastNetworkState: Boolean = true

    fun deleteAllMessages(context: Context? = null) {
        val appCtx = context?.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            messageRepository.deleteAllMessages()
            appCtx?.let {
                try {
                    LongTermMemoryManager.deleteAllMemories(it)
                } catch (e: Exception) {
                    Log.e("ChatViewModel", "Failed to delete all memories", e)
                }
            }
        }
    }

    fun deleteMessagesForCharacter(characterId: Long, context: Context? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            messageRepository.deleteMessagesByCharacterId(characterId)
            context?.let {
                try {
                    LongTermMemoryManager.deleteMemory(it, characterId)
                } catch (e: Exception) {
                    Log.e("ChatViewModel", "Failed to delete memory", e)
                }
            }
        }
    }

    fun deleteChatMessagesOnlyForCharacter(characterId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            messageRepository.deleteMessagesByCharacterId(characterId)
        }
    }

    fun deleteLongTermMemoryForCharacter(characterId: Long, context: Context? = null) {
        val appCtx = context?.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            if (appCtx == null) {
                _errorMessage.postValue("应用状态异常，请稍后重试")
                return@launch
            }
            LongTermMemoryManager.deleteMemory(appCtx, characterId)
            _errorMessage.postValue("长久记忆已删除")
        }
    }

    fun deleteMessage(messageId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            messageRepository.deleteMessageById(messageId)
        }
    }

    fun toggleStarred(messageId: Long, starred: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            messageRepository.updateStarred(messageId, starred, if (starred) System.currentTimeMillis() else null)
        }
    }

    fun searchMessagesInChat(characterId: Long, query: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val results = messageRepository.searchMessages(characterId, query)
            _searchResults.postValue(results)
        }
    }

    private val _searchResults = MutableLiveData<List<Message>>()
    val searchResults: LiveData<List<Message>> = _searchResults

    private val _assistantMessageToSpeak = MutableLiveData<String?>()
    val assistantMessageToSpeak: LiveData<String?> = _assistantMessageToSpeak

    fun exportChat(characterId: Long, characterName: String, format: String, context: Context?) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.postValue(true)
            try {
                val messages = messageRepository.getAllMessagesByCharacterId(characterId)
                if (messages.isEmpty()) {
                    _errorMessage.postValue("没有聊天记录可导出")
                    return@launch
                }
                val sb = StringBuilder()
                if (format == "md") {
                    sb.appendLine("# 与「${characterName}」的对话")
                    sb.appendLine()
                    sb.appendLine("> 导出时间：${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}")
                    sb.appendLine()
                }
                for (msg in messages) {
                    val speaker = if (msg.isUser) "用户" else characterName
                    val time = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(msg.timestamp))
                    if (format == "md") {
                        sb.appendLine("**$speaker** [$time]")
                        sb.appendLine(msg.content)
                        sb.appendLine()
                    } else {
                        sb.appendLine("$speaker [$time]: ${msg.content}")
                    }
                }
                val fileName = "${characterName}_${System.currentTimeMillis()}.$format"
                val content = sb.toString()
                if (context != null) {
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_SUBJECT, "聊天记录：$characterName")
                        putExtra(android.content.Intent.EXTRA_TEXT, content)
                    }
                    withContext(Dispatchers.Main) {
                        context.startActivity(android.content.Intent.createChooser(intent, "导出聊天记录"))
                    }
                }
                _errorMessage.postValue("已准备导出")
            } catch (e: Exception) {
                _errorMessage.postValue("导出失败：${e.message}")
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    /** 删除该角色的全部聊天记录与角色本体 */
    fun deleteCharacterWithMessages(characterId: Long, context: Context? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            messageRepository.deleteMessagesByCharacterId(characterId)
            characterRepository.deleteCharacterById(characterId)
            context?.let {
                try {
                    LongTermMemoryManager.deleteMemory(it, characterId)
                } catch (e: Exception) {
                    Log.e("ChatViewModel", "Failed to delete memory", e)
                }
            }
        }
    }

    fun saveLongTermMemoryNow(characterId: Long, context: Context? = null) {
        val appCtx = context?.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            if (activeStreamingAssistantMessageId > 0L) {
                _errorMessage.postValue("当前回复还没结束，请稍后再保存长期记忆")
                return@launch
            }

            _isLoading.postValue(true)
            _errorMessage.postValue("正在手动保存长期记忆...")
            try {
                if (!isNetworkAvailable(appCtx)) {
                    _errorMessage.postValue("网络不可用，请检查网络连接")
                    return@launch
                }

                if (appCtx == null) {
                    _errorMessage.postValue("应用状态异常，请稍后重试")
                    return@launch
                }

                val character = characterRepository.getCharacterById(characterId)
                if (character == null) {
                    _errorMessage.postValue("角色不存在，无法保存长期记忆")
                    return@launch
                }
                if (!character.enableLongTermMemory) {
                    _errorMessage.postValue("请先在角色编辑中开启长期记忆")
                    return@launch
                }

                val config = apiConfigRepository.getApiConfig()
                if (config == null || config.baseUrl.isBlank() || config.apiKey.isBlank() || config.model.isBlank()) {
                    _errorMessage.postValue("请先配置大模型API")
                    return@launch
                }
                if (!isValidUrl(config.baseUrl)) {
                    _errorMessage.postValue("API地址格式不正确")
                    return@launch
                }

                val allMessages = messageRepository.getAllMessagesByCharacterId(characterId)
                if (allMessages.size < MIN_MESSAGES_FOR_MANUAL_MEMORY) {
                    _errorMessage.postValue("对话太少，至少需要 $MIN_MESSAGES_FOR_MANUAL_MEMORY 条消息再保存长期记忆")
                    return@launch
                }

                val result = LongTermMemoryManager.forceRegenerateMemory(
                    context = appCtx,
                    characterId = characterId,
                    messages = allMessages,
                    apiConfig = config
                )
                val memory = result.getOrNull().orEmpty()
                if (result.isSuccess && memory.isNotBlank()) {
                    _errorMessage.postValue("长期记忆已手动保存")
                } else {
                    _errorMessage.postValue("长期记忆生成失败，请稍后重试")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Manual long-term memory save failed", e)
                _errorMessage.postValue("长期记忆保存失败：${e.message}")
            } finally {
                _isLoading.postValue(false)
            }
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
            sendApiRequestAndUpdateMessage(characterId, characterPrompt, content, context)
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

    private suspend fun sendApiRequestAndUpdateMessage(characterId: Long, characterPrompt: String, userContent: String, context: Context?) {
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
        var chatSucceeded = false
        val appCtx = context?.applicationContext
        var longTermMemoryEnabled = false
        var streamResultRef: Result<String>? = null

        try {
            val apiService = RetrofitClient.create(config.baseUrl, config.apiKey)

            val prefs = appCtx?.getSharedPreferences(App.PREFS_NAME, Context.MODE_PRIVATE)
            val strength = prefs?.getInt(App.KEY_MEMORY_CONTEXT_COUNT, 5)?.coerceIn(0, 10) ?: 5
            val replyStyle = prefs?.getInt(App.KEY_REPLY_STYLE, App.REPLY_STYLE_STANDARD)
                ?: App.REPLY_STYLE_STANDARD

            val userDisplayName = prefs?.getString(App.KEY_USER_DISPLAY_NAME, null)?.trim()
                ?.takeIf { it.isNotEmpty() } ?: "用户"
            val userPersona = prefs?.getString(App.KEY_USER_PERSONA, null)?.trim().orEmpty()
            val character = characterRepository.getCharacterById(characterId)
            val charName = character?.name?.trim().orEmpty()
            longTermMemoryEnabled = character?.enableLongTermMemory == true

            assistantRowId = messageRepository.insertMessage(
                Message(
                    characterId = characterId,
                    content = "",
                    isUser = false,
                    status = Message.STATUS_STREAMING
                )
            )
            activeStreamingAssistantMessageId = assistantRowId

            fun plug(src: String) = UserPromptPlaceholders.apply(src, userDisplayName, userPersona, charName)

            val messages = mutableListOf<MessageRequest>()
            if (characterPrompt.isNotEmpty()) {
                messages.add(MessageRequest("system", plug(characterPrompt)))
            }
            replyStyleInstruction(replyStyle)?.let { instruction ->
                messages.add(MessageRequest("system", instruction))
            }

            if (longTermMemoryEnabled && appCtx != null) {
                // v2.0：4 层记忆 + Mermaid 画布拼成单一 system 消息
                val built = PromptBuilder.build(appCtx, characterId, userContent)
                if (built.systemMessage?.isNotBlank() == true) {
                    messages.add(MessageRequest("system", built.systemMessage))
                } else {
                    // 退化：旧行为
                    val legacy = LongTermMemoryManager.getCachedMemoryForChat(appCtx, characterId)
                    if (legacy.isNotBlank()) {
                        messages.add(MessageRequest("system", "【长期记忆】\n$legacy"))
                    }
                }
            }

            if (strength > 0) {
                val hist = messageRepository
                    .getRecentMessagesChronological(characterId, strength)
                    .filter { it.isUser || it.status != Message.STATUS_FAILED }
                for (m in hist) {
                    messages.add(
                        MessageRequest(
                            role = if (m.isUser) "user" else "assistant",
                            content = plug(m.content)
                        )
                    )
                }
            }

            val maxTokens = config.maxTokens.coerceIn(1, ModelDefaultTokens.MAX_OUTPUT_TOKENS_CAP)
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "chat/completions max_tokens=$maxTokens model=${config.model}")
            }

            val request = ChatRequest(
                model = config.model,
                messages = messages,
                temperature = config.temperature,
                maxTokens = maxTokens,
                stream = true
            )

            val base = RetrofitClient.normalizeApiBaseUrl(config.baseUrl)
            val gson = Gson()
            Log.d(TAG, "Request JSON: ${gson.toJson(request)}")
            Log.d(TAG, "base=$base")
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
                streamResultRef = streamResult

                val textOut = accumulated.toString().ifBlank { streamResult.getOrNull().orEmpty() }

                if (textOut.isBlank()) {
                    markAssistantFailed(assistantRowId, "API返回内容为空")
                    clearStreamingAssistantIfMatches(assistantRowId)
                    if (persistNonStreamingReply(characterId, request, apiService, base)) {
                        chatSucceeded = true
                        messageRepository.deleteMessageById(assistantRowId)
                    } else {
                        withContext(Dispatchers.Main) {
                            _errorMessage.value = streamResult.exceptionOrNull()?.message
                                ?: "API返回内容为空"
                        }
                    }
                } else {
                    markAssistantCompleted(assistantRowId)
                    if (streamResult.isFailure) {
                        markAssistantFailed(
                            assistantRowId,
                            "回复可能未完整接收：${streamResult.exceptionOrNull()?.message ?: ""}".trim()
                        )
                        withContext(Dispatchers.Main) {
                            _errorMessage.value =
                                "回复可能未完整接收：${streamResult.exceptionOrNull()?.message ?: ""}"
                        }
                    } else {
                        chatSucceeded = true
                    }
                }
            } finally {
                httpResponse.close()
            }
        } catch (e: HttpException) {
            val errorMsg = when (e.code()) {
                400 -> "请求参数错误"
                401 -> "API密钥无效"
                403 -> "无访问权限"
                404 -> "API地址不存在"
                429 -> "请求过于频繁，请稍后重试"
                500 -> "服务器内部错误"
                else -> "API请求失败: ${e.code()}"
            }
            markAssistantFailed(assistantRowId, errorMsg)
            withContext(Dispatchers.Main) {
                _errorMessage.value = errorMsg
            }
        } catch (e: TimeoutException) {
            markAssistantFailed(assistantRowId, "请求超时，请检查网络或API地址")
            withContext(Dispatchers.Main) {
                _errorMessage.value = "请求超时，请检查网络或API地址"
            }
        } catch (e: java.net.UnknownHostException) {
            markAssistantFailed(assistantRowId, "无法解析域名，请检查API地址")
            withContext(Dispatchers.Main) {
                _errorMessage.value = "无法解析域名，请检查API地址"
            }
        } catch (e: java.net.SocketTimeoutException) {
            markAssistantFailed(assistantRowId, "连接超时，请检查网络")
            withContext(Dispatchers.Main) {
                _errorMessage.value = "连接超时，请检查网络"
            }
        } catch (e: Exception) {
            markAssistantFailed(assistantRowId, "网络请求失败: ${e.message}")
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
            if (chatSucceeded && longTermMemoryEnabled && appCtx != null) {
                scheduleLongTermMemoryRefresh(appCtx, characterId, config)
                // v2.0：直接走 4 层 pipeline；不再依赖「每日首次」之类的粗粒度触发
                val assistantText = accumulated.toString().ifBlank { streamResultRef?.getOrNull().orEmpty() }
                MemoryPipeline.onTurnComplete(
                    context = appCtx,
                    characterId = characterId,
                    apiConfig = config,
                    userMessage = Message(characterId = characterId, content = userContent, isUser = true, timestamp = System.currentTimeMillis()),
                    assistantMessage = Message(
                        characterId = characterId,
                        content = assistantText,
                        isUser = false,
                        timestamp = System.currentTimeMillis()
                    ),
                    historyTail = withContext(Dispatchers.IO) {
                        runCatching { messageRepository.getAllMessagesByCharacterId(characterId) }.getOrDefault(emptyList())
                    }
                )
            }
        }
    }

    /** 对话成功后在后台更新长期记忆，不阻塞本次回复。 */
    private fun scheduleLongTermMemoryRefresh(
        appCtx: Context,
        characterId: Long,
        config: com.example.chatbot.data.model.ApiConfig
    ) {
        val toastCtx = appCtx.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val allMessages = messageRepository.getAllMessagesByCharacterId(characterId)
                val success = LongTermMemoryManager.refreshMemoryIfNeeded(
                    appCtx,
                    characterId,
                    config,
                    allMessages,
                    onGenerationStarted = {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                toastCtx,
                                toastCtx.getString(R.string.long_term_memory_generating),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
                if (success) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            toastCtx,
                            toastCtx.getString(R.string.long_term_memory_generated),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Long-term memory background refresh failed", e)
            }
        }
    }

    private fun clearStreamingAssistantIfMatches(messageId: Long) {
        if (activeStreamingAssistantMessageId == messageId) {
            activeStreamingAssistantMessageId = -1L
        }
    }

    private suspend fun markAssistantCompleted(assistantRowId: Long) {
        if (assistantRowId < 0L) return
        runCatching {
            messageRepository.updateMessageStatus(
                assistantRowId,
                Message.STATUS_COMPLETED
            )
        }
        val character = characterRepository.getCharacterById(activeCharacterId.value ?: 0L)
        if (character?.enableAutoRead == true) {
            val messages = messageRepository.getAllMessagesByCharacterId(activeCharacterId.value ?: 0L)
            val lastMsg = messages.lastOrNull()
            lastMsg?.let {
                _assistantMessageToSpeak.postValue(it.content)
            }
        }
    }

    private suspend fun markAssistantFailed(assistantRowId: Long, error: String) {
        if (assistantRowId < 0L) return
        val message = error.ifBlank { "回复中断，请重新发送。" }
        runCatching {
            messageRepository.updateMessageStatus(
                assistantRowId,
                Message.STATUS_FAILED,
                message
            )
        }
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

    private fun replyStyleInstruction(style: Int): String? = when (style) {
        App.REPLY_STYLE_SHORT -> "【回复策略】请优先使用简短、直接、易读的回复；除非用户明确要求展开，否则控制篇幅。"
        App.REPLY_STYLE_DETAILED -> "【回复策略】请在保持连贯的前提下，适当增加情绪、细节、动作和上下文承接，让回复更细腻。"
        else -> null
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
        private const val TAG = "ChatViewModel"
        private const val NETWORK_CHECK_INTERVAL = 5000L
        private const val MIN_MESSAGES_FOR_MANUAL_MEMORY = 5
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
