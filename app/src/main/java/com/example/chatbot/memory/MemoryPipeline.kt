package com.example.chatbot.memory

import android.content.Context
import android.util.Log
import com.example.chatbot.data.model.ApiConfig
import com.example.chatbot.data.model.Message
import com.example.chatbot.memory.embed.EmbedderFactory
import com.example.chatbot.memory.layers.L0ConversationStore
import com.example.chatbot.memory.layers.L1AtomExtractor
import com.example.chatbot.memory.layers.L2ScenarioClusterer
import com.example.chatbot.memory.layers.L3PersonaUpdater
import com.example.chatbot.memory.layers.ShortTermCanvas
import com.example.chatbot.memory.vec.VecIndexFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 4 层记忆 + 短时压缩的编排器（TencentDB Agent Memory 的 pipeline 移植）。
 *
 * 入口：
 *  - [onTurnComplete]  对话成功一轮后由 ChatViewModel 调用
 *  - [buildContextSystemMessage]  发送消息前由 ChatViewModel 拼 system
 *  - [migrateLegacy]   旧 LongTermMemoryManager 数据迁移（启动时调用一次）
 *
 * 并发：单角色串行；不同角色并发；L1/L2/L3 全局串行（用全局锁）。
 */
object MemoryPipeline {

    private const val TAG = "MemoryPipeline"

    private val perCharMutex = ConcurrentHashMap<Long, Mutex>()
    private val globalMutex = Mutex()
    private val initialized = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun initOnce(context: Context) {
        if (initialized.getAndSet(true)) return
        // 预热：触发 embedder 加载，并行迁移旧数据
        val appCtx = context.applicationContext
        scope.launch {
            runCatching { EmbedderFactory.get(appCtx) }.onFailure { Log.w(TAG, "embedder init failed", it) }
            runCatching { migrateLegacy(appCtx) }.onFailure { Log.w(TAG, "legacy migrate failed", it) }
        }
    }

    /** 旧 LongTermMemoryManager 的一次性迁移 */
    suspend fun migrateLegacy(context: Context) {
        if (MemoryConfig.prefs(context).getBoolean(MemoryConfig.KEY_LEGACY_MIGRATED, false)) return
        val legacyDir = MemoryPaths.legacyLongTermMemoryDir(context)
        if (!legacyDir.exists()) {
            MemoryConfig.prefs(context).edit().putBoolean(MemoryConfig.KEY_LEGACY_MIGRATED, true).apply()
            return
        }
        val files = legacyDir.listFiles()?.filter { it.isFile && it.name.startsWith("memory_") && it.name.endsWith(".md") }
            ?: emptyList()
        files.forEach { f ->
            val idStr = f.name.removePrefix("memory_").removeSuffix(".md")
            val cid = idStr.toLongOrNull() ?: return@forEach
            val content = runCatching { f.readText() }.getOrNull().orEmpty()
            if (content.isBlank()) return@forEach
            val target = MemoryPaths.characterPersona(context, cid)
            target.parentFile?.mkdirs()
            val body = buildString {
                appendLine("# Persona（来自旧版本长期记忆迁移）")
                appendLine()
                appendLine("<!-- migrated from memory_$cid.md -->")
                appendLine()
                append(content)
            }
            target.writeText(body, Charsets.UTF_8)
            runCatching { f.delete() }
        }
        // 旧 prefs 标记位也清掉（next-gen 直接写新 prefs）
        val oldPrefs = context.getSharedPreferences("long_term_memory_prefs", Context.MODE_PRIVATE)
        oldPrefs.edit().clear().apply()
        MemoryConfig.prefs(context).edit().putBoolean(MemoryConfig.KEY_LEGACY_MIGRATED, true).apply()
        Log.d(TAG, "Legacy long-term memory migrated: ${files.size} files")
    }

    fun buildContextSystemMessage(
        context: Context,
        characterId: Long,
        query: String
    ): String? {
        return runCatching {
            PromptBuilder.build(context, characterId, query).systemMessage
        }.getOrNull()
    }

