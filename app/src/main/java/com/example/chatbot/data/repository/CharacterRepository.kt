package com.example.chatbot.data.repository

import com.example.chatbot.database.CharacterDao
import com.example.chatbot.data.model.Character
import kotlinx.coroutines.flow.Flow

class CharacterRepository(private val characterDao: CharacterDao) {
    val allCharacters: Flow<List<Character>> = characterDao.getAllCharacters()

    suspend fun insertCharacter(character: Character) {
        characterDao.insertCharacter(character)
    }

    suspend fun updateCharacter(character: Character) {
        characterDao.updateCharacter(character)
    }

    suspend fun deleteCharacter(character: Character) {
        characterDao.deleteCharacter(character)
    }

    suspend fun getCharacterById(id: Long): Character? {
        return characterDao.getCharacterById(id)
    }

    suspend fun deleteCharacterById(id: Long) {
        characterDao.deleteCharacterById(id)
    }
}
