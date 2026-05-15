package com.example.chatbot.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.chatbot.data.repository.CharacterRepository
import com.example.chatbot.data.model.Character
import kotlinx.coroutines.launch

class CharacterViewModel(private val repository: CharacterRepository) : ViewModel() {
    val allCharacters: LiveData<List<Character>> = repository.allCharacters.asLiveData()

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

class CharacterViewModelFactory(private val repository: CharacterRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CharacterViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CharacterViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