    /**
     * 调试用：临时把 L1/L2 阈值都压到 1，强制对 [characterId] 跑一整轮。
     * 不修改 prefs，跑完恢复。返回一段简短的执行摘要。
     *
     * 用于：少量对话也想看 L2/L3 是否正常工作；或者排查「为什么还没生成场景」之类问题。
     */
    fun forceRunAll(
        context: Context,
        characterId: Long,
        apiConfig: ApiConfig,
        onDone: (String) -> Unit = {}
    ) {
        scope.launch {
            try {
                val ctx = context.applicationContext
                val prefs = MemoryConfig.prefs(ctx)
                val e = prefs.edit()
                // snapshot 用户原值
                val keys = listOf(
                    MemoryConfig.KEY_L1_MIN_MESSAGES,
                    MemoryConfig.KEY_L1_EVERY_N_TURNS,
                    MemoryConfig.KEY_L2_MIN_ATOMS,
                    MemoryConfig.KEY_L2_EVERY_N_ATOMS
                )
                val snapshot = keys.associateWith { prefs.getInt(it, Int.MIN_VALUE) }
                val summary = StringBuilder()
                try {
                    // 把阈值压到 1
                    keys.forEach { e.putInt(it, 1) }
                    e.apply()

                    val history = withContext(Dispatchers.IO) {
                        runCatching { messageRepo()?.getAllMessagesByCharacterId(characterId) }
                            .getOrNull() ?: emptyList()
                    }
                    if (history.isEmpty()) {
                        summary.append("该角色还没有对话记录")
                    } else {
                        val lastId = L1AtomExtractor.lastExtractedMessageId(ctx, characterId)
                        val newOnes = history.filter { it.id > lastId }
                        summary.append("L1 新增消息=${newOnes.size}\n")

                        val newAtoms = L1AtomExtractor.extract(ctx, characterId, apiConfig, newOnes)
                        val totalAtomsNow = L1AtomExtractor.loadTotalAtomsFromFile(ctx, characterId)
                        summary.append("L1 抽到 atom=${newAtoms.size}，累计=${totalAtomsNow}\n")

                        val l2Result = L2ScenarioClusterer.cluster(ctx, characterId, apiConfig, newAtoms)
                        val allScenarios = L2ScenarioClusterer.loadAllScenarios(ctx, characterId)
                        summary.append("L2 新建 scenario=${l2Result.created.size}，累计场景=${allScenarios.size}\n")

                        if (l2Result.created.isNotEmpty()) {
                            L3PersonaUpdater.updateForCharacter(
                                ctx, apiConfig, characterId,
                                l2Result.created.map { d ->
                                    L3PersonaUpdater.CharacterScenarioDelta(
                                        characterId = d.characterId,
                                        title = d.title,
                                        summary = d.summary,
                                        scenarioId = d.id
                                    )
                                }
                            )
                            summary.append("L3 character 已更新\n")
                        }
                        val deltas = collectAllCharacterScenarioDeltas(ctx)
                        if (deltas.isNotEmpty()) {
                            L3PersonaUpdater.updateGlobal(ctx, apiConfig, deltas)
                            summary.append("L3 global 已更新（deltas=${deltas.size}）\n")
                        }
                    }
                } finally {
                    // 恢复 snapshot；Int.MIN_VALUE 表示之前没设过，直接 remove
                    val e2 = prefs.edit()
                    snapshot.forEach { (k, v) ->
                        if (v == Int.MIN_VALUE) e2.remove(k) else e2.putInt(k, v)
                    }
                    e2.apply()
                }
                withContext(Dispatchers.Main) { onDone(summary.toString()) }
            } catch (e: Exception) {
                Log.e(TAG, "forceRunAll failed for char=$characterId", e)
                withContext(Dispatchers.Main) { onDone("forceRunAll 失败: ${e.message}") }
            }
        }
    }

    private fun messageRepo(): com.example.chatbot.data.repository.MessageRepository? {
        return runCatching {
            val app = com.example.chatbot.App.getInstance()
            if (!app.isDatabaseInitialized()) return null
            com.example.chatbot.data.repository.MessageRepository(app.database.messageDao())
        }.getOrNull()
    }

    /**
     * 对话成功一轮后调用：写 L0 缓存、决定外置、跑 L1/L2/L3、刷新画布。
     * 任何一步异常都被吞掉，绝不阻塞对话流。
     */
    fun onTurnComplete(
        context: Context,
        characterId: Long,
        apiConfig: ApiConfig?,
        userMessage: Message,
        assistantMessage: Message,
        historyTail: List<Message>
    ) {
        if (!MemoryConfig.isEnabled(context)) return
        scope.launch {
            try {
                val mutex = perCharMutex.getOrPut(characterId) { Mutex() }
                mutex.withLock { perCharWork(context, characterId, apiConfig, userMessage, assistantMessage, historyTail) }
            } catch (e: Exception) {
                Log.e(TAG, "onTurnComplete failed for char=$characterId", e)
            }
        }
    }

