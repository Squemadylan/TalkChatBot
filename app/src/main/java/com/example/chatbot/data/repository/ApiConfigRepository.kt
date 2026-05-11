package com.example.chatbot.data.repository

import com.example.chatbot.database.ApiConfigDao
import com.example.chatbot.data.model.ApiConfig

class ApiConfigRepository(private val apiConfigDao: ApiConfigDao) {
    suspend fun getApiConfig(): ApiConfig? {
        return apiConfigDao.getApiConfig()
    }

    suspend fun insertApiConfig(config: ApiConfig) {
        apiConfigDao.insertApiConfig(config)
    }

    suspend fun updateApiConfig(config: ApiConfig) {
        apiConfigDao.updateApiConfig(config)
    }
}
