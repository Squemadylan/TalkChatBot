package com.example.chatbot.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.chatbot.data.model.ApiConfig
import com.example.chatbot.data.model.Character
import com.example.chatbot.data.model.Message

@Database(
    entities = [Character::class, Message::class, ApiConfig::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun characterDao(): CharacterDao
    abstract fun messageDao(): MessageDao
    abstract fun apiConfigDao(): ApiConfigDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE characters ADD COLUMN enableLongTermMemory INTEGER NOT NULL DEFAULT 0"
                )
            }
        }
    }
}
