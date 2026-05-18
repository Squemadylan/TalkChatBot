package com.example.chatbot.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "api_config")
data class ApiConfig(
    @PrimaryKey val id: Int = 1,
    val baseUrl: String = "https://api.siliconflow.cn/v1",
    val apiKey: String = "",
    val model: String = "deepseek-ai/DeepSeek-V3.2",
    val temperature: Double = 0.7,
    val maxTokens: Int = 8192
)
