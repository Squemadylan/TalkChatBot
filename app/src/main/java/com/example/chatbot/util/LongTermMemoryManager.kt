package com.example.chatbot.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.chatbot.data.model.Message
import com.example.chatbot.data.network.ChatRequest
import com.example.chatbot.data.network.MessageRequest
import com.example.chatbot.data.network.RetrofitClient
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LongTermMemoryManager {
    private const val TAG = "LongTermMemoryManager"
    /** 参与记忆摘要的最大消息条数，避免超长聊天记录拖慢后台任务 */
    private const val MAX_MESSAGES_FOR_SUMMARY = 500

    private val refreshMutexByCharacter = ConcurrentHashMap<Long, Mutex>()
    private const val MEMORY_DIR = "long_term_memory"
    private const val MEMORY_FILE_PREFIX = "memory_"
    private const val MEMORY_FILE_EXTENSION = ".md"
    private const val PREFS_NAME = "long_term_memory_prefs"
    private const val KEY_LAST_GENERATE_DATE = "last_generate_date_"
    private const val KEY_LAST_MESSAGE_TIMESTAMP = "last_message_timestamp_"
    
    private const val MEMORY_SUMMARY_PROMPT_INITIAL = """
请分析以下对话历史，生成一个结构化的长期记忆摘要。

要求：
1. 提取关键人物关系和互动模式
2. 记录重要事件、约定和决策
3. 追踪情感发展和关系变化
4. 识别反复出现的主题和兴趣
5. 保持简洁，总长度不超过1500字

对话历史：
%s

请用以下格式输出（直接输出内容，不要有其他说明）：
# 角色关系
[描述角色之间的关系和互动模式]

# 重要事件
[按时间顺序列出重要事件]

# 已建立的约定
[记录双方达成的约定或承诺]

# 情感发展
[描述关系中的情感变化]

# 关键信息
[其他需要记住的重要信息]
"""

    private const val MEMORY_SUMMARY_PROMPT_UPDATE = """
请根据【已有记忆】和【新增对话】，更新长期记忆摘要。

要求：
1. 保留已有记忆中重要且未过期的信息
2. 合并新增对话中的新事件和信息
3. 更新关系描述和情感发展
4. 删除已不再重要的旧信息
5. 保持简洁，总长度不超过1500字

【已有记忆】：
%s

【新增对话（自 %s 之后的新对话）】
%s

请用以下格式输出（直接输出内容，不要有其他说明）：
# 角色关系
[描述角色之间的关系和互动模式]

# 重要事件
[按时间顺序列出重要事件]

# 已建立的约定
[记录双方达成的约定或承诺]

# 情感发展
[描述关系中的情感变化]

# 关键信息
[其他需要记住的重要信息]
"""

    private fun getDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun getTimestampString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun getLastGenerateDate(context: Context, characterId: Long): String? {
        return getPrefs(context).getString(KEY_LAST_GENERATE_DATE + characterId, null)
    }

    private fun setLastGenerateDate(context: Context, characterId: Long, date: String) {
        getPrefs(context).edit()
            .putString(KEY_LAST_GENERATE_DATE + characterId, date)
            .apply()
    }

    private fun getLastMessageTimestamp(context: Context, characterId: Long): Long {
        return getPrefs(context).getLong(KEY_LAST_MESSAGE_TIMESTAMP + characterId, 0L)
    }

    private fun setLastMessageTimestamp(context: Context, characterId: Long, timestamp: Long) {
        getPrefs(context).edit()
            .putLong(KEY_LAST_MESSAGE_TIMESTAMP + characterId, timestamp)
            .apply()
    }

    private fun clearLastState(context: Context, characterId: Long) {
        getPrefs(context).edit()
            .remove(KEY_LAST_GENERATE_DATE + characterId)
            .remove(KEY_LAST_MESSAGE_TIMESTAMP + characterId)
            .apply()
    }

    internal data class MemoryMetadata(
        val version: Int = 1,
        val createdAt: String = "",
        val updatedAt: String = "",
        val lastMessageTimestamp: Long = 0L,
        val lastMessageDate: String = ""
    )

    private fun parseMemoryMetadata(content: String): Pair<MemoryMetadata?, String> {
        if (!content.startsWith("<!--")) {
            return Pair(null, content)
        }
        
        val endIndex = content.indexOf("-->")
        if (endIndex == -1) {
            return Pair(null, content)
        }
        
        val metadataStr = content.substring(4, endIndex).trim()
        val actualContent = content.substring(endIndex + 3).trim()
        
        var version = 1
        var createdAt = ""
        var updatedAt = ""
        var lastMessageTimestamp = 0L
        var lastMessageDate = ""
        
        metadataStr.split("\n").forEach { line ->
            val parts = line.split(":", limit = 2)
            if (parts.size == 2) {
                val key = parts[0].trim()
                val value = parts[1].trim()
                when (key) {
                    "version" -> version = value.toIntOrNull() ?: 1
                    "created_at" -> createdAt = value
                    "updated_at" -> updatedAt = value
                    "last_message_timestamp" -> lastMessageTimestamp = value.toLongOrNull() ?: 0L
                    "last_message_date" -> lastMessageDate = value
                }
            }
        }
        
        return Pair(
            MemoryMetadata(version, createdAt, updatedAt, lastMessageTimestamp, lastMessageDate),
            actualContent
        )
    }

    private fun buildMemoryWithMetadata(metadata: MemoryMetadata, content: String): String {
        return buildString {
            appendLine("<!--")
            appendLine("version: ${metadata.version}")
            appendLine("created_at: ${metadata.createdAt}")
            appendLine("updated_at: ${metadata.updatedAt}")
            appendLine("last_message_timestamp: ${metadata.lastMessageTimestamp}")
            appendLine("last_message_date: ${metadata.lastMessageDate}")
            append("-->")
            if (content.isNotBlank()) {
                appendLine()
                append(content)
            }
        }
    }

    private suspend fun getMemoryFile(context: Context, characterId: Long): File {
        return withContext(Dispatchers.IO) {
            val memoryDir = File(context.filesDir, MEMORY_DIR)
            if (!memoryDir.exists()) {
                memoryDir.mkdirs()
            }
            File(memoryDir, "$MEMORY_FILE_PREFIX$characterId$MEMORY_FILE_EXTENSION")
        }
    }

    suspend fun loadMemory(context: Context, characterId: Long): String? {
        return withContext(Dispatchers.IO) {
            try {
                val file = getMemoryFile(context, characterId)
                if (file.exists()) {
                    val content = file.readText()
                    val (_, actualContent) = parseMemoryMetadata(content)
                    if (actualContent.isNotBlank()) actualContent else null
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load memory for character $characterId", e)
                null
            }
        }
    }

    private suspend fun saveMemory(context: Context, characterId: Long, content: String, lastMessageTimestamp: Long) {
        withContext(Dispatchers.IO) {
            try {
                val file = getMemoryFile(context, characterId)
                val existingContent = if (file.exists()) file.readText() else ""
                val (existingMetadata, _) = parseMemoryMetadata(existingContent)
                
                val metadata = MemoryMetadata(
                    version = 1,
                    createdAt = existingMetadata?.createdAt ?: getTimestampString(),
                    updatedAt = getTimestampString(),
                    lastMessageTimestamp = lastMessageTimestamp,
                    lastMessageDate = getDateString()
                )
                
                val finalContent = buildMemoryWithMetadata(metadata, content)
                file.writeText(finalContent)
                Log.d(TAG, "Saved memory for character $characterId, size: ${content.length} chars, timestamp: $lastMessageTimestamp")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save memory for character $characterId", e)
            }
        }
    }

    suspend fun deleteMemory(context: Context, characterId: Long) {
        withContext(Dispatchers.IO) {
            try {
                val file = getMemoryFile(context, characterId)
                if (file.exists()) {
                    val deleted = file.delete()
                    if (deleted) {
                        Log.d(TAG, "Deleted memory file for character $characterId")
                    }
                }
                clearLastState(context, characterId)
                Log.d(TAG, "Cleared memory state for character $characterId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete memory for character $characterId", e)
            }
        }
    }

    suspend fun deleteAllMemories(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val memoryDir = File(context.filesDir, MEMORY_DIR)
                if (memoryDir.exists()) {
                    memoryDir.listFiles()?.forEach { file ->
                        if (file.isFile && file.name.startsWith(MEMORY_FILE_PREFIX)) {
                            file.delete()
                        }
                    }
                }
                getPrefs(context).edit().clear().apply()
                Log.d(TAG, "Deleted all long-term memories")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete all memories", e)
            }
        }
    }

    suspend fun generateAndSaveMemory(
        context: Context,
        characterId: Long,
        allMessages: List<Message>,
        apiConfig: com.example.chatbot.data.model.ApiConfig
    ): Result<String> = withContext(Dispatchers.IO) {
        Log.d(TAG, "╔══════════════════════════════════════════════════════╗")
        Log.d(TAG, "║         [generateAndSaveMemory] 记忆生成开始      ║")
        Log.d(TAG, "╠══════════════════════════════════════════════════════╣")
        
        try {
            if (allMessages.isEmpty()) {
                Log.w(TAG, "║ ⚠ 警告：消息列表为空")
                Log.d(TAG, "╚══════════════════════════════════════════════════════╝")
                return@withContext Result.success("")
            }
            
            Log.d(TAG, "║ CharacterID: $characterId")
            Log.d(TAG, "║ Total Messages: ${allMessages.size}")

            val savedMemory = loadMemory(context, characterId)
            val lastMessageTimestamp = getLastMessageTimestamp(context, characterId)
            val today = getDateString()

            val latestMessage = allMessages.maxByOrNull { it.timestamp }
            val latestTimestamp = latestMessage?.timestamp ?: 0L
            
            Log.d(TAG, "╠══════════════════════════════════════════════════════╣")
            Log.d(TAG, "║ Has Existing Memory: ${savedMemory != null}")
            Log.d(TAG, "║ Last Message Timestamp: $lastMessageTimestamp")
            Log.d(TAG, "║ Latest Message Timestamp: $latestTimestamp")
            
            val isInitialGeneration = savedMemory.isNullOrBlank() || lastMessageTimestamp == 0L
            Log.d(TAG, "║ Generation Mode: ${if (isInitialGeneration) "INITIAL (完整重建)" else "UPDATE (增量更新)"}")

            val summary = if (isInitialGeneration) {
                Log.d(TAG, "╠══════════════════════════════════════════════════════╣")
                Log.d(TAG, "║ → 模式1：初始生成，使用所有对话")
                
                val conversationText = buildConversationText(allMessages)
                Log.d(TAG, "║ Conversation Text Length: ${conversationText.length}")
                
                if (conversationText.length < 50) {
                    Log.w(TAG, "║ ⚠ 对话内容太短，跳过生成")
                    Log.d(TAG, "╚══════════════════════════════════════════════════════╝")
                    return@withContext Result.success("")
                }
                
                Log.d(TAG, "║ → 调用API生成初始摘要...")
                val result = generateSummaryInitial(conversationText, apiConfig)
                Log.d(TAG, "║ ← API返回: ${if (result.isNotBlank()) "成功 (${result.length} chars)" else "失败"}")
                result
            } else {
                val newMessages = allMessages.filter { it.timestamp > lastMessageTimestamp }
                
                Log.d(TAG, "╠══════════════════════════════════════════════════════╣")
                Log.d(TAG, "║ → 模式2：增量更新")
                Log.d(TAG, "║ New Messages Count: ${newMessages.size}")
                
                if (newMessages.isEmpty()) {
                    Log.d(TAG, "║ ✓ 没有新增对话，返回已有记忆")
                    Log.d(TAG, "╚══════════════════════════════════════════════════════╝")
                    return@withContext Result.success(savedMemory ?: "")
                }
                
                val lastMessageTime = Date(lastMessageTimestamp)
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                val timeStr = dateFormat.format(lastMessageTime)
                
                Log.d(TAG, "║ Since: $timeStr")
                
                val newConversationText = buildConversationText(newMessages)
                Log.d(TAG, "║ New Conversation Text Length: ${newConversationText.length}")
                
                if (newConversationText.length < 20) {
                    Log.w(TAG, "║ ⚠ 新增对话内容太短，跳过生成")
                    Log.d(TAG, "╚══════════════════════════════════════════════════════╝")
                    return@withContext Result.success(savedMemory ?: "")
                }
                
                Log.d(TAG, "║ → 调用API生成更新摘要...")
                val result = generateSummaryUpdate(savedMemory ?: "", timeStr, newConversationText, apiConfig)
                Log.d(TAG, "║ ← API返回: ${if (result.isNotBlank()) "成功 (${result.length} chars)" else "失败，使用原记忆"}")
                result
            }

            if (summary.isNotBlank()) {
                Log.d(TAG, "╠══════════════════════════════════════════════════════╣")
                Log.d(TAG, "║ → 保存记忆到文件...")
                saveMemory(context, characterId, summary, latestTimestamp)
                setLastGenerateDate(context, characterId, today)
                setLastMessageTimestamp(context, characterId, latestTimestamp)
                Log.d(TAG, "║ ✓ 记忆保存成功")
                Log.d(TAG, "║ Final Memory Size: ${summary.length} chars")
                Log.d(TAG, "║ Updated Date: $today")
                Log.d(TAG, "║ Updated Timestamp: $latestTimestamp")
                Log.d(TAG, "╚══════════════════════════════════════════════════════╝")
                Result.success(summary)
            } else {
                Log.e(TAG, "╠══════════════════════════════════════════════════════╣")
                Log.e(TAG, "║ ✗ 摘要生成失败")
                Log.d(TAG, "╚══════════════════════════════════════════════════════╝")
                Result.failure(Exception("Failed to generate memory summary"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "╔══════════════════════════════════════════════════════╗")
            Log.e(TAG, "║ ✗ 异常发生: ${e.javaClass.simpleName}")
            Log.e(TAG, "║ Message: ${e.message}")
            Log.e(TAG, "╚══════════════════════════════════════════════════════╝")
            Log.e(TAG, "StackTrace:", e)
            Result.failure(e)
        }
    }

    private fun buildConversationText(messages: List<Message>): String {
        return messages.joinToString("\n") { msg ->
            val role = if (msg.isUser) "用户" else "AI"
            "[$role] ${msg.content}"
        }
    }

    private suspend fun generateSummaryInitial(
        conversationText: String,
        apiConfig: com.example.chatbot.data.model.ApiConfig
    ): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "    ╔═══════════════════════════════════════════════════════╗")
        Log.d(TAG, "    ║   [generateSummaryInitial] 初始摘要生成 (API调用)    ║")
        Log.d(TAG, "    ╠═══════════════════════════════════════════════════════╣")
        try {
            val truncatedText = if (conversationText.length > 8000) {
                Log.d(TAG, "    ║ ⚠ 对话过长，已截断至8000字符")
                conversationText.take(8000) + "\n...(对话过长，已截断)"
            } else {
                Log.d(TAG, "    ║ ✓ 文本长度: ${conversationText.length} 字符")
                conversationText
            }

            Log.d(TAG, "    ╠═══════════════════════════════════════════════════════╣")
            Log.d(TAG, "    ║ API配置:")
            Log.d(TAG, "    ║   - Base URL: ${apiConfig.baseUrl}")
            Log.d(TAG, "    ║   - Model: ${apiConfig.model}")
            Log.d(TAG, "    ║   - Temperature: ${apiConfig.temperature}")
            Log.d(TAG, "    ║   - Max Tokens: 1000")

            val prompt = MEMORY_SUMMARY_PROMPT_INITIAL.format(truncatedText)

            // 使用原始模型名称（SiliconFlow要求）
            val modelName = apiConfig.model
            
            Log.d(TAG, "    ║ Model: $modelName")
            
            val request = ChatRequest(
                model = modelName,
                messages = listOf(MessageRequest("user", prompt)),
                temperature = apiConfig.temperature.coerceIn(0.0, 2.0),
                maxTokens = 2048,
                stream = false
            )

            val base = RetrofitClient.normalizeApiBaseUrl(apiConfig.baseUrl)
            val gson = Gson()
            val jsonMediaType = "application/json".toMediaType()
            val requestBody = gson.toJson(request)
            
            Log.d(TAG, "    ║ → 发送HTTP请求...")

            val httpRequest = Request.Builder()
                .url("${base}chat/completions")
                .post(requestBody.toRequestBody(jsonMediaType))
                .build()

            val client = RetrofitClient.createOkHttpClient(apiConfig.apiKey, logBodies = false)
            val response = client.newCall(httpRequest).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "No error body"
                Log.e(TAG, "    ║ ✗ API请求失败: HTTP ${response.code}")
                Log.e(TAG, "    ║ Error Body: $errorBody")
                Log.e(TAG, "    ║ Request URL: ${httpRequest.url}")
                Log.e(TAG, "    ║ Request Headers: ${httpRequest.headers}")
                response.close()
                Log.d(TAG, "    ╚═══════════════════════════════════════════════════════╝")
                return@withContext ""
            }

            val responseBody = response.body?.string() ?: ""
            val parsed = gson.fromJson(responseBody, Map::class.java)
            val choices = parsed["choices"] as? List<*>
            val firstChoice = choices?.firstOrNull() as? Map<*, *>
            val message = firstChoice?.get("message") as? Map<*, *>
            val content = message?.get("content") as? String ?: ""

            response.close()
            Log.d(TAG, "    ║ ✓ API调用成功")
            Log.d(TAG, "    ║ 返回内容长度: ${content.length} 字符")
            Log.d(TAG, "    ╚═══════════════════════════════════════════════════════╝")
            content
        } catch (e: Exception) {
            Log.e(TAG, "    ╔═══════════════════════════════════════════════════════╗")
            Log.e(TAG, "    ║ ✗ API调用异常: ${e.javaClass.simpleName}")
            Log.e(TAG, "    ║ Message: ${e.message}")
            Log.e(TAG, "    ╚═══════════════════════════════════════════════════════╝")
            ""
        }
    }

    private suspend fun generateSummaryUpdate(
        existingMemory: String,
        timeStr: String,
        newConversationText: String,
        apiConfig: com.example.chatbot.data.model.ApiConfig
    ): String = withContext(Dispatchers.IO) {
        try {
            val truncatedNewText = if (newConversationText.length > 6000) {
                newConversationText.take(6000) + "\n...(新增对话过长，已截断)"
            } else {
                newConversationText
            }

            val prompt = MEMORY_SUMMARY_PROMPT_UPDATE.format(existingMemory, timeStr, truncatedNewText)

            // 使用原始模型名称（SiliconFlow要求）
            val modelName = apiConfig.model
            
            Log.d(TAG, "    ║ Model: $modelName")
            
            val request = ChatRequest(
                model = modelName,
                messages = listOf(MessageRequest("user", prompt)),
                temperature = apiConfig.temperature.coerceIn(0.0, 2.0),
                maxTokens = 2048,
                stream = false
            )

            val base = RetrofitClient.normalizeApiBaseUrl(apiConfig.baseUrl)
            val gson = Gson()
            val jsonMediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = gson.toJson(request)

            val httpRequest = Request.Builder()
                .url("${base}chat/completions")
                .post(requestBody.toRequestBody(jsonMediaType))
                .build()

            val client = RetrofitClient.createOkHttpClient(apiConfig.apiKey, logBodies = false)
            val response = client.newCall(httpRequest).execute()

            if (!response.isSuccessful) {
                Log.e(TAG, "API request failed: ${response.code}")
                return@withContext existingMemory
            }

            val responseBody = response.body?.string() ?: ""
            val parsed = gson.fromJson(responseBody, Map::class.java)
            val choices = parsed["choices"] as? List<*>
            val firstChoice = choices?.firstOrNull() as? Map<*, *>
            val message = firstChoice?.get("message") as? Map<*, *>
            val content = message?.get("content") as? String ?: ""

            response.close()
            if (content.isNotBlank()) content else existingMemory
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate updated summary", e)
            existingMemory
        }
    }

    suspend fun shouldGenerateToday(context: Context, characterId: Long): Boolean {
        val lastDate = getLastGenerateDate(context, characterId)
        val today = getDateString()
        return lastDate != today
    }

    /**
     * 聊天发消息路径：只读本地已保存记忆，不触发 API（避免发消息前同步等待整段摘要生成导致界面卡死）。
     */
    suspend fun getCachedMemoryForChat(context: Context, characterId: Long): String {
        return loadMemory(context, characterId).orEmpty()
    }

    /**
     * 后台刷新长期记忆（每日至多一次）。若已有同角色任务在执行则跳过。
     * @return 是否成功触发并完成一次生成
     */
    suspend fun refreshMemoryIfNeeded(
        context: Context,
        characterId: Long,
        apiConfig: com.example.chatbot.data.model.ApiConfig,
        allMessages: List<Message>,
        onGenerationStarted: (suspend () -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val trimmed = trimMessagesForSummary(allMessages)
        if (!shouldRefreshMemoryToday(context, characterId, trimmed.size)) {
            return@withContext false
        }

        val mutex = refreshMutexByCharacter.getOrPut(characterId) { Mutex() }
        if (!mutex.tryLock()) {
            Log.d(TAG, "Memory refresh already in progress for character $characterId")
            return@withContext false
        }
        try {
            if (!shouldRefreshMemoryToday(context, characterId, trimmed.size)) {
                return@withContext false
            }
            Log.d(TAG, "Background memory refresh started for character $characterId")
            onGenerationStarted?.invoke()
            val result = generateAndSaveMemory(context, characterId, trimmed, apiConfig)
            result.isSuccess
        } catch (e: Exception) {
            Log.e(TAG, "Background memory refresh failed for character $characterId", e)
            false
        } finally {
            mutex.unlock()
        }
    }

    private fun trimMessagesForSummary(allMessages: List<Message>): List<Message> {
        if (allMessages.size <= MAX_MESSAGES_FOR_SUMMARY) return allMessages
        return allMessages.sortedBy { it.timestamp }.takeLast(MAX_MESSAGES_FOR_SUMMARY)
    }

    private suspend fun shouldRefreshMemoryToday(
        context: Context,
        characterId: Long,
        messageCount: Int
    ): Boolean {
        if (messageCount < 5) return false
        val today = getDateString()
        val lastGenerateDate = getLastGenerateDate(context, characterId)
        return lastGenerateDate != today
    }

    /** @deprecated 请使用 [getCachedMemoryForChat] + [refreshMemoryIfNeeded] */
    @Suppress("UNUSED_PARAMETER")
    suspend fun getMemoryWithApi(
        context: Context,
        characterId: Long,
        apiConfig: com.example.chatbot.data.model.ApiConfig,
        allMessages: List<Message>
    ): String = getCachedMemoryForChat(context, characterId)

    fun getMemoryFilePath(context: Context, characterId: Long): String {
        return File(context.filesDir, "$MEMORY_DIR/$MEMORY_FILE_PREFIX$characterId$MEMORY_FILE_EXTENSION").absolutePath
    }

    suspend fun exportAllMemories(context: Context): Map<Long, String> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<Long, String>()
        try {
            val memoryDir = File(context.filesDir, MEMORY_DIR)
            if (memoryDir.exists()) {
                memoryDir.listFiles()?.forEach { file ->
                    if (file.name.startsWith(MEMORY_FILE_PREFIX) && file.name.endsWith(MEMORY_FILE_EXTENSION)) {
                        val characterId = file.name
                            .removePrefix(MEMORY_FILE_PREFIX)
                            .removeSuffix(MEMORY_FILE_EXTENSION)
                            .toLongOrNull()
                        if (characterId != null) {
                            val content = file.readText()
                            result[characterId] = content
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export memories", e)
        }
        result
    }

    suspend fun importMemory(context: Context, characterId: Long, content: String) {
        if (content.isNotBlank()) {
            try {
                val (metadata, actualContent) = parseMemoryMetadata(content)
                val timestamp = metadata?.lastMessageTimestamp ?: System.currentTimeMillis()
                saveMemory(context, characterId, actualContent, timestamp)
                setLastGenerateDate(context, characterId, metadata?.lastMessageDate ?: getDateString())
                setLastMessageTimestamp(context, characterId, timestamp)
                Log.d(TAG, "Imported memory for character $characterId successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to import memory for character $characterId", e)
            }
        }
    }

    suspend fun forceRegenerateMemory(
        context: Context,
        characterId: Long,
        messages: List<Message>,
        apiConfig: com.example.chatbot.data.model.ApiConfig
    ): Result<String> {
        clearLastState(context, characterId)
        return generateAndSaveMemory(context, characterId, messages, apiConfig)
    }

    internal suspend fun getMemoryMetadata(context: Context, characterId: Long): MemoryMetadata? {
        return withContext(Dispatchers.IO) {
            try {
                val file = getMemoryFile(context, characterId)
                if (file.exists()) {
                    val content = file.readText()
                    val (metadata, _) = parseMemoryMetadata(content)
                    metadata
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get memory metadata for character $characterId", e)
                null
            }
        }
    }
}
