package com.example.chatbot.memory

/**
 * Persona 文件的解析/序列化。注释块内放元数据，正文写 Markdown。
 */
object PersonaMarkdown {

    fun toMarkdown(p: PersonaDoc): String = buildString {
        appendLine("<!--")
        appendLine("level: ${p.level.name}")
        appendLine("scope: ${if (p.characterId == null) "global" else "character"}")
        if (p.characterId != null) appendLine("characterId: ${p.characterId}")
        appendLine("updatedAt: ${p.updatedAt}")
        if (p.drillDown.isNotEmpty()) {
            appendLine("drillDown: ${p.drillDown.joinToString(",")}")
        }
        appendLine("-->")
        if (p.content.isNotBlank()) {
            appendLine()
            append(p.content.trim())
        }
    }

    fun fromMarkdown(text: String): PersonaDoc? {
        if (!text.startsWith("<!--")) {
            return PersonaDoc(
                level = PersonaDoc.Level.L3_GLOBAL,
                characterId = null,
                content = text,
                updatedAt = ""
            )
        }
        val end = text.indexOf("-->")
        if (end < 0) return null
        val meta = text.substring(4, end).trim()
        var level = PersonaDoc.Level.L3_GLOBAL
        var characterId: Long? = null
        var updatedAt = ""
        var drill = emptyList<String>()
        meta.split("\n").forEach { line ->
            val idx = line.indexOf(":")
            if (idx <= 0) return@forEach
            val k = line.substring(0, idx).trim()
            val v = line.substring(idx + 1).trim()
            when (k) {
                "level" -> level = runCatching { PersonaDoc.Level.valueOf(v) }.getOrDefault(PersonaDoc.Level.L3_GLOBAL)
                "characterId" -> characterId = v.toLongOrNull()
                "updatedAt" -> updatedAt = v
                "drillDown" -> if (v.isNotBlank()) drill = v.split(",")
            }
        }
        val body = text.substring(end + 3).trim()
        return PersonaDoc(level, characterId, body, updatedAt, drill)
    }
}
