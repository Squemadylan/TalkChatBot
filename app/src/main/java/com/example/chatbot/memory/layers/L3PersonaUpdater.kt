package com.example.chatbot.memory.layers

import android.content.Context
import com.example.chatbot.data.model.ApiConfig
import com.example.chatbot.memory.LlmCall
import com.example.chatbot.memory.MemoryConfig
import com.example.chatbot.memory.MemoryPaths
import com.example.chatbot.memory.PersonaDoc
import com.example.chatbot.memory.PersonaMarkdown
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * L3 Persona 更新器。
 *
 * 两个层次：
 *  - 跨角色 global persona：所有角色 L2 摘要的横向归纳
 *  - 角色级 persona：单角色 L2 摘要的归纳
 *
 * 每次 L2 跑完后调用一次。LLM 输入 L2 增量、输出 delta，落到 persona.md 末尾的
 * "Recent deltas" 节；同日多次更新合并为一条。
 */
object L3PersonaUpdater {

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    /** 是否应触发 global 更新：累计自上次以来所有角色 atoms 增量和 ≥ 阈值 */
    fun shouldUpdateGlobal(globalDeltaAtoms: Int): Boolean =
        globalDeltaAtoms >= 0  // 实际阈值在 Pipeline 处传入

    suspend fun updateGlobal(
        context: Context,
        apiConfig: ApiConfig,
        deltas: List<CharacterScenarioDelta>
    ): Boolean {
        if (deltas.isEmpty()) return false
        if (!MemoryConfig.isEnabled(context)) return false
        val cur = loadGlobal(context)
        val systemPrompt = """
            你是 L3 Persona 综合器。把多个角色的场景摘要归纳为"用户跨角色画像"。
            关注：称呼偏好、沟通风格、长期目标、敏感话题、反复出现的主题、价值取向。
            输出严格 JSON：{"additions":["..."],"drillDown":["scn_..."]}
        """.trimIndent()
        val userPrompt = buildString {
            appendLine("现有 global persona：")
            appendLine(cur.content.ifBlank { "（空）" })
            appendLine()
            appendLine("新增场景摘要：")
            deltas.forEach { d ->
                appendLine("- character=${d.characterId} :: ${d.title} :: ${d.summary.take(300)}")
            }
        }
        val raw = LlmCall.callJsonText(apiConfig, systemPrompt, userPrompt, maxTokens = 800, temperature = 0.3)
        if (raw.isBlank()) return false
        val o = runCatching { JSONObject(stripCodeFence(raw)) }.getOrNull() ?: return false
        val additions = o.optJSONArray("additions")?.let { a -> (0 until a.length()).mapNotNull { a.optString(it, "").ifBlank { null } } }.orEmpty()
        val drill = o.optJSONArray("drillDown")?.let { a -> (0 until a.length()).mapNotNull { a.optString(it, "").ifBlank { null } } }.orEmpty()
        if (additions.isEmpty()) return false

        val newContent = if (cur.content.isBlank()) {
            "# Persona (跨角色)\n\n" + additions.joinToString("\n") { "- $it" }
        } else {
            val today = dayFormat.format(Date())
            val merged = buildString {
                append(cur.content.trim())
                appendLine()
                appendLine()
                appendLine("<!-- delta $today ${timeFormat.format(Date())} -->")
                additions.forEach { appendLine("- $it") }
            }
            merged
        }
        val updated = cur.copy(
            content = newContent,
            updatedAt = timeFormat.format(Date()),
            drillDown = (cur.drillDown + drill).distinct()
        )
        saveGlobal(context, updated)
        return true
    }

