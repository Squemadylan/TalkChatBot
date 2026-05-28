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
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun characterDao(): CharacterDao
    abstract fun messageDao(): MessageDao
    abstract fun apiConfigDao(): ApiConfigDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE characters ADD COLUMN enableLongTermMemory INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE messages ADD COLUMN status TEXT NOT NULL DEFAULT 'completed'"
                )
                db.execSQL(
                    "ALTER TABLE messages ADD COLUMN error TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "UPDATE messages SET status = 'failed', error = '回复中断，请重新发送。' " +
                            "WHERE isUser = 0 AND TRIM(content) = ''"
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Message: isStarred, starredAt
                db.execSQL("ALTER TABLE messages ADD COLUMN isStarred INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE messages ADD COLUMN starredAt INTEGER")
                // Character: enableAutoRead, voiceSpeed, voiceLanguage
                db.execSQL("ALTER TABLE characters ADD COLUMN enableAutoRead INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE characters ADD COLUMN voiceSpeed REAL NOT NULL DEFAULT 1.0")
                db.execSQL("ALTER TABLE characters ADD COLUMN voiceLanguage TEXT NOT NULL DEFAULT 'zh-CN'")
            }
        }
    }
}
