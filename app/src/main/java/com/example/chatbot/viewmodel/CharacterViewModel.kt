package com.example.chatbot.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.chatbot.data.model.Character
import com.example.chatbot.data.repository.CharacterRepository
import com.example.chatbot.data.repository.MessageRepository
import com.example.chatbot.util.LongTermMemoryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CharacterViewModel(
    private val characterRepository: CharacterRepository,
    private val messageRepository: MessageRepository
) : ViewModel() {
    val allCharacters: LiveData<List<Character>> = characterRepository.allCharacters.asLiveData()

    fun insertCharacter(character: Character) = viewModelScope.launch {
        characterRepository.insertCharacter(character)
    }

    fun updateCharacter(character: Character) = viewModelScope.launch {
        characterRepository.updateCharacter(character)
    }

    fun deleteCharacter(character: Character) = viewModelScope.launch {
        characterRepository.deleteCharacter(character)
    }

    fun deleteCharacterWithMessages(characterId: Long, context: Context? = null) {
        val appCtx = context?.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            messageRepository.deleteMessagesByCharacterId(characterId)
            characterRepository.deleteCharacterById(characterId)
            appCtx?.let {
                try {
                    LongTermMemoryManager.deleteMemory(it, characterId)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to delete memory", e)
                }
            }
        }
    }

    suspend fun getCharacterById(id: Long): Character? {
        return characterRepository.getCharacterById(id)
    }

    companion object {
        private const val TAG = "CharacterViewModel"
    }
}

class CharacterViewModelFactory(
    private val characterRepository: CharacterRepository,
    private val messageRepository: MessageRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CharacterViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CharacterViewModel(characterRepository, messageRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
