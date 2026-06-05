package com.example.chatbot.memory.vec

import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * 兜底实现：纯内存线性扫描，线程安全。
 *
 * 当 sqlite-vec AAR 不可用时用这个，能力足够单角色数千 atom 体量。
 * 真实部署在第三方 AAR 接入后，替换为 [SqliteVecIndex]。
 */
class LinearScanIndex(override val dim: Int) : VecIndex {

    private val store = HashMap<String, FloatArray>()
    private val lock = ReentrantReadWriteLock()

    override fun isReady(): Boolean = true

    override fun upsert(id: String, vector: FloatArray) {
        require(vector.size == dim) { "dim mismatch: expected $dim got ${vector.size}" }
        lock.write { store[id] = vector.copyOf() }
    }

    override fun search(query: FloatArray, topK: Int): List<VecIndex.Match> {
        if (topK <= 0) return emptyList()
        require(query.size == dim)
        val q = normalize(query)
        return lock.read {
            store.entries
                .map { (id, v) -> VecIndex.Match(id, cosine(q, normalize(v))) }
                .sortedByDescending { it.score }
                .take(topK)
        }
    }

    override fun close() { lock.write { store.clear() } }

    private fun normalize(v: FloatArray): FloatArray {
        var sum = 0f
        for (x in v) sum += x * x
        val out = if (sum == 0f) v.copyOf() else {
            val n = kotlin.math.sqrt(sum)
            FloatArray(v.size) { v[it] / n }
        }
        return out
    }

    private fun cosine(a: FloatArray, b: FloatArray): Float {
        var s = 0f
        for (i in a.indices) s += a[i] * b[i]
        return s
    }
}
