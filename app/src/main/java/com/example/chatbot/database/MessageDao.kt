package com.example.chatbot.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.example.chatbot.data.model.Message
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE characterId = :characterId ORDER BY timestamp ASC")
    fun getMessagesByCharacterId(characterId: Long): Flow<List<Message>>

    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    fun getAllMessagesFlow(): Flow<List<Message>>

    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()

    @Insert
    suspend fun insertMessage(message: Message): Long

    @Query("UPDATE messages SET content = :content WHERE id = :id")
    suspend fun updateMessageContent(id: Long, content: String)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessageById(id: Long)

    @Delete
    suspend fun deleteMessage(message: Message)

    @Query("DELETE FROM messages WHERE characterId = :characterId")
    suspend fun deleteMessagesByCharacterId(characterId: Long)

    @Query("SELECT * FROM messages WHERE characterId = :characterId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastMessage(characterId: Long): Message?

    @Query(
        "SELECT * FROM messages WHERE characterId = :characterId ORDER BY timestamp DESC LIMIT :limit"
    )
    suspend fun getRecentMessagesDesc(characterId: Long, limit: Int): List<Message>

    @Query("SELECT COUNT(*) FROM messages WHERE characterId = :characterId")
    suspend fun getMessageCount(characterId: Long): Int

    @Query("SELECT * FROM messages WHERE characterId = :characterId ORDER BY timestamp ASC")
    suspend fun getAllMessagesByCharacterId(characterId: Long): List<Message>
}
