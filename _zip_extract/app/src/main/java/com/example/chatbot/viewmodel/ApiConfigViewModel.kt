package com.example.chatbot.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.chatbot.data.repository.ApiConfigRepository
import com.example.chatbot.data.model.ApiConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ApiConfigViewModel(private val repository: ApiConfigRepository) : ViewModel() {

    private val _apiConfig = MutableStateFlow<ApiConfig?>(null)
    val apiConfig: StateFlow<ApiConfig?> = _apiConfig

    init {
        loadConfig()
    }

    fun loadConfig() = viewModelScope.launch(Dispatchers.IO) {
        _apiConfig.value = repository.getApiConfig()
    }

    fun saveConfig(config: ApiConfig) = viewModelScope.launch(Dispatchers.IO) {
        val existing = repository.getApiConfig()
        if (existing == null) {
            repository.insertApiConfig(config)
        } else {
            repository.updateApiConfig(config)
        }
        _apiConfig.value = config
    }
}
