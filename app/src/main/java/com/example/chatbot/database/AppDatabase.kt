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
    version = 4,
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

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE messages ADD COLUMN status TEXT NOT NULL DEFAULT 'completed'"
                )
                database.execSQL(
                    "ALTER TABLE messages ADD COLUMN error TEXT NOT NULL DEFAULT ''"
                )
                database.execSQL(
                    "UPDATE messages SET status = 'failed', error = '回复中断，请重新发送。' " +
                            "WHERE isUser = 0 AND TRIM(content) = ''"
                )
            }
        }
    }
}
