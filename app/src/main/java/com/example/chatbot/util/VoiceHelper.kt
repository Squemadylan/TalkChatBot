package com.example.chatbot.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import java.util.Locale
import java.util.UUID

/**
 * 语音助手工具类
 *
 * 回退链：豆包 TTS（在线）→ 系统 TTS（设备自带）
 * - 豆包火山引擎：无需设备授权，中文支持好，在线合成
 * - 系统 TTS：设备自带，完全免费，无需联网
 *
 * 使用方式：
 * VoiceHelper.init(context) { success -> ... }
 * VoiceHelper.speak(text) { ... }
 */
object VoiceHelper {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var isSpeaking = false
    private var currentEngine: String? = null
    private var initCallback: ((Boolean, String?) -> Unit)? = null
    private var volcTtsEnabled = false   // 豆包是否可用
    private var volcTtsReady = false      // 豆包是否初始化完成

    // 系统 TTS 引擎回退链（按品牌分组）
    private val enginePriorityMap = mapOf(
        // 华为/荣耀
        "HUAWEI" to listOf(
            "com.iflytek.speechengine",
            "com.iflytek.hwpstts",
            "com.huawei.dsds voiceassistant.tts",
            "com.huawei.tts",
            "com.google.android.tts"
        ),
        // 小米/红米
        "Xiaomi" to listOf(
            "com.miui.miSpeech",
            "com.miui.speechrecognition",
            "com.google.android.tts",
            "com.android.tts"
        ),
        // OPPO/Realme/一加
        "OPPO" to listOf(
            "com.coloros.speechservice",
            "com.heytap.mcs",
            "com.google.android.tts",
            "com.android.tts"
        ),
        // VIVO
        "VIVO" to listOf(
            "com.vivo.voicewakeup",
            "com.vivo.tts",
            "com.google.android.tts",
            "com.android.tts"
        ),
        // 努比亚/红魔（ZTE旗下）
        "NUBIA" to listOf(
            "com.zte.aichatbot.vivoceantts",
            "com.zte.zts",
            "com.google.android.tts",
            "com.android.tts"
        ),
        // 联想/摩托罗拉
        "LENOVO" to listOf(
            "com.lenovo.tts",
            "com.google.android.tts",
            "com.android.tts"
        ),
        // 三星
        "SAMSUNG" to listOf(
            "com.samsung.SVI.v2",
            "com.samsung.android.svoice",
            "com.google.android.tts",
            "com.android.tts"
        ),
        // 荣耀
        "HONOR" to listOf(
            "com.huawei.hiaiassistant.tts",
            "com.iflytek.speechengine",
            "com.google.android.tts",
            "com.android.tts"
        ),
        // 默认回退链
        "DEFAULT" to listOf(
            "com.google.android.tts",
            "com.android.tts"
        )
    )

    /**
     * 获取设备品牌（用于引擎选择策略）
     */
    private fun getDeviceBrand(): String {
        val brand = Build.BRAND.uppercase(Locale.getDefault())
        return when {
            brand.contains("HUAWEI") || brand.contains("HONOR") -> "HUAWEI"
            brand.contains("XIAOMI") || brand.contains("REDMI") || brand.contains("MI") -> "Xiaomi"
            brand.contains("OPPO") || brand.contains("REALME") || brand.contains("ONEPLUS") -> "OPPO"
            brand.contains("VIVO") -> "VIVO"
            brand.contains("NUBIA") || brand.contains("REDMAGIC") -> "NUBIA"
            brand.contains("LENOVO") || brand.contains("MOTOROLA") -> "LENOVO"
            brand.contains("SAMSUNG") -> "SAMSUNG"
            brand.contains("ZTE") -> "NUBIA"
            else -> brand.takeIf { it.isNotBlank() } ?: "DEFAULT"
        }
    }

