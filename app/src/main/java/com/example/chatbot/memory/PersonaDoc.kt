package com.example.chatbot.memory

/**
 * L3 Persona 文档。
 *
 * 对应 README 中 "L3 Persona lives in persona.md and traces back to the Scenarios that produced it"。
 */
data class PersonaDoc(
    val level: Level,
    val characterId: Long?,  // null 代表 global
    val content: String,
    val updatedAt: String,
    val drillDown: List<String> = emptyList()
) {
    enum class Level { L3_GLOBAL, L3_CHARACTER }
}
