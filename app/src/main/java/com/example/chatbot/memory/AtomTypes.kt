package com.example.chatbot.memory

/** L1 原子事实类型 */
enum class AtomType(val keyword: String) {
    /** 客观事实：用户/角色养了只猫，住在上海等 */
    FACT("fact"),
    /** 偏好：喜欢/不喜欢某物 */
    PREFERENCE("preference"),
    /** 事件：发生过的某件事 */
    EVENT("event"),
    /** 情绪：当时感受到的情绪 */
    EMOTION("emotion");

    companion object {
        fun fromKeyword(s: String?): AtomType =
            values().firstOrNull { it.keyword.equals(s, ignoreCase = true) } ?: FACT
    }
}

/** 主体：指向谁 */
enum class AtomSubject(val keyword: String) {
    USER("user"),
    CHARACTER("character"),
    THIRD_PARTY("third_party"),
    UNKNOWN("unknown");

    companion object {
        fun fromKeyword(s: String?): AtomSubject =
            values().firstOrNull { it.keyword.equals(s, ignoreCase = true) } ?: UNKNOWN
    }
}
