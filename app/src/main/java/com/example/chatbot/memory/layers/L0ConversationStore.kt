package com.example.chatbot.memory.layers

import com.example.chatbot.data.model.Message
import com.example.chatbot.memory.MemoryPaths
import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * L0 原始对话滚动缓存。每天一文件，超 256KB 滚动到次日。
 *
 * 这里不去重新格式化成自有结构，而是直接追加 Markdown 风格的行：
 *   HH:mm:ss [user|assistant] <content>
 *
 * 这样人和 Agent 都能直接读，TencentDB README 中强调的"白盒可审查"原则得到保留。
 */
object L0ConversationStore {

    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private const val MAX_FILE_BYTES = 256L * 1024L

    fun appendTurn(context: Context, characterId: Long, msg: Message) {
        val day = dayFormat.format(Date(msg.timestamp))
        val file = MemoryPaths.l0CacheFile(context, characterId, day)
        val line = "${timeFormat.format(Date(msg.timestamp))} " +
                if (msg.isUser) "[user] " else "[assistant] " +
                sanitize(msg.content) + "\n"
        runCatching {
            file.parentFile?.mkdirs()
            if (file.exists() && file.length() > MAX_FILE_BYTES) {
                // 滚动：原文件改名为 "yyyy-MM-dd-<seq>.md"（seq 自增）
                rotate(context, characterId, day)
            }
            file.appendText(line, Charsets.UTF_8)
        }
    }

    fun loadAll(context: Context, characterId: Long): List<Pair<Long, String>> {
        val dir = MemoryPaths.l0CacheDir(context, characterId)
        if (!dir.exists()) return emptyList()
        val out = ArrayList<Pair<Long, String>>()
        val files = dir.listFiles()?.sortedBy { it.name } ?: return emptyList()
        for (f in files) {
            if (!f.isFile || !f.name.endsWith(".md")) continue
            f.useLines { lines ->
                for (line in lines) {
                    val t = line.trim()
                    if (t.isEmpty()) continue
                    val ts = parseTs(t, f.nameWithoutExtension) ?: continue
                    out.add(ts to t)
                }
            }
        }
        return out.sortedBy { it.first }
    }

    private fun rotate(context: Context, characterId: Long, day: String) {
        val dir = MemoryPaths.l0CacheDir(context, characterId)
        var seq = 1
        while (File(dir, "$day-$seq.md").exists()) seq++
        val src = File(dir, "$day.md")
        val dst = File(dir, "$day-$seq.md")
        src.renameTo(dst)
    }

    private fun parseTs(line: String, day: String): Long? {
        val m = Regex("^(\\d{2}:\\d{2}:\\d{2})").find(line) ?: return null
        val timePart = m.groupValues[1]
        return runCatching {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            sdf.parse("$day $timePart")?.time
        }.getOrNull()
    }

    private fun sanitize(s: String): String =
        s.replace("\r", " ").replace("\n", " ")
}
