package com.example.chatbot.ui.chat

import com.example.chatbot.data.model.Character
import com.example.chatbot.data.model.Message

data class MemoryHubRow(
    val character: Character,
    val lastMessage: Message?
)
