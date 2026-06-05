package com.example.chatbot.memory

import android.content.Context
import android.content.SharedPreferences

/**
 * 4 层记忆的可调参数。集中存 SharedPreferences，避免散落到各处。
 *
 * 字段命名尽量贴近 TencentDB-Agent-Memory README 的 Level 1 / Level 2 / Level 3 三档可调参数，
 * 方便后续做"高级设置" UI 时直接对接。
 */
object MemoryConfig {

    private const val PREFS = "memory_pipeline_prefs"

    // ---- 开关 ----
    /** 4 层记忆总开关。关闭后所有 manager 都降级为 no-op。 */
    const val KEY_ENABLED = "memory_enabled"

    // ---- L1 节奏 ----
    const val KEY_L1_EVERY_N_TURNS = "l1_every_n_turns"
    const val KEY_L1_MIN_MESSAGES = "l1_min_messages"

    // ---- L2 节奏 ----
    const val KEY_L2_EVERY_N_ATOMS = "l2_every_n_atoms"
    const val KEY_L2_MIN_ATOMS = "l2_min_atoms"

    // ---- L3 节奏 ----
    const val KEY_L3_EVERY_N_ATOMS_GLOBAL = "l3_every_n_atoms_global"

    // ---- 召回（TencentDB 文档 Level 1） ----
    const val KEY_RECALL_TOP_PERSONAS = "recall_top_personas"
    const val KEY_RECALL_TOP_SCENARIOS = "recall_top_scenarios"
    const val KEY_RECALL_TOP_ATOMS = "recall_top_atoms"
    const val KEY_RECALL_MAX_CHARS_PER_ATOM = "recall_max_chars_per_atom"
    const val KEY_RECALL_MAX_TOTAL_CHARS = "recall_max_total_recall_chars"
    const val KEY_RECALL_TIMEOUT_MS = "recall_timeout_ms"

    // ---- 短时压缩 ----
    /** 0.3 ~ 0.9 之间；超过该比例后开始把更早的整段对话外置到 refs/。 */
    const val KEY_OFFLOAD_MILD_RATIO = "offload_mild_ratio"
    /** 0.6 ~ 0.95 之间；超过后节点进一步标题化。 */
    const val KEY_OFFLOAD_AGGRESSIVE_RATIO = "offload_aggressive_ratio"
    /** 上下文窗口（tokens）。默认按 8K 计算。 */
    const val KEY_CONTEXT_WINDOW_TOKENS = "context_window_tokens"

    // ---- 嵌入模型 ----
    const val KEY_EMBED_MODEL = "embed_model"
    const val KEY_EMBED_DIM = "embed_dim"
    /** 后端：remote | local | auto */
    const val KEY_EMBED_BACKEND = "embed_backend"
    /** 远程 embedding 模型名（OpenAI 兼容的 embeddings endpoint 都吃） */
    const val KEY_EMBED_REMOTE_MODEL = "embed_remote_model"

    // ---- 迁移标志 ----
    const val KEY_LEGACY_MIGRATED = "legacy_migrated"

    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    // ---- 便捷读默认值 ----
    fun l1EveryN(context: Context): Int =
        prefs(context).getInt(KEY_L1_EVERY_N_TURNS, 5).coerceAtLeast(1)

    fun l1MinMessages(context: Context): Int =
        prefs(context).getInt(KEY_L1_MIN_MESSAGES, 5).coerceAtLeast(1)

    fun l2EveryNAtoms(context: Context): Int =
        prefs(context).getInt(KEY_L2_EVERY_N_ATOMS, 50).coerceAtLeast(10)

    fun l2MinAtoms(context: Context): Int =
        prefs(context).getInt(KEY_L2_MIN_ATOMS, 20).coerceAtLeast(5)

    fun l3EveryNAtomsGlobal(context: Context): Int =
        prefs(context).getInt(KEY_L3_EVERY_N_ATOMS_GLOBAL, 50).coerceAtLeast(10)

    fun recallTopAtoms(context: Context): Int =
        prefs(context).getInt(KEY_RECALL_TOP_ATOMS, 8).coerceIn(1, 20)

    fun recallTopScenarios(context: Context): Int =
        prefs(context).getInt(KEY_RECALL_TOP_SCENARIOS, 2).coerceIn(0, 5)

    fun recallMaxCharsPerAtom(context: Context): Int =
        prefs(context).getInt(KEY_RECALL_MAX_CHARS_PER_ATOM, 240).coerceAtLeast(0)

    fun recallMaxTotalChars(context: Context): Int =
        prefs(context).getInt(KEY_RECALL_MAX_TOTAL_CHARS, 2400).coerceAtLeast(0)

    fun recallTimeoutMs(context: Context): Long =
        prefs(context).getLong(KEY_RECALL_TIMEOUT_MS, 5000L).coerceAtLeast(100L)

    fun offloadMildRatio(context: Context): Float =
        prefs(context).getFloat(KEY_OFFLOAD_MILD_RATIO, 0.5f)
            .coerceIn(0.2f, 0.9f)

    fun offloadAggressiveRatio(context: Context): Float =
        prefs(context).getFloat(KEY_OFFLOAD_AGGRESSIVE_RATIO, 0.85f)
            .coerceIn(0.5f, 0.98f)

    fun contextWindowTokens(context: Context): Int =
        prefs(context).getInt(KEY_CONTEXT_WINDOW_TOKENS, 8000).coerceAtLeast(2000)

    fun embedModelName(context: Context): String =
        prefs(context).getString(KEY_EMBED_MODEL, "bge-small-zh-v1.5-int8") ?: "bge-small-zh-v1.5-int8"

    fun embedDim(context: Context): Int =
        prefs(context).getInt(KEY_EMBED_DIM, 1024).coerceAtLeast(64)

    /**
     * 仅作历史 prefs 兼容：旧值 "local" / "auto" 一律视作 "remote"（已不再打包本地 ONNX 模型）。
     * 真正的取值只有 "remote"（默认）。
     */
    fun embedBackend(context: Context): String =
        (prefs(context).getString(KEY_EMBED_BACKEND, "remote") ?: "remote")
            .lowercase()
            .let { if (it == "local" || it == "auto") "remote" else it }

    fun setEmbedBackend(context: Context, v: String) {
        prefs(context).edit().putString(KEY_EMBED_BACKEND, v).apply()
    }

    fun embedRemoteModel(context: Context): String =
        prefs(context).getString(KEY_EMBED_REMOTE_MODEL, "BAAI/bge-large-zh-v1.5")
            ?: "BAAI/bge-large-zh-v1.5"

    fun setEmbedRemoteModel(context: Context, v: String) {
        prefs(context).edit().putString(KEY_EMBED_REMOTE_MODEL, v).apply()
    }
}
