package com.example.chatbot.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatbot.data.repository.CharacterRepository
import com.example.chatbot.data.model.Character
import kotlinx.coroutines.launch

class CharacterViewModel(private val repository: CharacterRepository) : ViewModel() {
    val allCharacters = repository.allCharacters

    fun insertCharacter(character: Character) = viewModelScope.launch {
        repository.insertCharacter(character)
    }

    fun updateCharacter(character: Character) = viewModelScope.launch {
        repository.updateCharacter(character)
    }

    fun deleteCharacter(character: Character) = viewModelScope.launch {
        repository.deleteCharacter(character)
    }

    suspend fun getCharacterById(id: Long): Character? {
        return repository.getCharacterById(id)
    }
}
