package com.example.chatbot.memory

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 原子事实的 JSONL 读写。append 模式下不重写整个文件，按行写入。
 *
 * 注意：用 Android 内置的 org.json，避免引入新依赖。
 */
object AtomJsonl {

    fun append(file: File, atom: Atom) {
        if (!file.exists()) file.parentFile?.mkdirs()
        file.appendText(toJson(atom) + "\n", Charsets.UTF_8)
    }

    fun readAll(file: File): List<Atom> {
        if (!file.exists()) return emptyList()
        val out = ArrayList<Atom>()
        file.useLines { lines ->
            for (line in lines) {
                val t = line.trim()
                if (t.isEmpty()) continue
                runCatching { fromJson(t) }.getOrNull()?.let(out::add)
            }
        }
        return out
    }

    fun fromJson(line: String): Atom {
        val o = JSONObject(line)
        val sourceIds = o.optJSONArray("sourceMessageIds")?.let { arr ->
            (0 until arr.length()).map { arr.getLong(it) }
        }.orEmpty()
        return Atom(
            id = o.getString("id"),
            characterId = o.optLong("characterId", 0L),
            scenarioId = o.optString("scenarioId", "").ifBlank { null },
            ts = o.optLong("ts", System.currentTimeMillis()),
            type = AtomType.fromKeyword(o.optString("type", "")),
            subject = AtomSubject.fromKeyword(o.optString("subject", "")),
            text = o.optString("text", ""),
            sourceMessageIds = sourceIds,
            ref = o.optString("ref", "").ifBlank { null }
        )
    }

    fun toJson(a: Atom): String {
        val o = JSONObject()
        o.put("id", a.id)
        o.put("characterId", a.characterId)
        if (a.scenarioId != null) o.put("scenarioId", a.scenarioId)
        o.put("ts", a.ts)
        o.put("type", a.type.keyword)
        o.put("subject", a.subject.keyword)
        o.put("text", a.text)
        if (a.sourceMessageIds.isNotEmpty()) {
            val arr = JSONArray()
            a.sourceMessageIds.forEach { arr.put(it) }
            o.put("sourceMessageIds", arr)
        }
        if (a.ref != null) o.put("ref", a.ref)
        return o.toString()
    }
}
