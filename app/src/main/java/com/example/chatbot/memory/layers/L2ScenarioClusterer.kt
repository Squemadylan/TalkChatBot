package com.example.chatbot.memory.layers

import android.content.Context
import com.example.chatbot.data.model.ApiConfig
import com.example.chatbot.memory.Atom
import com.example.chatbot.memory.AtomJsonl
import com.example.chatbot.memory.LlmCall
import com.example.chatbot.memory.MemoryConfig
import com.example.chatbot.memory.MemoryPaths
import com.example.chatbot.memory.ScenarioBlock
import com.example.chatbot.memory.ScenarioMarkdown
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * L2 场景聚类。
 *
 * 输入：自上次聚类以来新增的 atoms（去重后）。
 * 输出：决策 JSON：
 *   { "create":[{"id":..., "title":..., "summary":..., "atomIds":[...]}],
 *     "merge":[{"targetId":..., "atomIds":[...]}],
 *     "obsolete":["scn_xxx"] }
 *
 * 写：scenarios/<id>.md，并尝试回写 atom.scenarioId（仅在 JSONL 内重写一行）。
 */
object L2ScenarioClusterer {

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())

    data class Result(
        val created: List<ScenarioBlock>,
        val merged: Int,
        val obsoleted: Int
    )

    /** 召回侧用的精简版 */
    data class ScenarioLite(val block: ScenarioBlock)

    /** 是否应当触发 L2 聚类 */
    fun shouldCluster(context: Context, characterId: Long, currentAtomCount: Int): Boolean {
        if (!MemoryConfig.isEnabled(context)) return false
        if (currentAtomCount < MemoryConfig.l2MinAtoms(context)) return false
        return true
    }

    suspend fun cluster(
        context: Context,
        characterId: Long,
        apiConfig: ApiConfig,
        newAtoms: List<Atom>
    ): Result {
        if (newAtoms.isEmpty()) return Result(emptyList(), 0, 0)

        val existing = loadAllScenarios(context, characterId)

        val systemPrompt = """
            你是 L2 场景聚类器。把原子事实（atoms）按主题聚成场景（scenario）。
            一个 scenario 是一个"反复出现或持续的话题 / 剧情线"，可能跨多天。
            输出严格 JSON，不要 Markdown 代码块。
            schema:
            {
              "create": [{"id":"scn_xxx","title":"...","summary":"...","atomIds":["atom_xxx", ...]}],
              "merge":  [{"targetId":"scn_xxx","atomIds":["atom_xxx"]}],
              "obsolete": ["scn_xxx"]
            }
        """.trimIndent()
        val userPrompt = buildString {
            appendLine("角色: $characterId")
            if (existing.isNotEmpty()) {
                appendLine("已存在场景：")
                existing.forEach { s ->
                    appendLine("- ${s.id} :: ${s.title} :: atomCount=${s.atomCount}")
                }
            }
            appendLine()
            appendLine("新增原子事实：")
            newAtoms.forEach { a ->
                appendLine("- ${a.id} [${a.type.keyword}/${a.subject.keyword}] ${a.text.take(200)}")
            }
        }
        val raw = LlmCall.callJsonText(
            apiConfig = apiConfig,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            maxTokens = 1500,
            temperature = 0.3
        )
        if (raw.isBlank()) return Result(emptyList(), 0, 0)

        val o = runCatching { JSONObject(stripCodeFence(raw)) }.getOrNull() ?: return Result(emptyList(), 0, 0)
        val created = ArrayList<ScenarioBlock>()
        val merged = ArrayList<Pair<String, List<String>>>()
        val obsoleted = ArrayList<String>()

        o.optJSONArray("create")?.let { arr ->
            for (i in 0 until arr.length()) {
                val s = arr.optJSONObject(i) ?: continue
                val id = s.optString("id").ifBlank { "scn_${UUID.randomUUID()}" }
                val title = s.optString("title").ifBlank { "未命名场景" }
                val summary = s.optString("summary")
                val atomIds = s.optJSONArray("atomIds")?.let { a ->
                    (0 until a.length()).mapNotNull { a.optString(it, "").ifBlank { null } }
                }.orEmpty()
                val block = ScenarioBlock(
                    id = id,
                    characterId = characterId,
                    title = title,
                    summary = summary,
                    createdAt = timeFormat.format(Date()),
                    updatedAt = timeFormat.format(Date()),
                    atomCount = atomIds.size,
                    atomIds = atomIds
                )
                writeScenario(context, block)
                created.add(block)
            }
        }
        o.optJSONArray("merge")?.let { arr ->
            for (i in 0 until arr.length()) {
                val s = arr.optJSONObject(i) ?: continue
                val target = s.optString("targetId")
                val atomIds = s.optJSONArray("atomIds")?.let { a ->
                    (0 until a.length()).mapNotNull { a.optString(it, "").ifBlank { null } }
                }.orEmpty()
                if (target.isNotBlank() && atomIds.isNotEmpty()) {
                    merged.add(target to atomIds)
                }
            }
        }
        o.optJSONArray("obsolete")?.let { arr ->
            for (i in 0 until arr.length()) obsoleted.add(arr.optString(i, ""))
        }

        // 回写 atom.scenarioId
        rewriteAtomScenarioIds(context, characterId, created, merged)
        // 合并：把 atomIds 追加到目标 scenario 的 atomIds
        merged.forEach { (target, atomIds) ->
            val cur = loadScenario(context, characterId, target) ?: return@forEach
            val newSet = (cur.atomIds + atomIds).distinct()
            val updated = cur.copy(
                atomIds = newSet,
                atomCount = newSet.size,
                updatedAt = timeFormat.format(Date())
            )
            writeScenario(context, updated)
        }
        // 废弃
        obsoleted.forEach { id ->
            if (id.isBlank()) return@forEach
            MemoryPaths.scenarioFile(context, characterId, id).delete()
        }
        return Result(created, merged.size, obsoleted.size)
    }

    fun loadAllScenarios(context: Context, characterId: Long): List<ScenarioBlock> {
        val dir = MemoryPaths.scenariosDir(context, characterId)
        val out = ArrayList<ScenarioBlock>()
        dir.listFiles()?.forEach { f ->
            if (!f.isFile || !f.name.endsWith(".md")) return@forEach
            runCatching { ScenarioMarkdown.fromMarkdown(f.readText()) }.getOrNull()?.let(out::add)
        }
        return out
    }

    fun loadScenario(context: Context, characterId: Long, scenarioId: String): ScenarioBlock? {
        val f = MemoryPaths.scenarioFile(context, characterId, scenarioId)
        if (!f.exists()) return null
        return runCatching { ScenarioMarkdown.fromMarkdown(f.readText()) }.getOrNull()
    }

    fun writeScenario(context: Context, block: ScenarioBlock) {
        val body = buildString {
            appendLine("# ${block.title}")
            appendLine()
            appendLine("## 摘要")
            appendLine(block.summary.ifBlank { "（暂无摘要）" })
            if (block.atomIds.isNotEmpty()) {
                appendLine()
                appendLine("## 关键原子")
                block.atomIds.take(20).forEach { id ->
                    appendLine("- $id")
                }
            }
        }
        val md = ScenarioMarkdown.toMarkdown(block, body)
        val f = MemoryPaths.scenarioFile(context, block.characterId, block.id)
        f.parentFile?.mkdirs()
        f.writeText(md, Charsets.UTF_8)
    }

    private fun rewriteAtomScenarioIds(
        context: Context,
        characterId: Long,
        created: List<ScenarioBlock>,
        merged: List<Pair<String, List<String>>>
    ) {
        val file = MemoryPaths.atomsJsonl(context, characterId)
        if (!file.exists()) return
        val all = AtomJsonl.readAll(file)
        val idToScenario = HashMap<String, String>()
        created.forEach { s -> s.atomIds.forEach { idToScenario[it] = s.id } }
        merged.forEach { (target, ids) -> ids.forEach { idToScenario[it] = target } }
        val sb = StringBuilder()
        var changed = false
        for (a in all) {
            val newSid = idToScenario[a.id]
            if (newSid != null && a.scenarioId != newSid) {
                changed = true
                sb.appendLine(AtomJsonl.toJson(a.copy(scenarioId = newSid)))
            } else {
                sb.appendLine(AtomJsonl.toJson(a))
            }
        }
        if (changed) file.writeText(sb.toString(), Charsets.UTF_8)
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
}
