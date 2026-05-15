package com.example.chatbot.util

/**
 * 根据模型 id（硅基流动 / OpenAI 兼容等常见命名）给出「单次回复」的推荐 max_tokens 上限。
 * 实际以各厂商文档为准；此处为保守默认值，避免过小截断或过大被网关拒绝。
 */
object ModelDefaultTokens {

    /** 配置页校验与保存时允许的上限（多数 OpenAI 兼容网关可接受） */
    const val MAX_OUTPUT_TOKENS_CAP = 131_072

    fun recommendedMaxOutputTokens(model: String): Int {
        val m = model.trim().lowercase()
        if (m.isEmpty()) return FALLBACK_DEFAULT
        return when {
            m.contains("deepseek-r1") || m.contains("deepseek_r1") || m.contains("deepseek-v3") ||
                m.contains("deepseek_v3") -> 8192
            m.contains("gpt-4o") || m.contains("gpt-4-turbo") || m.contains("gpt-4.1") -> 16384
            m.contains("gpt-4") -> 8192
            m.contains("gpt-3.5") || m.contains("gpt-35") -> 4096
            m.contains("kimi") || m.contains("moonshot") -> 8192
            m.contains("qwen3") || m.contains("qwen2.5") || m.contains("qwen2-") -> 8192
            m.contains("qwen") -> 4096
            m.contains("glm-4.5") || m.contains("glm-4") || m.contains("glm4") -> 8192
            m.contains("glm") -> 4096
            m.contains("72b") || m.contains("70b") || m.contains("65b") || m.contains("34b") ||
                m.contains("32b") -> 8192
            m.contains("14b") || m.contains("13b") || m.contains("9b") || m.contains("8b") ||
                m.contains("7b") || m.contains("nano") || m.contains("small") -> 4096
            m.contains("embedding") || m.contains("bge-") || m.contains("bce-") -> 1024
            else -> FALLBACK_DEFAULT
        }.coerceIn(1, MAX_OUTPUT_TOKENS_CAP)
    }

    private const val FALLBACK_DEFAULT = 4096
}
