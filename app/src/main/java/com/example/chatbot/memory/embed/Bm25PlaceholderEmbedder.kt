package com.example.chatbot.memory.embed

import java.security.MessageDigest

/**
 * 兜底实现：把文本按 token 切分后做 hashed bag-of-words 投影。
 *
 * 这不是真正的语义向量，但能保证：
 *  1. 同一文本向量稳定
 *  2. 维度可控（默认 256）
 *  3. 模型缺失 / 加载失败时不抛异常
 *
 * 当本地 ONNX 模型就绪后，embedder 会切到 [OnnxEmbedder]，本类仅作为 no-op 后备。
 */
class Bm25PlaceholderEmbedder(private val dim: Int = 256) : LocalEmbedder {

    private val closed = java.util.concurrent.atomic.AtomicBoolean(false)

    override fun isReady(): Boolean = !closed.get()

    override fun dim(): Int = dim

    override fun encode(text: String): FloatArray {
        if (closed.get()) error("embedder closed")
        val v = FloatArray(dim)
        val md = MessageDigest.getInstance("SHA-256")
        for (token in tokenize(text)) {
            val bytes = md.digest(token.toByteArray(Charsets.UTF_8))
            val idx = ((bytes[0].toInt() and 0xFF) shl 1 or (bytes[1].toInt() and 0xFF)) % dim
            v[idx] += 1f
        }
        // L2 归一化，方便 cosine
        var sum = 0f
        for (x in v) sum += x * x
        if (sum > 0f) {
            val norm = kotlin.math.sqrt(sum)
            for (i in v.indices) v[i] /= norm
        }
        return v
    }

    override fun close() { closed.set(true) }

    private fun tokenize(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        // 极简中英混合分词：英文按空格、汉字按字。
        val out = ArrayList<String>(text.length)
        val sb = StringBuilder()
        fun flush() {
            if (sb.isNotEmpty()) {
                out.add(sb.toString().lowercase())
                sb.clear()
            }
        }
        for (c in text) {
            when {
                c.isWhitespace() -> flush()
                c.code in 0x4E00..0x9FFF -> { flush(); out.add(c.toString()) }
                c.isLetterOrDigit() -> sb.append(c)
                else -> flush()
            }
        }
        flush()
        return out
    }
}
