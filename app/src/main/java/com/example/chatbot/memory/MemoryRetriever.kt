package com.example.chatbot.memory

import android.content.Context
import com.example.chatbot.memory.embed.EmbedderFactory
import com.example.chatbot.memory.layers.L2ScenarioClusterer
import com.example.chatbot.memory.vec.VecIndexFactory

/**
 * 召回器：把 BM25（关键词命中）和向量 KNN 用 RRF 融合。
 *
 * - BM25 简化版：atom 文本与 query 的 token 重合数 / sqrt(atom 长度)
 * - 向量：本地 embedder 编码后调 VecIndex
 * - RRF: score(d) = Σ 1 / (k + rank_i(d))，k = 60
 */
object MemoryRetriever {

    private const val RRF_K = 60.0

    data class Hit(val atom: Atom, val score: Double, val source: String)

    fun recallAtoms(
        context: Context,
        characterId: Long,
        query: String,
        topK: Int = MemoryConfig.recallTopAtoms(context),
        maxCharsPerAtom: Int = MemoryConfig.recallMaxCharsPerAtom(context)
    ): List<Hit> {
        if (query.isBlank() || topK <= 0) return emptyList()
        val file = MemoryPaths.atomsJsonl(context, characterId)
        if (!file.exists()) return emptyList()
        val atoms = AtomJsonl.readAll(file)
        if (atoms.isEmpty()) return emptyList()

        val qTokens = tokenize(query).toSet()
        val bm25Rank = HashMap<String, Int>()
        val bm25Score = HashMap<String, Double>()
        atoms.forEach { a ->
            val toks = tokenize(a.text)
            val overlap = toks.count { it in qTokens }
            if (overlap > 0) {
                bm25Score[a.id] = overlap.toDouble() / kotlin.math.sqrt(toks.size.toDouble())
            }
        }
        val bm25Sorted = atoms
            .filter { (bm25Score[it.id] ?: 0.0) > 0.0 }
            .sortedByDescending { bm25Score[it.id] ?: 0.0 }
        bm25Sorted.forEachIndexed { idx, a -> bm25Rank[a.id] = idx + 1 }

        val vecRank = HashMap<String, Int>()
        runCatching {
            val embedder = EmbedderFactory.get(context)
            val expectedDim = embedder.dim()
            val vec = VecIndexFactory.get(context, characterId)
            // dim 不一致（例如刚切远程 1024d，但旧 atoms_index.sqlite 还是 512d）：
            // 重置 vec 索引让 SqliteVecIndex 重新按当前 dim 建表 + 量化。
            if (vec.dim != expectedDim) {
                VecIndexFactory.reset(characterId)
            }
            val qv = embedder.encode(query)
            val vec2 = VecIndexFactory.get(context, characterId)
            val matches = vec2.search(qv, topK * 3)
            matches.forEachIndexed { idx, m -> vecRank[m.id] = idx + 1 }
        }

        val rrf = HashMap<String, Double>()
        bm25Rank.forEach { (id, r) -> rrf[id] = (rrf[id] ?: 0.0) + 1.0 / (RRF_K + r) }
        vecRank.forEach { (id, r) -> rrf[id] = (rrf[id] ?: 0.0) + 1.0 / (RRF_K + r) }
        val rankedIds = rrf.entries.sortedByDescending { it.value }.take(topK).map { it.key }
        if (rankedIds.isEmpty()) return emptyList()
        val byId = atoms.associateBy { it.id }
        return rankedIds.mapNotNull { id ->
            val atom = byId[id] ?: return@mapNotNull null
            val text = if (maxCharsPerAtom > 0 && atom.text.length > maxCharsPerAtom) {
                atom.text.take(maxCharsPerAtom) + "…"
            } else atom.text
            Hit(
                atom = atom.copy(text = text),
                score = rrf[id] ?: 0.0,
                source = listOfNotNull(
                    if (bm25Rank.containsKey(id)) "BM25" else null,
                    if (vecRank.containsKey(id)) "VEC" else null
                ).joinToString("+")
            )
        }
    }

    fun recallScenarios(
        context: Context,
        characterId: Long,
        query: String,
        topK: Int = MemoryConfig.recallTopScenarios(context)
    ): List<L2ScenarioClusterer.ScenarioLite> {
        if (topK <= 0 || query.isBlank()) return emptyList()
        val all = L2ScenarioClusterer.loadAllScenarios(context, characterId)
        if (all.isEmpty()) return emptyList()
        val q = tokenize(query).toSet()
        return all.map { s ->
            val toks = tokenize(s.title + " " + s.summary)
            val overlap = toks.count { it in q }
            s to overlap.toDouble() / kotlin.math.sqrt(toks.size.coerceAtLeast(1).toDouble())
        }
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .take(topK)
            .map { L2ScenarioClusterer.ScenarioLite(it.first) }
    }

    private fun tokenize(text: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        fun flush() { if (sb.isNotEmpty()) { out.add(sb.toString().lowercase()); sb.clear() } }
        for (c in text) {
            when {
                c.isWhitespace() -> flush()
                c.code in 0x4E00..0x9FFF -> { flush(); out.add(c.toString()) }
                c.isLetterOrDigit() -> sb.append(c)
                else -> flush()
            }
        }
        flush()
        return out
    }
}
