package com.example.chatbot.ui.common

/**
 * 一次性 UI 事件包装，避免 LiveData 在配置变更后重复消费。
 */
class Event<out T>(private val content: T) {

    private var handled = false

    fun getContentIfNotHandled(): T? {
        if (handled) return null
        handled = true
        return content
    }
}