    /**
     * 检查设备上已安装的 TTS 引擎
     */
    private fun getAvailableEngines(context: Context): List<String> {
        val packageManager = context.packageManager
        val intent = Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA).apply {
            setPackage(context.packageName)
        }
        val resolveInfos = packageManager.queryIntentServices(intent, PackageManager.MATCH_ALL)
        return resolveInfos.mapNotNull { it.serviceInfo?.packageName }.distinct()
    }

    /**
     * 初始化语音引擎
     * 回退链：豆包 TTS（在线）→ 系统 TTS（设备自带）
     */
    fun init(context: Context, onReady: (success: Boolean, error: String?) -> Unit) {
        if ((volcTtsReady && volcTtsEnabled) || tts != null) {
            onReady(true, null)
            return
        }
        initCallback = onReady

        // 优先级 1：豆包 TTS（在线，无需设备授权）
        VolcTtsHelper.init(context) { success, err ->
            if (success) {
                volcTtsReady = true
                volcTtsEnabled = true
                android.util.Log.d("VoiceHelper", "豆包TTS初始化成功")
                isInitialized = true
                initCallback?.invoke(true, null)
                initCallback = null
            } else {
                android.util.Log.w("VoiceHelper", "豆包TTS初始化失败: $err，fallback到系统TTS")
                volcTtsEnabled = false
                // 优先级 2：系统 TTS（设备自带）
                initSystemTts(context)
            }
        }
    }

    private fun initSystemTts(context: Context) {
        val brand = getDeviceBrand()
        val engineList = enginePriorityMap[brand] ?: enginePriorityMap["DEFAULT"] ?: emptyList()
        val available = getAvailableEngines(context)
        android.util.Log.d("VoiceHelper", "Brand: $brand, Available engines: $available")

        val targetEngine = engineList.firstOrNull { enginePackage ->
            available.any { it.equals(enginePackage, ignoreCase = true) }
        } ?: available.firstOrNull()

        if (targetEngine == null) {
            attemptInit(context, null)
        } else {
            attemptInit(context, targetEngine)
        }
    }

    private fun attemptInit(context: Context, enginePackage: String?) {
        try {
            tts?.shutdown()
            tts = null

            val listener = TextToSpeech.OnInitListener { status ->
                isInitialized = status == TextToSpeech.SUCCESS
                if (isInitialized) {
                    currentEngine = enginePackage
                    val langResult = tts?.setLanguage(Locale.CHINA)
                    android.util.Log.d("VoiceHelper", "TTS init success, engine: $currentEngine, lang result: $langResult")
                    if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                        android.util.Log.w("VoiceHelper", "Chinese language not supported, falling back to default")
                        tts?.setLanguage(Locale.getDefault())
                    }
                    initCallback?.invoke(true, null)
                } else {
                    val errorMsg = "TTS 初始化失败，请到系统设置中安装语音引擎"
                    android.util.Log.e("VoiceHelper", errorMsg)
                    initCallback?.invoke(false, errorMsg)
                }
                initCallback = null
            }

            tts = if (enginePackage != null) {
                android.util.Log.d("VoiceHelper", "Trying engine: $enginePackage")
                TextToSpeech(context.applicationContext, listener, enginePackage)
            } else {
                TextToSpeech(context.applicationContext, listener)
            }
        } catch (e: Exception) {
            android.util.Log.e("VoiceHelper", "Exception during TTS init", e)
            initCallback?.invoke(false, "TTS 初始化异常：${e.message}")
            initCallback = null
        }
    }

    /**
     * 朗读文本，回退链：豆包 TTS → 系统 TTS
     */
    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (text.isBlank()) {
            onDone?.invoke()
            return
        }
        stop()
        isSpeaking = true

        if (volcTtsEnabled && volcTtsReady) {
            android.util.Log.d("VoiceHelper", "Using VolcTtsHelper to speak")
            VolcTtsHelper.speak(text) {
                isSpeaking = false
                onDone?.invoke()
            }
        } else {
            speakWithSystemTts(text, onDone)
        }
    }

    private fun speakWithSystemTts(text: String, onDone: (() -> Unit)?) {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                android.util.Log.d("VoiceHelper", "TTS started: $utteranceId")
            }

            override fun onDone(utteranceId: String?) {
                isSpeaking = false
                android.util.Log.d("VoiceHelper", "TTS finished")
                onDone?.invoke()
            }

            @Deprecated("Deprecated in API")
            override fun onError(utteranceId: String?) {
                isSpeaking = false
                android.util.Log.e("VoiceHelper", "TTS error: $utteranceId")
                onDone?.invoke()
            }
        })

        val utteranceId = UUID.randomUUID().toString()
        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        android.util.Log.d("VoiceHelper", "speak() returned: $result")
    }

    /**
     * 停止朗读
     */
    fun stop() {
        try {
            tts?.stop()
            isSpeaking = false
        } catch (e: Exception) {
            android.util.Log.e("VoiceHelper", "stop() failed", e)
        }
        try { VolcTtsHelper.stop() } catch (_: Exception) {}
    }

    /**
     * 设置语速
     * @param speed 0.5 ~ 2.0，1.0 为正常速度
     */
    fun setSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        tts?.setSpeechRate(clamped)
        VolcTtsHelper.currentSpeed = clamped
    }

    /**
     * 设置音调
     * @param pitch 0.5 ~ 2.0，1.0 为正常音调
     */
    fun setPitch(pitch: Float) {
        val clamped = pitch.coerceIn(0.5f, 2.0f)
        tts?.setPitch(clamped)
        VolcTtsHelper.currentPitch = clamped
    }

    /**
     * 是否正在朗读中
     */
    fun isSpeaking(): Boolean = isSpeaking

    /**
     * 获取当前使用的引擎名称
     */
    fun getCurrentEngine(): String? = currentEngine

    /**
     * 检查 TTS 数据是否完整
     */
    fun checkTtsData完整性(context: Context, onResult: (Boolean) -> Unit) {
        if (!isInitialized) {
            onResult(false)
            return
        }
        val intent = Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA)
        try {
            context.startActivity(intent)
            onResult(true)
        } catch (e: Exception) {
            android.util.Log.e("VoiceHelper", "checkTtsData failed", e)
            onResult(false)
        }
    }

    /**
     * 跳转到系统 TTS 设置页面
     */
    fun openTtsSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("VoiceHelper", "openTtsSettings failed", e)
            Toast.makeText(context, "请在系统设置中查找「语音输入」或「文字转语音」选项", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * 释放 TTS 资源
     */
    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
            isSpeaking = false
            currentEngine = null
        } catch (e: Exception) {
            android.util.Log.e("VoiceHelper", "shutdown() failed", e)
        }
    }

    /**
     * 创建语音识别 Intent
     */
    fun createSpeechRecognizerIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.SIMPLIFIED_CHINESE)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "请说话")
        }
    }
}