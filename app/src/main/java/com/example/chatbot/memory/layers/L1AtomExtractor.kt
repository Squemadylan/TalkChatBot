package com.example.chatbot.memory.layers

import android.content.Context
import android.util.Log
import com.example.chatbot.data.model.ApiConfig
import com.example.chatbot.data.model.Message
import com.example.chatbot.memory.Atom
import com.example.chatbot.memory.AtomJsonl
import com.example.chatbot.memory.AtomSubject
import com.example.chatbot.memory.AtomType
import com.example.chatbot.memory.LlmCall
import com.example.chatbot.memory.MemoryConfig
import com.example.chatbot.memory.MemoryPaths
import com.example.chatbot.memory.embed.EmbedderFactory
import com.example.chatbot.memory.vec.VecIndexFactory
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/**
 * L1 原子事实抽取。
 *
 * 输入：自上次抽取以来新增的对话。
 * 输出：JSONL 行，每行一个 atom。
 * 副作用：写 atoms.jsonl + sqlite-vec/linear scan 索引。
 */
object L1AtomExtractor {

    private const val TAG = "L1AtomExtractor"

    private data class AtomsMeta(
        var lastExtractedMessageId: Long = 0L,
        var totalAtoms: Int = 0
    )

    private val metaCache = AtomicReference<MutableMap<Long, AtomsMeta>>(HashMap())

    /** 是否应当触发一次抽取：自上次以来新增消息数 ≥ l1MinMessages */
    fun shouldExtract(context: Context, characterId: Long, newMessageCount: Int): Boolean {
        if (!MemoryConfig.isEnabled(context)) return false
        if (newMessageCount < MemoryConfig.l1MinMessages(context)) return false
        return true
    }

    /**
     * 同步执行一次抽取。会写文件、调用 LLM、更新本地缓存。
     * 失败时静默返回空列表，由调用方决定是否重试。
     */
    suspend fun extract(
        context: Context,
        characterId: Long,
        apiConfig: ApiConfig,
        newMessages: List<Message>
    ): List<Atom> {
        if (newMessages.isEmpty()) return emptyList()
        if (!MemoryConfig.isEnabled(context)) return emptyList()

        val lastId = newMessages.maxOf { it.id }
        val systemPrompt = """
            你是 L1 原子事实抽取器。从一段对话中抽取"原子事实"，每条一个独立信息。
            输出必须是严格 JSON 数组，不要任何解释、不要 Markdown 代码块。
            schema: [{"type":"fact|preference|event|emotion","subject":"user|character|third_party|unknown","text":"..."}]
        """.trimIndent()
        val userPrompt = buildString {
            appendLine("角色 ID: $characterId")
            appendLine("请抽取以下对话中的原子事实：")
            appendLine()
            for (m in newMessages) {
                val role = if (m.isUser) "user" else "assistant"
                append("[$role] ").appendLine(m.content.take(400))
            }
        }

        val raw = LlmCall.callJsonText(
            apiConfig = apiConfig,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            maxTokens = 1200,
            temperature = 0.2
        )
        if (raw.isBlank()) return emptyList()

        val parsed = runCatching { JSONArray(stripCodeFence(raw)) }.getOrNull() ?: return emptyList()
        val atoms = ArrayList<Atom>(parsed.length())
        val embedder = EmbedderFactory.get(context)
        val expectedDim = embedder.dim()
        var vec = VecIndexFactory.get(context, characterId)
        if (vec.dim != expectedDim) {
            // dim 不一致（例如刚切远程 1024d，旧 atoms_index.sqlite 还是 512d）：重建
            VecIndexFactory.reset(characterId)
            vec = VecIndexFactory.get(context, characterId)
        }
        val jsonl = MemoryPaths.atomsJsonl(context, characterId)

        for (i in 0 until parsed.length()) {
            val o = parsed.optJSONObject(i) ?: continue
            val text = o.optString("text", "").trim()
            if (text.isEmpty()) continue
            val atom = Atom(
                id = "atom_${UUID.randomUUID()}",
                characterId = characterId,
                ts = System.currentTimeMillis(),
                type = AtomType.fromKeyword(o.optString("type", "")),
                subject = AtomSubject.fromKeyword(o.optString("subject", "")),
                text = text,
                sourceMessageIds = newMessages.map { it.id }
            )
            runCatching {
                AtomJsonl.append(jsonl, atom)
                val v = embedder.encode(atom.text)
                vec.upsert(atom.id, v)
            }.onFailure { Log.w(TAG, "atom persist failed", it) }
            atoms.add(atom)
        }

        // 更新 meta
        val meta = metaCache.get().toMutableMap()
        val cur = meta[characterId] ?: AtomsMeta()
        cur.lastExtractedMessageId = maxOf(cur.lastExtractedMessageId, lastId)
        cur.totalAtoms += atoms.size
        meta[characterId] = cur
        metaCache.set(meta)
        persistMeta(context, characterId, cur)
        return atoms
    }

    fun lastExtractedMessageId(context: Context, characterId: Long): Long {
        metaCache.get()[characterId]?.let { return it.lastExtractedMessageId }
        val file = MemoryPaths.atomsMeta(context, characterId)
        if (!file.exists()) return 0L
        val o = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return 0L
        val cur = AtomsMeta(
            lastExtractedMessageId = o.optLong("lastExtractedMessageId", 0L),
            totalAtoms = o.optInt("totalAtoms", 0)
        )
        val map = metaCache.get().toMutableMap()
        map[characterId] = cur
        metaCache.set(map)
        return cur.lastExtractedMessageId
    }

    fun totalAtoms(characterId: Long): Int =
        metaCache.get()[characterId]?.totalAtoms ?: 0

    fun reset(characterId: Long) {
        val m = metaCache.get().toMutableMap()
        m.remove(characterId)
        metaCache.set(m)
    }

    private fun persistMeta(context: Context, characterId: Long, m: AtomsMeta) {
        runCatching {
            val o = JSONObject()
            o.put("lastExtractedMessageId", m.lastExtractedMessageId)
            o.put("totalAtoms", m.totalAtoms)
            val f = MemoryPaths.atomsMeta(context, characterId)
            f.parentFile?.mkdirs()
            f.writeText(o.toString(), Charsets.UTF_8)
        }
    }

    private fun stripCodeFence(s: String): String {
        var t = s.trim()
        if (t.startsWith("```")) {
            t = t.removePrefix("```").trimStart()
            // 去掉语言标记
            val nl = t.indexOf('\n')
            if (nl in 0..20) t = t.substring(nl + 1)
            if (t.endsWith("```")) t = t.removeSuffix("```")
        }
        return t.trim()
    }
}
