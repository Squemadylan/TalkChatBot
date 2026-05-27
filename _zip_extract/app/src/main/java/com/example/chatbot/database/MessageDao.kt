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

    @Insert
    suspend fun insertMessage(message: Message)

    @Delete
    suspend fun deleteMessage(message: Message)

    @Query("DELETE FROM messages WHERE characterId = :characterId")
    suspend fun deleteMessagesByCharacterId(characterId: Long)

    @Query("SELECT * FROM messages WHERE characterId = :characterId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastMessage(characterId: Long): Message?
}