    suspend fun updateForCharacter(
        context: Context,
        apiConfig: ApiConfig,
        characterId: Long,
        scenarioDeltas: List<CharacterScenarioDelta>
    ): Boolean {
        if (scenarioDeltas.isEmpty()) return false
        if (!MemoryConfig.isEnabled(context)) return false
        val cur = loadForCharacter(context, characterId)
        val systemPrompt = """
            你是 L3 角色级 Persona 综合器。从该角色近期场景摘要中归纳"用户在该角色下的画像"。
            关注：与该角色的关系定位、互动模式、常用话题、共同记忆。
            输出严格 JSON：{"additions":["..."],"drillDown":["scn_..."]}
        """.trimIndent()
        val userPrompt = buildString {
            appendLine("现有角色 persona：")
            appendLine(cur.content.ifBlank { "（空）" })
            appendLine()
            appendLine("新增场景摘要：")
            scenarioDeltas.forEach { d ->
                appendLine("- ${d.title} :: ${d.summary.take(300)}")
            }
        }
        val raw = LlmCall.callJsonText(apiConfig, systemPrompt, userPrompt, maxTokens = 800, temperature = 0.3)
        if (raw.isBlank()) return false
        val o = runCatching { JSONObject(stripCodeFence(raw)) }.getOrNull() ?: return false
        val additions = o.optJSONArray("additions")?.let { a -> (0 until a.length()).mapNotNull { a.optString(it, "").ifBlank { null } } }.orEmpty()
        val drill = o.optJSONArray("drillDown")?.let { a -> (0 until a.length()).mapNotNull { a.optString(it, "").ifBlank { null } } }.orEmpty()
        if (additions.isEmpty()) return false

        val newContent = if (cur.content.isBlank()) {
            "# Persona (角色 $characterId)\n\n" + additions.joinToString("\n") { "- $it" }
        } else {
            val today = dayFormat.format(Date())
            buildString {
                append(cur.content.trim())
                appendLine()
                appendLine()
                appendLine("<!-- delta $today ${timeFormat.format(Date())} -->")
                additions.forEach { appendLine("- $it") }
            }
        }
        val updated = cur.copy(
            content = newContent,
            updatedAt = timeFormat.format(Date()),
            drillDown = (cur.drillDown + drill).distinct()
        )
        saveForCharacter(context, characterId, updated)
        return true
    }

    fun loadGlobal(context: Context): PersonaDoc {
        val f = MemoryPaths.globalPersona(context)
        if (!f.exists()) return PersonaDoc(PersonaDoc.Level.L3_GLOBAL, null, "", "")
        return runCatching { PersonaMarkdown.fromMarkdown(f.readText())!! }.getOrNull()
            ?: PersonaDoc(PersonaDoc.Level.L3_GLOBAL, null, "", "")
    }

    fun loadForCharacter(context: Context, characterId: Long): PersonaDoc {
        val f = MemoryPaths.characterPersona(context, characterId)
        if (!f.exists()) return PersonaDoc(PersonaDoc.Level.L3_CHARACTER, characterId, "", "")
        return runCatching { PersonaMarkdown.fromMarkdown(f.readText())!! }.getOrNull()
            ?: PersonaDoc(PersonaDoc.Level.L3_CHARACTER, characterId, "", "")
    }

    fun saveGlobal(context: Context, doc: PersonaDoc) {
        val f = MemoryPaths.globalPersona(context)
        f.parentFile?.mkdirs()
        f.writeText(PersonaMarkdown.toMarkdown(doc), Charsets.UTF_8)
    }

    fun saveForCharacter(context: Context, characterId: Long, doc: PersonaDoc) {
        val f = MemoryPaths.characterPersona(context, characterId)
        f.parentFile?.mkdirs()
        f.writeText(PersonaMarkdown.toMarkdown(doc), Charsets.UTF_8)
    }

    private fun stripCodeFence(s: String): String {
        var t = s.trim()
        if (t.startsWith("```")) {
            t = t.removePrefix("```").trimStart()
            val nl = t.indexOf('\n')
            if (nl in 0..20) t = t.substring(nl + 1)
            if (t.endsWith("```")) t = t.removeSuffix("```")
        }
        return t.trim()
    }

    data class CharacterScenarioDelta(
        val characterId: Long,
        val title: String,
        val summary: String,
        val scenarioId: String? = null
    )
}
