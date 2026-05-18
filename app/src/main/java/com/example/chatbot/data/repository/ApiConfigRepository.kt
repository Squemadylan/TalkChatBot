package com.example.chatbot.data.repository

import com.example.chatbot.database.ApiConfigDao
import com.example.chatbot.data.model.ApiConfig

class ApiConfigRepository(private val apiConfigDao: ApiConfigDao) {
    suspend fun getApiConfig(): ApiConfig? {
        return apiConfigDao.getApiConfig()
    }

    suspend fun saveApiConfig(config: ApiConfig) {
        apiConfigDao.upsertApiConfig(config)
    }
}
