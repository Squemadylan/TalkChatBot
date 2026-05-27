package com.example.chatbot.data.repository

import com.example.chatbot.database.MessageDao
import com.example.chatbot.data.model.Message
import kotlinx.coroutines.flow.Flow

class MessageRepository(private val messageDao: MessageDao) {
    fun getMessagesByCharacterId(characterId: Long): Flow<List<Message>> {
        return messageDao.getMessagesByCharacterId(characterId)
    }

    suspend fun insertMessage(message: Message) {
        messageDao.insertMessage(message)
    }

    suspend fun deleteMessagesByCharacterId(characterId: Long) {
        messageDao.deleteMessagesByCharacterId(characterId)
    }
}
