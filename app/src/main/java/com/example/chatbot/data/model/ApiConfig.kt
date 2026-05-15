package com.example.chatbot.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "api_config")
data class ApiConfig(
    @PrimaryKey val id: Int = 1,
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "gpt-3.5-turbo",
    val temperature: Double = 0.7,
    val maxTokens: Int = 4096
)
