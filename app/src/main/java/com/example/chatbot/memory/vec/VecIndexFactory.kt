package com.example.chatbot.memory.vec

import com.example.chatbot.memory.MemoryPaths
import android.content.Context
import com.example.chatbot.memory.MemoryConfig
import java.util.concurrent.ConcurrentHashMap

/**
 * 选 vec 索引的工厂。优先 sqlite-vec；不可用 → 线性扫描。
 *
 * 单角色独立索引（独立 sqlite 文件 + 独立 LinearScan 内存表）。
 */
object VecIndexFactory {

    private val byCharacter = ConcurrentHashMap<Long, VecIndex>()

    fun get(context: Context, characterId: Long): VecIndex {
        byCharacter[characterId]?.let { return it }
        synchronized(this) {
            byCharacter[characterId]?.let { return it }
            val ctx = context.applicationContext
            val dim = MemoryConfig.embedDim(ctx)
            val dbFile = MemoryPaths.atomsIndexDb(ctx, characterId)
            val sqlite = SqliteVecIndex(ctx, dbFile, dim)
            val pick: VecIndex = if (sqlite.isReady()) sqlite else {
                sqlite.close()
                LinearScanIndex(dim)
            }
            byCharacter[characterId] = pick
            return pick
        }
    }

    fun reset(characterId: Long) {
        byCharacter.remove(characterId)?.close()
    }

    /** dim 变更或用户主动清理时调用，清掉所有缓存并删掉所有角色索引文件 */
    fun resetAll() {
        byCharacter.values.forEach { runCatching { it.close() } }
        byCharacter.clear()
    }
}
