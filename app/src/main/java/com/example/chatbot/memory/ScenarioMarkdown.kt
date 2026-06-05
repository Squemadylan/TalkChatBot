package com.example.chatbot.memory

/**
 * 把 ScenarioBlock 与 Markdown 互转。注释块里写元数据，Markdown 正文写摘要 + 关联引用。
 */
object ScenarioMarkdown {

    fun toMarkdown(s: ScenarioBlock, body: String): String = buildString {
        appendLine("<!--")
        appendLine("id: ${s.id}")
        appendLine("characterId: ${s.characterId}")
        appendLine("title: ${s.title}")
        appendLine("createdAt: ${s.createdAt}")
        appendLine("updatedAt: ${s.updatedAt}")
        appendLine("atomCount: ${s.atomCount}")
        if (s.atomIds.isNotEmpty()) {
            appendLine("atomIds: ${s.atomIds.joinToString(",")}")
        }
        if (s.relatedMessageRefs.isNotEmpty()) {
            appendLine("relatedMessageRefs: ${s.relatedMessageRefs.joinToString(",")}")
        }
        appendLine("-->")
        if (body.isNotBlank()) {
            appendLine()
            append(body.trim())
        }
    }

    fun fromMarkdown(text: String): ScenarioBlock? {
        if (!text.startsWith("<!--")) return null
        val end = text.indexOf("-->")
        if (end < 0) return null
        val meta = text.substring(4, end).trim()
        var id = ""
        var characterId = 0L
        var title = ""
        var createdAt = ""
        var updatedAt = ""
        var atomCount = 0
        var atomIds = emptyList<String>()
        var relatedMessageRefs = emptyList<String>()
        meta.split("\n").forEach { line ->
            val idx = line.indexOf(":")
            if (idx <= 0) return@forEach
            val k = line.substring(0, idx).trim()
            val v = line.substring(idx + 1).trim()
            when (k) {
                "id" -> id = v
                "characterId" -> characterId = v.toLongOrNull() ?: 0L
                "title" -> title = v
                "createdAt" -> createdAt = v
                "updatedAt" -> updatedAt = v
                "atomCount" -> atomCount = v.toIntOrNull() ?: 0
                "atomIds" -> if (v.isNotBlank()) atomIds = v.split(",")
                "relatedMessageRefs" -> if (v.isNotBlank()) relatedMessageRefs = v.split(",")
            }
        }
        if (id.isBlank()) return null
        val body = text.substring(end + 3).trim()
        // 第一行可能是 "## 摘要" / "## ..." 这种小标题；这里不强行剥离，summary 由 LLM 直接写。
        return ScenarioBlock(
            id = id,
            characterId = characterId,
            title = title,
            summary = body,
            createdAt = createdAt,
            updatedAt = updatedAt,
            atomCount = atomCount,
            atomIds = atomIds,
            relatedMessageRefs = relatedMessageRefs
        )
    }
}
