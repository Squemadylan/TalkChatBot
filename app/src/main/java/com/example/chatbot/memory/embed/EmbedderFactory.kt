package com.example.chatbot.memory.embed

import android.content.Context
import android.util.Log
import com.example.chatbot.App
import com.example.chatbot.data.model.ApiConfig
import com.example.chatbot.data.model.effectiveEmbedApiKey
import com.example.chatbot.data.model.effectiveEmbedModel
import com.example.chatbot.memory.MemoryConfig
import kotlinx.coroutines.runBlocking

/**
 * 选 embedder 的工厂。
 *
 * 策略（自 2026-06 决定：只走远程，不再打包本地 ONNX 模型）：
 *   remote -> 远程 embedding API（OpenAI 兼容 /embeddings，复用用户 Chat 的 baseUrl+apiKey）
 *   auto   -> 同 remote（保留 key 字面以兼容旧 prefs；语义上等价）
 *   远程失败 / 用户未配 apiConfig -> 兜底 [Bm25PlaceholderEmbedder]
 */
object EmbedderFactory {

    @Volatile private var instance: LocalEmbedder? = null
    @Volatile private var lastChosenTag: String = "?"

    /** 当前生效的 embedder 标签：remote / bm25 */
    fun tag(): String = lastChosenTag

    fun get(context: Context): LocalEmbedder {
        instance?.let { return it }
        synchronized(this) {
            instance?.let { return it }
            val ctx = context.applicationContext
            val pick = buildOnce(ctx)
            instance = pick
            return pick
        }
    }

    fun rebuild(context: Context): LocalEmbedder {
        synchronized(this) {
            instance?.close()
            instance = buildOnce(context.applicationContext)
            return instance!!
        }
    }

    private fun buildOnce(context: Context): LocalEmbedder {
        val dim = MemoryConfig.embedDim(context)
        val apiConfig = loadApiConfigBlocking(context)
        val r = pickRemote(apiConfig, dim, context)
        if (r != null) {
            lastChosenTag = "remote"
            return r
        }
        lastChosenTag = "bm25"
        return Bm25PlaceholderEmbedder(dim)
    }

    private fun loadApiConfigBlocking(context: Context): ApiConfig? {
        return runCatching {
            val app = context.applicationContext as? App ?: return@runCatching null
            runBlocking { app.apiConfigRepository.getApiConfig() }
        }.getOrNull()
    }

    private fun pickRemote(
        apiConfig: ApiConfig?,
        dim: Int,
        context: Context
    ): RemoteEmbedder? {
        if (apiConfig == null || apiConfig.baseUrl.isBlank()) {
            Log.w(TAG, "remote embedder: no apiConfig yet, skip")
            return null
        }
        if (apiConfig.effectiveEmbedApiKey().isBlank()) {
            Log.w(TAG, "remote embedder: no embed api key yet, skip")
            return null
        }
        val model = apiConfig.effectiveEmbedModel()
        val appCtx = context.applicationContext
        return runCatching { RemoteEmbedder(apiConfig, model, dim, appCtx) }.getOrElse {
            Log.w(TAG, "RemoteEmbedder init failed", it)
            null
        }
    }

    private const val TAG = "EmbedderFactory"
}
