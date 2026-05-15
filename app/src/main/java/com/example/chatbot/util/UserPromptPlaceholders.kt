package com.example.chatbot.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 将角色卡 / 系统提示里常见占位符替换为实际文案（个人设置 + 当前角色名 + 本机时间）。
 *
 * **用户侧**：`{{user}}`、`{{uesr}}`、`{{username}}`、`{{user_name}}`、`{{nick}}`、`{{nickname}}`、
 * `{{player}}`、`{{you}}`、`{{用户}}`、`{{玩家}}`、`{{主人}}`；人设：`{{persona}}`、`{{user_persona}}`、`{{人设}}`。
 *
 * **角色侧**：`{{char}}`、`{{char_name}}`、`{{character}}`、`{{assistant}}`、`{{bot}}`、`{{角色}}`、`{{角色名}}`。
 *
 * **时间**：`{{date}}`、`{{today}}`、`{{time}}`、`{{datetime}}`、`{{now}}`、`{{当前日期}}`、`{{当前时间}}`。
 */
object UserPromptPlaceholders {

    private fun token(name: String, ignoreCase: Boolean = true): Regex {
        val options = if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
        return Regex("""\{\{\s*${Regex.escape(name)}\s*\}\}""", options)
    }

    fun apply(
        text: String,
        displayName: String,
        persona: String = "",
        characterName: String = ""
    ): String {
        if (text.isEmpty()) return text
        val user = displayName.ifBlank { "用户" }
        val char = characterName.ifBlank { "角色" }
        var out = text

        val locale = Locale.getDefault()
        val now = Date()
        val dfDate = SimpleDateFormat("yyyy-MM-dd", locale)
        val dfTime = SimpleDateFormat("HH:mm", locale)
        val dfDateTime = SimpleDateFormat("yyyy-MM-dd HH:mm", locale)
        val dateStr = dfDate.format(now)
        val timeStr = dfTime.format(now)
        val dateTimeStr = dfDateTime.format(now)

        out = token("datetime").replace(out) { dateTimeStr }
        out = token("now").replace(out) { dateTimeStr }
        out = token("date").replace(out) { dateStr }
        out = token("today").replace(out) { dateStr }
        out = token("time").replace(out) { timeStr }
        out = token("当前日期", ignoreCase = false).replace(out) { dateStr }
        out = token("当前时间", ignoreCase = false).replace(out) { timeStr }

        out = token("char_name").replace(out) { char }
        out = token("character").replace(out) { char }
        out = token("assistant").replace(out) { char }
        out = token("bot").replace(out) { char }
        out = token("角色名", ignoreCase = false).replace(out) { char }
        out = token("角色", ignoreCase = false).replace(out) { char }
        out = token("char").replace(out) { char }

        out = token("user_name").replace(out) { user }
        out = token("nickname").replace(out) { user }
        out = token("nick").replace(out) { user }
        out = token("username").replace(out) { user }
        out = token("uesr").replace(out) { user }
        out = token("user").replace(out) { user }
        out = token("player").replace(out) { user }
        out = token("you").replace(out) { user }
        out = token("用户", ignoreCase = false).replace(out) { user }
        out = token("玩家", ignoreCase = false).replace(out) { user }
        out = token("主人", ignoreCase = false).replace(out) { user }

        if (persona.isNotBlank()) {
            out = token("user_persona").replace(out) { persona }
            out = token("persona").replace(out) { persona }
            out = token("人设", ignoreCase = false).replace(out) { persona }
        }

        return out
    }
}
