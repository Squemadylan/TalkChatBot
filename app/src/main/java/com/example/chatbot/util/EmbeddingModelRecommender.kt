package com.example.chatbot.util

/**
 * 根据 Chat API 的 Base URL 推荐记忆向量（embedding）模型名。
 */
object EmbeddingModelRecommender {

    const val SILICONFLOW_DEFAULT = "BAAI/bge-large-zh-v1.5"
    const val OPENAI_DEFAULT = "text-embedding-3-small"

    fun recommend(baseUrl: String): String {
        val host = baseUrl.trim().lowercase()
        return when {
            host.contains("api.openai.com") || host.contains("openai.com/v1") -> OPENAI_DEFAULT
            host.contains("siliconflow") -> SILICONFLOW_DEFAULT
            else -> SILICONFLOW_DEFAULT
        }
    }
}
