package com.example.chatbot.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.chatbot.data.repository.ApiConfigRepository
import com.example.chatbot.data.model.ApiConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ApiConfigViewModel(private val repository: ApiConfigRepository) : ViewModel() {

    private val _apiConfig = MutableLiveData<ApiConfig?>(null)
    val apiConfig: LiveData<ApiConfig?> = _apiConfig

    init {
        loadConfig()
    }

    fun loadConfig() = viewModelScope.launch(Dispatchers.IO) {
        _apiConfig.postValue(repository.getApiConfig())
    }

    fun saveConfig(config: ApiConfig) = viewModelScope.launch(Dispatchers.IO) {
        val existing = repository.getApiConfig()
        if (existing == null) {
            repository.insertApiConfig(config)
        } else {
            repository.updateApiConfig(config)
        }
        _apiConfig.postValue(config)
    }
}

class ApiConfigViewModelFactory(private val repository: ApiConfigRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ApiConfigViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ApiConfigViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
