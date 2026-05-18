package com.example.chatbot.data.repository

import com.example.chatbot.database.MessageDao
import com.example.chatbot.data.model.Message
import kotlinx.coroutines.flow.Flow

class MessageRepository(private val messageDao: MessageDao) {
    fun getMessagesByCharacterId(characterId: Long): Flow<List<Message>> {
        return messageDao.getMessagesByCharacterId(characterId)
    }

    fun getAllMessagesFlow(): Flow<List<Message>> = messageDao.getAllMessagesFlow()

    suspend fun deleteAllMessages() {
        messageDao.deleteAllMessages()
    }

    suspend fun insertMessage(message: Message): Long {
        return messageDao.insertMessage(message)
    }

    suspend fun updateMessageContent(id: Long, content: String) {
        messageDao.updateMessageContent(id, content)
    }

    suspend fun deleteMessageById(id: Long) {
        messageDao.deleteMessageById(id)
    }

    suspend fun deleteMessagesByCharacterId(characterId: Long) {
        messageDao.deleteMessagesByCharacterId(characterId)
    }

    /** 时间正序的最近 [limit] 条（含刚写入库的最新一条） */
    suspend fun getRecentMessagesChronological(characterId: Long, limit: Int): List<Message> {
        if (limit <= 0) return emptyList()
        return messageDao.getRecentMessagesDesc(characterId, limit).reversed()
    }

    /** 获取该角色的所有对话历史（时间正序） */
    suspend fun getAllMessagesByCharacterId(characterId: Long): List<Message> {
        return messageDao.getAllMessagesByCharacterId(characterId)
    }
}
