package com.example.chatbot.data.model

import com.example.chatbot.util.EmbeddingModelRecommender

/** 记忆向量请求使用的 API Key：未单独填写时与对话 API Key 一致。 */
fun ApiConfig.effectiveEmbedApiKey(): String =
    embedApiKey.trim().ifBlank { apiKey.trim() }

/** 记忆向量模型：未填写时按 Base URL 自动推荐。 */
fun ApiConfig.effectiveEmbedModel(): String =
    embedModel.trim().ifBlank { EmbeddingModelRecommender.recommend(baseUrl) }

/** 用户是否单独保存了记忆向量 API Key（与对话 Key 不同）。 */
fun ApiConfig.isEmbedApiKeyOverridden(): Boolean = embedApiKey.isNotBlank()
