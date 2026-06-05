package com.example.chatbot.memory.embed

/**
 * 把一段文本映射到一个 Float 向量。本地实现：ONNX Runtime + bge-small-zh。
 * 任何不可用时（模型缺失、加载失败、内存压力）都可以回退到 [Bm25PlaceholderEmbedder]，
 * 召回器会自动选择"纯 BM25"路径，整体链路仍能工作。
 */
interface LocalEmbedder {

    /** 模型可加载、跑通一个空字符串测试即视为就绪 */
    fun isReady(): Boolean

    /** 模型向量维度 */
    fun dim(): Int

    /** 同步编码；线程安全（实现方负责） */
    fun encode(text: String): FloatArray

    /** 关闭底层 session，释放 native 内存 */
    fun close()
}
