package com.example.chatbot.data.network

import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.Response
import okio.BufferedSource

/**
 * 解析 OpenAI 兼容的聊天接口响应：SSE 流式（如硅基流动 SiliconFlow）或单次 JSON。
 * 文档入口：https://docs.siliconflow.cn/cn/userguide/introduction
 */
object OpenAiChatResponseReader {

    /**
     * 消费 [response] 的 body（成功或失败均会关闭 body）。
     * 流式时通过 [onTextDelta] 增量回调 UTF-8 文本片段；返回拼接后的全文。
     */
    suspend fun consume(
        response: Response,
        gson: Gson,
        onTextDelta: suspend (String) -> Unit
    ): Result<String> {
        val body = response.body ?: return Result.failure(IllegalStateException("响应体为空"))
        try {
            if (!response.isSuccessful) {
                val err = runCatching { body.string() }.getOrNull().orEmpty()
                return Result.failure(
                    IllegalStateException(err.ifBlank { "HTTP ${response.code}" })
                )
            }
            val contentType = body.contentType()?.toString()?.lowercase() ?: ""
            return if (contentType.contains("event-stream")) {
                readSse(body.source(), gson, onTextDelta)
            } else {
                readSingleJson(body.string(), gson, onTextDelta)
            }
        } finally {
            body.close()
        }
    }

    private suspend fun readSse(
        source: BufferedSource,
        gson: Gson,
        onTextDelta: suspend (String) -> Unit
    ): Result<String> {
        val full = StringBuilder()
        return try {
            while (true) {
                val line = source.readUtf8Line() ?: break
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue
                if (trimmed == "data: [DONE]" || trimmed == "[DONE]") break
                if (!trimmed.startsWith("data:")) continue
                val json = trimmed.removePrefix("data:").trim()
                if (json == "[DONE]") break
                val piece = extractDeltaContent(json, gson) ?: continue
                if (piece.isNotEmpty()) {
                    full.append(piece)
                    onTextDelta(piece)
                }
            }
            Result.success(full.toString())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractDeltaContent(jsonLine: String, gson: Gson): String? {
        return try {
            val obj = gson.fromJson(jsonLine, JsonObject::class.java) ?: return null
            val choices = obj.getAsJsonArray("choices") ?: return null
            if (choices.size() == 0) return null
            val c0 = choices[0].asJsonObject
            val delta = c0.getAsJsonObject("delta") ?: return null
            if (!delta.has("content") || delta.get("content").isJsonNull) return null
            delta.get("content").asString
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun readSingleJson(
        full: String,
        gson: Gson,
        onTextDelta: suspend (String) -> Unit
    ): Result<String> {
        return try {
            val parsed = gson.fromJson(full, ChatResponse::class.java)
            val text = parsed.choices.firstOrNull()?.message?.content.orEmpty()
            if (text.isNotEmpty()) {
                onTextDelta(text)
            }
            Result.success(text)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
