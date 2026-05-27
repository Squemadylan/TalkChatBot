package com.example.chatbot.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val characterId: Long,
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = STATUS_COMPLETED,
    val error: String = ""
) {
    fun isStreaming(): Boolean = status == STATUS_STREAMING
    fun isFailed(): Boolean = status == STATUS_FAILED

    companion object {
        const val STATUS_COMPLETED = "completed"
        const val STATUS_STREAMING = "streaming"
        const val STATUS_FAILED = "failed"
    }
}
