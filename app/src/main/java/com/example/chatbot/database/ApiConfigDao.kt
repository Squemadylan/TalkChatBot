package com.example.chatbot.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.chatbot.data.model.ApiConfig

@Dao
interface ApiConfigDao {
    @Query("SELECT * FROM api_config WHERE id = 1")
    suspend fun getApiConfig(): ApiConfig?

    @Upsert
    suspend fun upsertApiConfig(config: ApiConfig)
}