    private suspend fun perCharWork(
        context: Context,
        characterId: Long,
        apiConfig: ApiConfig?,
        userMessage: Message,
        assistantMessage: Message,
        historyTail: List<Message>
    ) {
        // 1. L0 滚动
        runCatching {
            L0ConversationStore.appendTurn(context, characterId, userMessage)
            L0ConversationStore.appendTurn(context, characterId, assistantMessage)
        }

        // 2. 短时画布 + 决定外置
        val histText = historyTail.joinToString("\n") { m ->
            if (m.isUser) "[user] ${m.content}" else "[assistant] ${m.content}"
        }
        val canvasText = ShortTermCanvas.loadCanvasText(context, characterId)
        val ratio = ShortTermCanvas.decideOffload(context, histText, canvasText)
        val mild = MemoryConfig.offloadMildRatio(context)
        val aggressive = MemoryConfig.offloadAggressiveRatio(context)
        if (ratio >= mild) {
            val refName = ShortTermCanvas.fullOffloadRefName()
            runCatching {
                val refFile = MemoryPaths.refFile(context, characterId, refName)
                if (!refFile.exists()) refFile.createNewFile()
                refFile.appendText(buildString {
                    appendLine("<!-- offloaded at ${System.currentTimeMillis()} ratio=$ratio -->")
                    historyTail.forEach { m ->
                        val role = if (m.isUser) "user" else "assistant"
                        appendLine("[$role ${m.timestamp}] ${m.content}")
                    }
                }, Charsets.UTF_8)
            }
            runCatching {
                ShortTermCanvas.appendTurn(context, characterId, userMessage, refFile = refName, isOffloaded = true)
                ShortTermCanvas.appendTurn(context, characterId, assistantMessage, refFile = refName, isOffloaded = true)
            }
        } else {
            runCatching {
                ShortTermCanvas.appendTurn(context, characterId, userMessage)
                ShortTermCanvas.appendTurn(context, characterId, assistantMessage)
            }
        }
        if (ratio >= aggressive) {
            // 进一步把画布节点标题化：这里简化处理——超过 200 行就只保留最近 60 行
            runCatching { truncateCanvasIfTooLong(context, characterId, keepLines = 60) }
        }

        // 3. L1 抽取
        if (apiConfig != null) {
            val lastId = L1AtomExtractor.lastExtractedMessageId(context, characterId)
            val newOnes = historyTail.filter { it.id > lastId }
            if (newOnes.size >= MemoryConfig.l1MinMessages(context) && newOnes.size >= MemoryConfig.l1EveryN(context)) {
                val newAtoms = runCatching {
                    L1AtomExtractor.extract(context, characterId, apiConfig, newOnes)
                }.getOrDefault(emptyList())

                // 4. L2 聚类（串行）
                val totalAtoms = L1AtomExtractor.loadTotalAtomsFromFile(context, characterId)
                if (newAtoms.isNotEmpty() && totalAtoms >= MemoryConfig.l2MinAtoms(context)) {
                    val l2Result = runCatching {
                        L2ScenarioClusterer.cluster(context, characterId, apiConfig, newAtoms)
                    }.getOrNull()
                    if (l2Result != null && l2Result.created.isNotEmpty()) {
                        runCatching {
                            L3PersonaUpdater.updateForCharacter(
                                context, apiConfig, characterId,
                                l2Result.created.map { d ->
                                    L3PersonaUpdater.CharacterScenarioDelta(
                                        characterId = d.characterId,
                                        title = d.title,
                                        summary = d.summary,
                                        scenarioId = d.id
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        // 5. L3 global（全部角色累计 atoms 增量）
        if (apiConfig != null) {
            val deltas = collectAllCharacterScenarioDeltas(context)
            if (deltas.isNotEmpty()) {
                runCatching {
                    L3PersonaUpdater.updateGlobal(context, apiConfig, deltas)
                }
            }
        }

        // 收尾：补 mermaid 闭合
        runCatching { ShortTermCanvas.ensureTrailingMermaidClose(context, characterId) }
    }

    private fun truncateCanvasIfTooLong(context: Context, characterId: Long, keepLines: Int) {
        val f = MemoryPaths.canvasFile(context, characterId)
        if (!f.exists()) return
        val lines = f.readLines()
        if (lines.size <= keepLines) return
        val head = lines.take(2) // 保留 graph LR 头
        val tail = lines.takeLast(keepLines)
        f.writeText((head + tail).joinToString("\n") + "\n```\n", Charsets.UTF_8)
    }

    private suspend fun collectAllCharacterScenarioDeltas(
        context: Context
    ): List<L3PersonaUpdater.CharacterScenarioDelta> {
        val out = ArrayList<L3PersonaUpdater.CharacterScenarioDelta>()
        val root = com.example.chatbot.memory.MemoryPaths.root(context)
        val chars = root.listFiles { f -> f.isDirectory && f.name.toLongOrNull() != null }
            ?: return emptyList()
        chars.forEach { dir ->
            val cid = dir.name.toLongOrNull() ?: return@forEach
            val scens = L2ScenarioClusterer.loadAllScenarios(context, cid)
            scens.take(3).forEach { s ->
                out.add(L3PersonaUpdater.CharacterScenarioDelta(cid, s.title, s.summary, s.id))
            }
        }
        return out
    }

    /** 删除某角色所有记忆文件 + 关闭 vec / canvas */
    fun resetCharacter(context: Context, characterId: Long) {
        scope.launch {
            runCatching {
                VecIndexFactory.reset(characterId)
                com.example.chatbot.memory.MemoryPaths.characterDir(context, characterId).deleteRecursively()
                L1AtomExtractor.reset(characterId)
            }
        }
    }

    /** 删除全部 */
    fun resetAll(context: Context) {
        scope.launch {
            runCatching {
                com.example.chatbot.memory.MemoryPaths.root(context).deleteRecursively()
                com.example.chatbot.memory.MemoryPaths.root(context).mkdirs()
            }
        }
    }

    fun isReady(): Boolean = initialized.get()
}
