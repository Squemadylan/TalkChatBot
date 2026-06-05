package com.example.chatbot.util

import android.content.Context
import android.util.Log
import com.example.chatbot.data.model.ApiConfig
import com.example.chatbot.data.model.Message
import com.example.chatbot.memory.MemoryConfig
import com.example.chatbot.memory.MemoryPipeline
import com.example.chatbot.memory.layers.L1AtomExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex

/**
 * 旧 LongTermMemoryManager 的兼容层。
 *
 * 自 v2.0 起，4 层记忆由 `com.example.chatbot.memory.MemoryPipeline` 编排。
 * 这里保留旧 API（`getCachedMemoryForChat`、`saveLongTermMemoryNow`、`forceRegenerateMemory`、
 * `deleteMemory` 等），内部全部委托到新实现，确保 UI 层的调用不会因为
 * 重构而失联。
 */
object LongTermMemoryManager {
    private const val TAG = "LongTermMemoryManager"

    @Deprecated("Use MemoryPipeline.buildContextSystemMessage")
    suspend fun getCachedMemoryForChat(context: Context, characterId: Long): String {
        return MemoryPipeline.buildContextSystemMessage(context, characterId, query = "近期对话")
            .orEmpty()
    }

    @Deprecated("Use MemoryPipeline.onTurnComplete / ChatViewModel 集成")
    suspend fun getMemoryWithApi(
        context: Context,
        characterId: Long,
        apiConfig: com.example.chatbot.data.model.ApiConfig,
        allMessages: List<Message>
    ): String = getCachedMemoryForChat(context, characterId)

    @Deprecated("Manual regenerate is no longer required; pipeline runs after each turn.")
    suspend fun forceRegenerateMemory(
        context: Context,
        characterId: Long,
        messages: List<Message>,
        apiConfig: com.example.chatbot.data.model.ApiConfig
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val since = L1AtomExtractor.lastExtractedMessageId(context, characterId)
            val newOnes = messages.filter { it.id > since }
            if (newOnes.isNotEmpty()) {
                L1AtomExtractor.extract(context, characterId, apiConfig, newOnes)
            }
            MemoryPipeline.buildContextSystemMessage(context, characterId, "近期对话").orEmpty()
        }
    }

    @Deprecated("UI 层调用，手动触发后端 L1/L2/L3。")
    suspend fun refreshMemoryIfNeeded(
        context: Context,
        characterId: Long,
        apiConfig: ApiConfig,
        allMessages: List<Message>,
        onGenerationStarted: (suspend () -> Unit)? = null
    ): Boolean {
        if (!MemoryConfig.isEnabled(context)) return false
        onGenerationStarted?.invoke()
        val since = L1AtomExtractor.lastExtractedMessageId(context, characterId)
        val newOnes = allMessages.filter { it.id > since }
        if (newOnes.isEmpty()) return false
        return runCatching {
            L1AtomExtractor.extract(context, characterId, apiConfig, newOnes)
            true
        }.getOrDefault(false)
    }

    suspend fun deleteMemory(context: Context, characterId: Long) {
        withContext(Dispatchers.IO) {
            try {
                MemoryPipeline.resetCharacter(context, characterId)
                Log.d(TAG, "reset memory for character $characterId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reset memory", e)
            }
        }
    }

    suspend fun deleteAllMemories(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                MemoryPipeline.resetAll(context)
                Log.d(TAG, "reset all memory")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reset all memory", e)
            }
        }
    }

    fun getMemoryFilePath(context: Context, characterId: Long): String {
        // 旧路径已被新结构替代；备份兼容仍按此路径读取旧 memory_<id>.md
        val legacy = java.io.File(context.filesDir, "long_term_memory/memory_$characterId.md")
        if (legacy.exists()) return legacy.absolutePath
        return com.example.chatbot.memory.MemoryPaths.characterPersona(context, characterId).absolutePath
    }

    suspend fun exportAllMemories(context: Context): Map<Long, String> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<Long, String>()
        val root = com.example.chatbot.memory.MemoryPaths.root(context)
        val chars = root.listFiles { f -> f.isDirectory && f.name.toLongOrNull() != null }
            ?: return@withContext result
        chars.forEach { dir ->
            val cid = dir.name.toLongOrNull() ?: return@forEach
            val persona = com.example.chatbot.memory.MemoryPaths.characterPersona(context, cid)
            if (persona.exists()) {
                result[cid] = persona.readText()
            }
        }
        result
    }

    suspend fun importMemory(context: Context, characterId: Long, content: String) {
        if (content.isBlank()) return
        val target = com.example.chatbot.memory.MemoryPaths.characterPersona(context, characterId)
        target.parentFile?.mkdirs()
        target.writeText(content, Charsets.UTF_8)
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun shouldGenerateToday(context: Context, characterId: Long): Boolean = true

    // 旧 API 中一些不再需要的常量/方法保留 stub，避免上层引用断链
    private val refreshMutexByCharacter = ConcurrentHashMap<Long, Mutex>()
    private const val MEMORY_DIR = "long_term_memory"
    private const val MEMORY_FILE_PREFIX = "memory_"
    private const val MEMORY_FILE_EXTENSION = ".md"
    private const val PREFS_NAME = "long_term_memory_prefs"
    private const val KEY_LAST_GENERATE_DATE = "last_generate_date_"
    private const val KEY_LAST_MESSAGE_TIMESTAMP = "last_message_timestamp_"
}
