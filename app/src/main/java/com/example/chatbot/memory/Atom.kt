package com.example.chatbot.memory

/**
 * L1 原子事实。是 recall / L2 聚类的基本单元。
 *
 * 持久化：JSONL，每行一条；向量单独落到 sqlite-vec 中。
 * 兼容旧字段：subject / type / scenarioId 可能为空。
 */
data class Atom(
    val id: String,
    val characterId: Long,
    val scenarioId: String? = null,
    val ts: Long = System.currentTimeMillis(),
    val type: AtomType = AtomType.FACT,
    val subject: AtomSubject = AtomSubject.UNKNOWN,
    val text: String,
    val sourceMessageIds: List<Long> = emptyList(),
    val ref: String? = null
)
