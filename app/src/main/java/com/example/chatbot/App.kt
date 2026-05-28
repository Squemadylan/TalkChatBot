package com.example.chatbot

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.room.Room
import com.example.chatbot.database.AppDatabase
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class App : Application(), Thread.UncaughtExceptionHandler {

    private var _database: AppDatabase? = null
    val database: AppDatabase
        get() = _database ?: throw IllegalStateException("Database not initialized. App may be in an unstable state.")

    private var isInitializing = false

    private var systemDefaultExceptionHandler: Thread.UncaughtExceptionHandler? = null

    override fun onCreate() {
        super.onCreate()
        applySavedNightMode()
        instance = this
        systemDefaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
        initializeDatabase()
    }

    private fun applySavedNightMode() {
        val mode = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_YES)
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    private fun initializeDatabase() {
        try {
            isInitializing = true
            _database = Room.databaseBuilder(
                applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(AppDatabase.MIGRATION_2_3)
                .addMigrations(AppDatabase.MIGRATION_3_4)
                .addMigrations(AppDatabase.MIGRATION_4_5)
                .build()
            isInitializing = false
            Log.d(TAG, "Database initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize database", e)
            _database = null
            isInitializing = false
        }
    }

    /** 仅当 Room 已就绪返回 true（初始化中未就绪时返回 false，避免误用 [database] 崩溃） */
    fun isDatabaseInitialized(): Boolean = _database != null

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        Log.e(TAG, "Uncaught exception in thread ${thread.name}", throwable)
        throwable.printStackTrace()
        persistCrashLog(thread.name, throwable)
        val delegate = systemDefaultExceptionHandler
        if (delegate != null) {
            delegate.uncaughtException(thread, throwable)
        } else {
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(1)
        }
    }

    private fun persistCrashLog(threadName: String, throwable: Throwable) {
        runCatching {
            val writer = StringWriter()
            PrintWriter(writer).use { pw ->
                pw.println("time=" + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
                pw.println("thread=$threadName")
                throwable.printStackTrace(pw)
            }
            File(filesDir, "last_crash.txt").writeText(writer.toString())
        }.onFailure { e ->
            Log.e(TAG, "Failed to write crash log", e)
        }
    }

    companion object {
        private const val TAG = "ChatBotApp"
        private const val DATABASE_NAME = "chatbot_db"
        const val PREFS_NAME = "chatbot_prefs"
        const val KEY_NIGHT_MODE = "night_mode"
        const val KEY_USER_AVATAR_PATH = "user_avatar_path"
        const val KEY_CHAT_BACKGROUND_PATH = "chat_background_path"
        /** 带入 API 的历史消息条数（0–10），不含本次用户句则取最近 N 条含本轮 */
        const val KEY_MEMORY_CONTEXT_COUNT = "memory_context_count"
        const val KEY_CHAT_SHOW_AVATARS = "chat_show_avatars"
        /** 个人设置：聊天中「用户」显示名，用于替换角色卡里的 {{user}} 等占位符 */
        const val KEY_USER_DISPLAY_NAME = "user_display_name"
        /** 个人设置：用户人设，可替换 {{persona}} 等 */
        const val KEY_USER_PERSONA = "user_persona"
        /** 聊天气泡外观：默认 / 紧凑 / 圆角 / 半透明 */
        const val KEY_CHAT_BUBBLE_STYLE = "chat_bubble_style"
        /** 回复策略：标准 / 短回复 / 细腻 */
        const val KEY_REPLY_STYLE = "reply_style"
        /** 状态栏是否透明沉浸，图标颜色跟随当前主题 */
        const val KEY_STATUS_BAR_IMMERSIVE = "status_bar_immersive"
    const val KEY_VOICE_SPEED = "voice_speed"
    const val KEY_VOICE_LANGUAGE = "voice_language"

        const val CHAT_BUBBLE_STYLE_DEFAULT = 0
        const val CHAT_BUBBLE_STYLE_COMPACT = 1
        const val CHAT_BUBBLE_STYLE_ROUNDED = 2
        const val CHAT_BUBBLE_STYLE_TRANSLUCENT = 3

        const val REPLY_STYLE_STANDARD = 0
        const val REPLY_STYLE_SHORT = 1
        const val REPLY_STYLE_DETAILED = 2

        @Volatile
        private var instance: App? = null

        fun getInstance(): App {
            return instance ?: throw IllegalStateException("Application not initialized")
        }
    }
}
