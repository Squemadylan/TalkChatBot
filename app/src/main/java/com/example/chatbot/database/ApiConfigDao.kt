package com.example.chatbot.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.chatbot.data.model.ApiConfig

@Dao
interface ApiConfigDao {
    @Query("SELECT * FROM api_config WHERE id = 1")
    suspend fun getApiConfig(): ApiConfig?

    @Insert
    suspend fun insertApiConfig(config: ApiConfig)

    @Update
    suspend fun updateApiConfig(config: ApiConfig)
}
