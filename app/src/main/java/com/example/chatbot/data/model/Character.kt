package com.example.chatbot.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "characters")
data class Character(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val avatar: String,
    val description: String,
    val prompt: String,
    val tags: String,
    val openingGreeting: String = "",
    val enableLongTermMemory: Boolean = false,
    val enableAutoRead: Boolean = false,
    val voiceSpeed: Float = 1.0f,
    val voiceLanguage: String = "zh-CN",
    val createdAt: Long = System.currentTimeMillis()
) {
    fun systemPromptForChat(): String = buildString {
        if (description.isNotBlank()) {
            appendLine("【角色描述】")
            appendLine(description.trim())
            appendLine()
        }
        if (openingGreeting.isNotBlank()) {
            appendLine("【主开场白】")
            appendLine(openingGreeting.trim())
            appendLine()
        }
        if (tags.isNotBlank()) {
            appendLine("【标签】")
            appendLine(tags.trim())
            appendLine()
        }
        if (prompt.isNotBlank()) {
            appendLine("【补充人设】")
            appendLine(prompt.trim())
        }
    }.trim()
}
