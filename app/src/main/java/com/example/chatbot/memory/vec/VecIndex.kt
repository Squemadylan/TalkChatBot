package com.example.chatbot.memory.vec

/**
 * 抽象向量索引。生产实现用 sqlite-vec 0.x 提供的 `vec0` 虚拟表。
 * 不可用时回退到线性扫描，接口保持一致。
 */
interface VecIndex {

    val dim: Int

    fun isReady(): Boolean

    /** 添加或更新一条向量；id 形如 "atom_xxx" */
    fun upsert(id: String, vector: FloatArray)

    /** 按余弦相似度返回 top-k；空集合时返回 emptyList */
    fun search(query: FloatArray, topK: Int): List<Match>

    fun close()

    data class Match(val id: String, val score: Float)
}
