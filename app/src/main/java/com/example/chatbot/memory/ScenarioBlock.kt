package com.example.chatbot.memory

/**
 * L2 场景块元数据。
 *
 * 文件本身是 Markdown，正文头部用 HTML 注释存放这些元数据。
 * 这种"白盒文件 + 显式 metadata"的做法与 TencentDB Agent Memory README 中
 * "Scenario blocks are plain Markdown — open them and inspect" 保持一致。
 */
data class ScenarioBlock(
    val id: String,
    val characterId: Long,
    val title: String,
    val summary: String,
    val createdAt: String,
    val updatedAt: String,
    val atomCount: Int,
    val atomIds: List<String> = emptyList(),
    val relatedMessageRefs: List<String> = emptyList()
)
