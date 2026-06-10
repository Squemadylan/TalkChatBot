package com.example.chatbot.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.example.chatbot.R

/**
 * 记忆向量 API 首次失败时提示用户检查配置（仅 Toast 一次）。
 */
object EmbeddingFailureNotifier {

    private const val PREFS = "embed_failure_prefs"
    private const val KEY_TOAST_SHOWN = "first_failure_toast_shown"

    private val mainHandler = Handler(Looper.getMainLooper())

    fun notifyOnce(context: Context) {
        val appCtx = context.applicationContext
        val prefs = appCtx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_TOAST_SHOWN, false)) return
        prefs.edit().putBoolean(KEY_TOAST_SHOWN, true).apply()
        mainHandler.post {
            Toast.makeText(
                appCtx,
                appCtx.getString(R.string.config_embed_failure_toast),
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
