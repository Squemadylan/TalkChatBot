package com.example.chatbot.memory

import android.content.Context
import com.example.chatbot.memory.layers.L2ScenarioClusterer
import com.example.chatbot.memory.layers.L3PersonaUpdater
import com.example.chatbot.memory.layers.ShortTermCanvas

/**
 * 把 4 层记忆 + 短时画布拼成 system 消息。
 *
 * 顺序与 README 强调一致：
 *  1. L3 Persona（global + character）：最概览、长期偏好
 *  2. L2 命中场景摘要：当下话题
 *  3. L1 命中原子事实：具体细节
 *  4. Mermaid 短时画布：当前对话结构（最易压缩 / 钻取）
 */
object PromptBuilder {

    data class BuildResult(
        val systemMessage: String?,
        val personaChars: Int,
        val scenarioChars: Int,
        val atomChars: Int,
        val canvasChars: Int
    )

    fun build(
        context: Context,
        characterId: Long,
        query: String,
        extraCharacterPrompt: String? = null
    ): BuildResult {
        if (!MemoryConfig.isEnabled(context)) return BuildResult(null, 0, 0, 0, 0)
        val personaGlobal = L3PersonaUpdater.loadGlobal(context).content
        val personaCharacter = L3PersonaUpdater.loadForCharacter(context, characterId).content

        val scenarios = MemoryRetriever.recallScenarios(context, characterId, query)
        val atoms = MemoryRetriever.recallAtoms(context, characterId, query)
        val canvas = ShortTermCanvas.loadCanvasText(context, characterId)

        val totalBudget = MemoryConfig.recallMaxTotalChars(context)
        val perAtom = MemoryConfig.recallMaxCharsPerAtom(context)

        val sb = StringBuilder()
        val personaCombined = buildString {
            if (personaGlobal.isNotBlank()) {
                appendLine("【Persona · 全局】")
                appendLine(personaGlobal.trim())
                appendLine()
            }
            if (personaCharacter.isNotBlank()) {
                appendLine("【Persona · 当前角色】")
                appendLine(personaCharacter.trim())
            }
        }.trim()
        if (personaCombined.isNotBlank()) {
            sb.appendLine(personaCombined)
            sb.appendLine()
        }
        val personaChars = personaCombined.length

        if (scenarios.isNotEmpty()) {
            sb.appendLine("【活跃场景】")
            scenarios.forEach { lite ->
                sb.appendLine("- ${lite.block.title}：${lite.block.summary.take(300).ifBlank { "（无摘要）" }}")
            }
            sb.appendLine()
        }
        val scenarioChars = sb.length - personaChars

        var usedAtomChars = 0
        if (atoms.isNotEmpty()) {
            sb.appendLine("【相关事实】")
            for (h in atoms) {
                val line = "- [${h.atom.type.keyword}/${h.atom.subject.keyword}] ${h.atom.text}"
                if (totalBudget > 0 && usedAtomChars + line.length + 1 > totalBudget) break
                sb.appendLine(line)
                usedAtomChars += line.length + 1
            }
            sb.appendLine()
        }
        val atomChars = usedAtomChars

        var canvasChars = 0
        if (canvas.isNotBlank()) {
            sb.appendLine("【当前对话画布】")
            sb.appendLine(canvas.take(2000))
            canvasChars = canvas.length.coerceAtMost(2000)
        }

        if (extraCharacterPrompt.isNullOrBlank().not()) {
            // 不在 system 段插入角色卡，避免与原 ChatViewModel 的 system 冲突
        }

        if (sb.isBlank()) return BuildResult(null, 0, 0, 0, 0)
        return BuildResult(
            systemMessage = sb.toString().trim(),
            personaChars = personaChars,
            scenarioChars = scenarioChars,
            atomChars = atomChars,
            canvasChars = canvasChars
        )
    }
}
