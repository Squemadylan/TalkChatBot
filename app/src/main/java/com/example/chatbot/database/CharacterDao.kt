package com.example.chatbot.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.chatbot.data.model.Character
import kotlinx.coroutines.flow.Flow

@Dao
interface CharacterDao {
    @Query("SELECT * FROM characters ORDER BY createdAt DESC")
    fun getAllCharacters(): Flow<List<Character>>

    @Query("SELECT * FROM characters ORDER BY createdAt DESC")
    suspend fun getAllCharactersOnce(): List<Character>

    @Query("SELECT * FROM characters WHERE id = :id")
    suspend fun getCharacterById(id: Long): Character?

    @Insert
    suspend fun insertCharacter(character: Character): Long

    @Update
    suspend fun updateCharacter(character: Character)

    @Delete
    suspend fun deleteCharacter(character: Character)

    @Query("DELETE FROM characters WHERE id = :id")
    suspend fun deleteCharacterById(id: Long)
}
