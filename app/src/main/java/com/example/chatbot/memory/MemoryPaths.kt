package com.example.chatbot.memory

import android.content.Context
import java.io.File

/**
 * 4 层记忆 + 短时画布的统一路径配置。所有读写都走这里，避免硬编码。
 *
 * ```
 * <filesDir>/memory/
 *   persona_global.md
 *   global_state.json
 *   characters/<id>/
 *     persona.md
 *     short_term_canvas.md
 *     canvas_state.json
 *     scenarios/<scenarioId>.md
 *     atoms/atoms.jsonl
 *     atoms/atoms_index.sqlite
 *     atoms/atoms_meta.json
 *     refs/<messageId>.md
 *     l0_cache/<yyyy-MM-dd>.md
 * ```
 */
object MemoryPaths {

    private const val ROOT = "memory"
    private const val CHARACTERS = "characters"
    private const val PERSONA_GLOBAL = "persona_global.md"
    private const val GLOBAL_STATE = "global_state.json"
    private const val PERSONA = "persona.md"
    private const val CANVAS = "short_term_canvas.md"
    private const val CANVAS_STATE = "canvas_state.json"
    private const val SCENARIOS = "scenarios"
    private const val ATOMS = "atoms"
    private const val ATOMS_JSONL = "atoms.jsonl"
    private const val ATOMS_INDEX = "atoms_index.sqlite"
    private const val ATOMS_META = "atoms_meta.json"
    private const val REFS = "refs"
    private const val L0_CACHE = "l0_cache"

    fun root(context: Context): File =
        File(context.filesDir, ROOT).apply { if (!exists()) mkdirs() }

    fun globalPersona(context: Context): File = File(root(context), PERSONA_GLOBAL)

    fun globalState(context: Context): File = File(root(context), GLOBAL_STATE)

    fun characterDir(context: Context, characterId: Long): File =
        File(root(context).apply { if (!exists()) mkdirs() }, "$CHARACTERS/$characterId")
            .apply { if (!exists()) mkdirs() }

    fun characterPersona(context: Context, characterId: Long): File =
        File(characterDir(context, characterId), PERSONA)

    fun canvasFile(context: Context, characterId: Long): File =
        File(characterDir(context, characterId), CANVAS)

    fun canvasState(context: Context, characterId: Long): File =
        File(characterDir(context, characterId), CANVAS_STATE)

    fun scenariosDir(context: Context, characterId: Long): File =
        File(characterDir(context, characterId), SCENARIOS).apply { if (!exists()) mkdirs() }

    fun scenarioFile(context: Context, characterId: Long, scenarioId: String): File =
        File(scenariosDir(context, characterId), "$scenarioId.md")

    fun atomsDir(context: Context, characterId: Long): File =
        File(characterDir(context, characterId), ATOMS).apply { if (!exists()) mkdirs() }

    fun atomsJsonl(context: Context, characterId: Long): File =
        File(atomsDir(context, characterId), ATOMS_JSONL)

    fun atomsIndexDb(context: Context, characterId: Long): File =
        File(atomsDir(context, characterId), ATOMS_INDEX)

    fun atomsMeta(context: Context, characterId: Long): File =
        File(atomsDir(context, characterId), ATOMS_META)

    fun refsDir(context: Context, characterId: Long): File =
        File(characterDir(context, characterId), REFS).apply { if (!exists()) mkdirs() }

    fun refFile(context: Context, characterId: Long, name: String): File =
        File(refsDir(context, characterId), name)

    fun l0CacheDir(context: Context, characterId: Long): File =
        File(characterDir(context, characterId), L0_CACHE).apply { if (!exists()) mkdirs() }

    fun l0CacheFile(context: Context, characterId: Long, day: String): File =
        File(l0CacheDir(context, characterId), "$day.md")

    /** 旧 LongTermMemoryManager 目录，用于一次性迁移 */
    @Suppress("unused")
    fun legacyLongTermMemoryDir(context: Context): File =
        File(context.filesDir, "long_term_memory")
}
