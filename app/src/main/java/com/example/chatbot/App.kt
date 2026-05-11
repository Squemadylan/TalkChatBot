package com.example.chatbot

import android.app.Application
import android.util.Log
import androidx.room.Room
import com.example.chatbot.database.AppDatabase

class App : Application() {

    private var _database: AppDatabase? = null
    val database: AppDatabase
        get() = _database ?: throw IllegalStateException("Database not initialized. App may be in an unstable state.")

    override fun onCreate() {
        super.onCreate()
        instance = this
        initializeDatabase()
    }

    private fun initializeDatabase() {
        try {
            _database = Room.databaseBuilder(
                applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .fallbackToDestructiveMigration()
                .build()
            Log.d(TAG, "Database initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize database", e)
            _database = null
        }
    }

    fun isDatabaseInitialized(): Boolean {
        return _database != null
    }

    companion object {
        private const val TAG = "ChatBotApp"
        private const val DATABASE_NAME = "chatbot_db"

        @Volatile
        private var instance: App? = null

        fun getInstance(): App {
            return instance ?: throw IllegalStateException("Application not initialized")
        }
    }
}
