package com.example.chatbot.memory.embed

import android.content.Context
import android.util.Log
import com.example.chatbot.data.model.ApiConfig
import com.example.chatbot.data.model.effectiveEmbedApiKey
import com.example.chatbot.util.EmbeddingFailureNotifier
import com.example.chatbot.data.network.RetrofitClient
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 远程 embedding：直接复用用户 Chat 配置的 baseUrl + apiKey，POST `{baseUrl}embeddings`。
 *
 * 协议：OpenAI 兼容 / SiliconFlow / OpenRouter / 自建 OpenAI 协议都吃这一套。
 *   POST {baseUrl}embeddings
 *   {
 *     "input": ["text1", "text2"],
 *     "model": "BAAI/bge-large-zh-v1.5",
 *     "encoding_format": "float"
 *   }
 *   -> { "data": [{ "embedding": [..1024 floats..] }, ...] }
 *
 * 不依赖 streaming、suspend 在 IO 调度。失败抛 [RemoteEmbedder.Unavailable]，
 * [EmbedderFactory] 捕获后降级到下一档。
 */
class RemoteEmbedder(
    private val apiConfig: ApiConfig,
    private val model: String,
    private val dim: Int,
    private val appContext: Context
) : LocalEmbedder {

    @Volatile private var closed: Boolean = false
    @Volatile private var ok: Boolean = false
    private val gson = Gson()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    init {
        // 不在 init 里发探测（同步 IO 不允许）；让 isReady 默认 false，
        // 真正首次 encode() 成功后再翻成 true；这样 EmbedderFactory 的 "选型" 决策不会一上来就 HTTP。
        // —— 但 "Remote 优先" 意味着选型时并不知道它真能用，这里采用：
        // 工厂先快速创建，isReady() 直接返回 true（"声明可用"），由 encode 失败时再切档。
        ok = true
    }

    class Unavailable(msg: String, cause: Throwable? = null) : RuntimeException(msg, cause)

    override fun isReady(): Boolean = !closed && ok

    override fun dim(): Int = dim

    override fun encode(text: String): FloatArray {
        if (closed) error("embedder closed")
        if (text.isEmpty()) return FloatArray(dim)
        return runEncode(listOf(text)).first()
    }

    /**
     * 批量编码：让 L1 抽取 / 重算向量时一次发请求，省 N-1 次 RTT。
     * 不在 [LocalEmbedder] 接口里，但 [MemoryRetriever] 可以直接用。
     */
    suspend fun encodeBatch(texts: List<String>): List<FloatArray> = withContext(Dispatchers.IO) {
        if (texts.isEmpty()) return@withContext emptyList()
        if (closed) error("embedder closed")
        runEncode(texts)
    }

    private fun runEncode(inputs: List<String>): List<FloatArray> {
        val base = RetrofitClient.normalizeApiBaseUrl(apiConfig.baseUrl)
        val payload = mapOf(
            "input" to inputs,
            "model" to model,
            "encoding_format" to "float"
        )
        val http = Request.Builder()
            .url("${base}embeddings")
            .post(gson.toJson(payload).toRequestBody(jsonType))
            .build()
        val embedKey = apiConfig.effectiveEmbedApiKey()
        if (embedKey.isBlank()) {
            throw Unavailable("embed api key empty")
        }
        val client = RetrofitClient.createOkHttpClient(embedKey, logBodies = false)
        client.newCall(http).execute().use { resp ->
            if (!resp.isSuccessful) {
                val body = resp.body?.string().orEmpty().take(200)
                Log.w(TAG, "embeddings HTTP ${resp.code}: $body")
                EmbeddingFailureNotifier.notifyOnce(appContext)
                throw Unavailable("embeddings HTTP ${resp.code}")
            }
            val body = resp.body?.string() ?: throw Unavailable("empty body")
            val parsed = gson.fromJson(body, Map::class.java) ?: throw Unavailable("null json")
            val data = parsed["data"] as? List<*>
                ?: throw Unavailable("missing 'data' field")
            val out = ArrayList<FloatArray>(data.size)
            for (e in data) {
                val m = e as? Map<*, *> ?: continue
                val arr = m["embedding"] as? List<*>
                    ?: throw Unavailable("missing 'embedding' array")
                val vec = FloatArray(arr.size) { i ->
                    (arr[i] as? Number)?.toFloat() ?: 0f
                }
                if (vec.size != dim) {
                    // 维度不匹配：截断或 0 填充
                    if (vec.size > dim) {
                        FloatArray(dim) { vec[it] }
                    } else {
                        val padded = FloatArray(dim)
                        System.arraycopy(vec, 0, padded, 0, vec.size)
                        padded
                    }
                } else {
                    vec
                }
            }
            ok = true
            return out
        }
    }

    override fun close() {
        closed = true
        ok = false
    }

    companion object {
        private const val TAG = "RemoteEmbedder"
    }
}
