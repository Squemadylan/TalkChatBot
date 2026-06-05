package com.example.chatbot.memory.layers

import android.content.Context
import com.example.chatbot.data.model.Message
import com.example.chatbot.memory.MemoryConfig
import com.example.chatbot.memory.MemoryPaths
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 短时 Mermaid 画布。
 *
 * 设计目标（对齐 TencentDB Agent Memory README）：
 *  - 把对话流编码成 Mermaid 节点 + 边，节省 token
 *  - 历史过厚时把整段对话外置到 refs/<date>.md，画布只保留 `node_id` 占位
 *  - 角色 Agent 后续能"回忆"某 node_id 时再 grep 取出原文
 */
object ShortTermCanvas {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val fullTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    data class CanvasState(
        var lastNodeId: Long = 0L,
        var lastMessageTimestamp: Long = 0L,
        var estimatedContextTokens: Int = 0,
        var nextNodeId: Long = 1L
    )

    /** 估算一整段对话历史大致多少 token（按每 1.5 个汉字 = 1 token 的粗略估算） */
    fun estimateTokens(text: String): Int = (text.length / 1.5f).toInt()

    /** 决定要把哪些消息外置到 refs，返回要外置的 messages。空列表表示不外置。 */
    fun decideOffload(
        context: Context,
        historyText: String,
        existingCanvas: String
    ): Float {
        val total = estimateTokens(historyText) + estimateTokens(existingCanvas)
        val cap = MemoryConfig.contextWindowTokens(context)
        return total.toFloat() / cap.toFloat()
    }

    fun appendTurn(
        context: Context,
        characterId: Long,
        msg: Message,
        refFile: String? = null,
        isOffloaded: Boolean = false
    ) {
        val state = loadState(context, characterId)
        val nodeId = "n${msg.id.coerceAtLeast(state.lastNodeId + 1L)}"
        state.lastNodeId = msg.id
        state.lastMessageTimestamp = msg.timestamp
        state.nextNodeId = (msg.id + 1L).coerceAtLeast(state.nextNodeId)

        val labelPrefix = timeFormat.format(Date(msg.timestamp))
        val text = msg.content.take(40).replace("\n", " ")
        val label = if (isOffloaded) {
            "[已外置 $labelPrefix]<br/>node_id=$nodeId"
        } else {
            "$labelPrefix ${if (msg.isUser) "用户" else "AI"}<br/>$text"
        }
        val role = if (msg.isUser) "U" else "A"
        val f = MemoryPaths.canvasFile(context, characterId)
        f.parentFile?.mkdirs()
        val append = buildString {
            if (!f.exists() || f.length() == 0L) {
                appendLine("```mermaid")
                appendLine("graph LR")
                appendLine("    START((\"对话起点\"))")
            } else {
                val cur = f.readText()
                if (!cur.trimEnd().endsWith("START((\"对话起点\"))")) {
                    // 文件不是新格式，不强求改写
                }
            }
            appendLine("    $nodeId([\"$label\"])")
            appendLine("    $nodeId:::msg_$role")
            if (refFile != null) {
                appendLine("    $nodeId -.-> R${msg.id}((\"refs/$refFile\"))")
            }
        }
        f.appendText(append, Charsets.UTF_8)
        saveState(context, characterId, state)
    }

    fun ensureTrailingMermaidClose(context: Context, characterId: Long) {
        val f = MemoryPaths.canvasFile(context, characterId)
        if (!f.exists()) return
        val text = f.readText()
        if (text.contains("```mermaid") && !text.trimEnd().endsWith("```")) {
            f.appendText("\n```\n", Charsets.UTF_8)
        }
    }

    fun loadCanvasText(context: Context, characterId: Long): String {
        val f = MemoryPaths.canvasFile(context, characterId)
        return if (f.exists()) f.readText() else ""
    }

    fun loadState(context: Context, characterId: Long): CanvasState {
        val f = MemoryPaths.canvasState(context, characterId)
        if (!f.exists()) return CanvasState()
        val o = runCatching { JSONObject(f.readText()) }.getOrNull() ?: return CanvasState()
        return CanvasState(
            lastNodeId = o.optLong("lastNodeId", 0L),
            lastMessageTimestamp = o.optLong("lastMessageTimestamp", 0L),
            estimatedContextTokens = o.optInt("estimatedContextTokens", 0),
            nextNodeId = o.optLong("nextNodeId", 1L)
        )
    }

    fun saveState(context: Context, characterId: Long, s: CanvasState) {
        val f = MemoryPaths.canvasState(context, characterId)
        f.parentFile?.mkdirs()
        val o = JSONObject()
        o.put("lastNodeId", s.lastNodeId)
        o.put("lastMessageTimestamp", s.lastMessageTimestamp)
        o.put("estimatedContextTokens", s.estimatedContextTokens)
        o.put("nextNodeId", s.nextNodeId)
        f.writeText(o.toString(), Charsets.UTF_8)
    }

    fun reset(context: Context, characterId: Long) {
        MemoryPaths.canvasFile(context, characterId).delete()
        MemoryPaths.canvasState(context, characterId).delete()
    }

    fun fullOffloadRefName(now: Long = System.currentTimeMillis()): String {
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(now))
        return "$day.md"
    }
}
