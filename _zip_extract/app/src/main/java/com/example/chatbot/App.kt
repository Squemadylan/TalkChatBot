package com.example.chatbot

import android.app.Application
import androidx.room.Room
import com.example.chatbot.database.AppDatabase

class App : Application() {
    lateinit var database: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "chatbot_db"
        ).build()
    }
}
