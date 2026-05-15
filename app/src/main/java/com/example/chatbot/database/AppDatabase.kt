package com.example.chatbot.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.chatbot.data.model.ApiConfig
import com.example.chatbot.data.model.Character
import com.example.chatbot.data.model.Message

@Database(
    entities = [Character::class, Message::class, ApiConfig::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun characterDao(): CharacterDao
    abstract fun messageDao(): MessageDao
    abstract fun apiConfigDao(): ApiConfigDao
}
