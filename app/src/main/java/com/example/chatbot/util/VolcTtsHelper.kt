package com.example.chatbot.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 火山引擎豆包 TTS 助手（在线合成，纯 HTTP API 调用）
 *
 * 授权信息：
 * - APP ID: 2216356170
 * - Access Token: i4VxCRlg7_kexAO_LDTrDafV8M-jrZLx
 *
 * 使用豆包 HTTP TTS API，PCM 24kHz 单声道，中文语音合成。
 */

/**
 * 火山引擎豆包 TTS 助手（在线合成）
 *
 * 授权信息：
 * - APP ID: 2216356170
 * - Access Token: i4VxCRlg7_kexAO_LDTrDafV8M-jrZLx
 * - Secret Key: QkKAirXyGYVZM67-4ITsx4kpKvlpcVnD
 *
 * 使用 HTTP API 直接调用豆包 TTS 服务，支持中文语音合成。
 * 免费额度（需到火山引擎控制台确认具体用量）。
 */
object VolcTtsHelper {

    private const val TAG = "VolcTtsHelper"
    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()

    // 豆包 TTS HTTP API
    private const val TTS_URL = "https://openspeech.bytedance.com/api/v1/tts"
    // 文本长度限制（豆包 TTS API 单次最大支持约 500 字符）
    private const val MAX_TEXT_LEN = 480

    // 采样率 24000Hz（豆包默认）
    private const val SAMPLE_RATE = 24000
    private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
    private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

    private var audioTrack: AudioTrack? = null
    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false
    private var isInitialized = false
    private var initCallback: ((Boolean, String?) -> Unit)? = null
    private var playbackHandler: Handler? = null
    private var playbackThread: Thread? = null
    private var pendingPCMData = mutableListOf<Byte>()
    private var onSpeakDone: (() -> Unit)? = null
    private lateinit var context: Context

    // 朗读参数
    var currentVcn = "BV700_streaming"   // 默认音色（中文青年女声）
    var currentSpeed = 1.0f              // 语速：0.5~2.0
    var currentVolume = 1.0f            // 音量：0.5~2.0
    var currentPitch = 1.0f             // 音调：0.5~2.0

    // 认证信息
    private const val APP_ID = "2216356170"
    private const val ACCESS_TOKEN = "i4VxCRlg7_kexAO_LDTrDafV8M-jrZLx"

    /**
     * 初始化（实际上就是启动播放线程）
     */
    fun init(ctx: Context, onReady: (success: Boolean, error: String?) -> Unit) {
        if (isInitialized) {
            onReady(true, null)
            return
        }
        context = ctx
        initCallback = onReady
        startPlaybackThread()
        isInitialized = true
        Log.d(TAG, "VolcTtsHelper initialized")
        onReady(true, null)
    }

    /**
     * 合成并朗读文本（通过豆包 HTTP TTS API）
     */
    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (!isInitialized) {
            Log.e(TAG, "VolcTtsHelper not initialized")
            onDone?.invoke()
            return
        }
        if (text.isBlank()) {
            onDone?.invoke()
            return
        }
        stopPlayback()
        onSpeakDone = onDone

        // 在子线程发起 HTTP 请求
        Thread {
            try {
                // 如果文本过长，截断到最大长度
                val safeText = if (text.length > MAX_TEXT_LEN) {
                    text.substring(0, MAX_TEXT_LEN)
                } else {
                    text
                }
                val audioData = synthesize(safeText)
                if (audioData != null && audioData.isNotEmpty()) {
                    // 保存到临时文件
                    val tempFile = File(context.cacheDir, "tts_temp.mp3")
                    FileOutputStream(tempFile).use { fos ->
                        fos.write(audioData)
                    }
                    // 使用 MediaPlayer 播放 MP3
                    playbackHandler?.sendMessage(playbackHandler?.obtainMessage(MSG_PLAY_MP3, tempFile.absolutePath)!!)
                } else {
                    Log.e(TAG, "合成结果为空")
                    onSpeakDone?.invoke()
                    onSpeakDone = null
                }
            } catch (e: Exception) {
                Log.e(TAG, "speak exception", e)
                onSpeakDone?.invoke()
                onSpeakDone = null
            }
        }.start()
    }

    /**
     * 调用豆包 HTTP TTS API，获取 PCM 原始音频数据
     */
    private fun synthesize(text: String): ByteArray? {
        var conn: HttpURLConnection? = null
        try {
            val url = URL(TTS_URL)
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer;$ACCESS_TOKEN")
            conn.setRequestProperty("X-Appid", APP_ID)

            // 构建请求 JSON（使用 Gson 正确处理 Unicode 字符）
            val speedRatio = currentSpeed.coerceIn(0.5f, 2.0f)
            val volumeRatio = currentVolume.coerceIn(0.5f, 2.0f)
            val pitchRatio = currentPitch.coerceIn(0.5f, 2.0f)

            val requestBody = mapOf(
                "app" to mapOf(
                    "appid" to APP_ID,
                    "token" to ACCESS_TOKEN,
                    "cluster" to "volcano_tts"
                ),
                "user" to mapOf(
                    "uid" to "uid_${System.currentTimeMillis()}"
                ),
                "audio" to mapOf(
                    "voice_type" to currentVcn,
                    "encoding" to "mp3",
                    "rate" to 24000,
                    "speed_ratio" to speedRatio,
                    "volume_ratio" to volumeRatio,
                    "pitch_ratio" to pitchRatio,
                    "emotion" to "neutral",
                    "language" to "cn"
                ),
                "request" to mapOf(
                    "reqid" to "req_${System.currentTimeMillis()}_${(Math.random() * 10000).toInt()}",
                    "text" to text.replace(Regex("[\\x00-\\x1F\\x7F]"), "").take(500),
                    "text_type" to "plain",
                    "operation" to "query",
                    "with_frontend" to "1",
                    "frontend_type" to "unitTson"
                )
            )
            val json = gson.toJson(requestBody)

            conn.outputStream.use { os ->
                os.write(json.toByteArray(Charsets.UTF_8))
            }

            val responseCode = conn.responseCode
            Log.d(TAG, "TTS response code: $responseCode")

            if (responseCode == 200) {
                // 读取响应体
                val responseBytes = conn.inputStream.readBytes()
                val responseStr = responseBytes.toString(Charsets.UTF_8)

                Log.d(TAG, "Response length: ${responseBytes.size}, content: $responseStr")

                // 尝试解析为 JSON（豆包返回 base64 编码的音频）
                try {
                    val jsonObj = gson.fromJson(responseStr, Map::class.java)
                    val code = (jsonObj["code"] as? Number)?.toInt() ?: -1
                    Log.d(TAG, "TTS response code: $code")

                    if (code == 3000) {
                        // 成功，音频在 data 字段
                        val audioData = jsonObj["data"] as? String
                        if (audioData != null && audioData.isNotEmpty()) {
                            Log.d(TAG, "Audio data length: ${audioData.length}")
                            return Base64.decode(audioData, Base64.NO_WRAP)
                        } else {
                            Log.e(TAG, "Audio data is empty")
                        }
                    } else {
                        val message = jsonObj["message"] as? String ?: "unknown"
                        Log.e(TAG, "TTS API error code: $code, message: $message")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "JSON parse error: ${e.message}")
                }
                return null
            } else {
                // 读取错误信息
                val errorBody = conn.errorStream?.readBytes()?.toString(Charsets.UTF_8) ?: "unknown"
                Log.e(TAG, "TTS API error: $responseCode - $errorBody")
                return null
            }
        } catch (e: Exception) {
            Log.e(TAG, "synthesize exception", e)
            return null
        } finally {
            conn?.disconnect()
        }
    }


    /**
     * 停止朗读
     */
    fun stop() {
        try {
            playbackHandler?.sendEmptyMessage(MSG_PLAY_END)
        } catch (_: Exception) {}
        stopPlayback()
    }

    fun shutdown() {
        stop()
        isInitialized = false
        playbackThread?.interrupt()
        playbackThread = null
    }

    private fun startPlaybackThread() {
        if (playbackThread != null) return
        playbackThread = Thread {
            Looper.prepare()
            playbackHandler = Handler(Looper.myLooper()!!) { msg ->
                when (msg.what) {
                    MSG_PLAY_START -> {
                        try {
                            // 初始化 AudioTrack
                            val minBuffer = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
                            audioTrack = AudioTrack(
                                AudioManager.STREAM_MUSIC,
                                SAMPLE_RATE,
                                CHANNEL_CONFIG,
                                AUDIO_FORMAT,
                                minBuffer.coerceAtLeast(8192),
                                AudioTrack.MODE_STREAM
                            )
                            audioTrack?.play()
                            isPlaying = true
                            Thread { drainPCMBuffer() }.start()
                        } catch (e: Exception) {
                            Log.e(TAG, "play start error", e)
                        }
                    }
                    MSG_PLAY_MP3 -> {
                        try {
                            val filePath = msg.obj as? String
                            if (filePath != null) {
                                mediaPlayer?.release()
                                mediaPlayer = MediaPlayer().apply {
                                    setDataSource(filePath)
                                    setOnCompletionListener {
                                        Log.d(TAG, "MP3 playback completed")
                                        onSpeakDone?.invoke()
                                        onSpeakDone = null
                                        File(filePath).delete()
                                    }
                                    setOnErrorListener { _, what, extra ->
                                        Log.e(TAG, "MediaPlayer error: $what, $extra")
                                        onSpeakDone?.invoke()
                                        onSpeakDone = null
                                        File(filePath).delete()
                                        true
                                    }
                                    prepare()
                                    start()
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "MP3 play error", e)
                            onSpeakDone?.invoke()
                            onSpeakDone = null
                        }
                    }
                    MSG_PLAY_END -> {
                        stopPlayback()
                        onSpeakDone?.invoke()
                        onSpeakDone = null
                    }
                }
                true
            }
            Looper.loop()
        }.apply { start() }
    }

    private fun drainPCMBuffer() {
        val buffer = ByteArray(4096)
        var emptyCount = 0
        while (isPlaying && !Thread.currentThread().isInterrupted) {
            val available = synchronized(pendingPCMData) {
                if (pendingPCMData.isEmpty()) 0 else pendingPCMData.size
            }
            if (available > 0) {
                emptyCount = 0
                val toRead = available.coerceAtMost(buffer.size)
                synchronized(pendingPCMData) {
                    for (i in 0 until toRead) {
                        buffer[i] = pendingPCMData.removeAt(0)
                    }
                }
                try {
                    audioTrack?.write(buffer, 0, toRead)
                } catch (e: Exception) {
                    Log.e(TAG, "audio write error", e)
                }
            } else {
                emptyCount++
                // 如果连续 100 次检查都为空（约 3 秒），认为数据已传输完
                if (emptyCount > 100) {
                    Log.d(TAG, "PCM buffer drained, stopping")
                    playbackHandler?.sendEmptyMessage(MSG_PLAY_END)
                    break
                }
                Thread.sleep(30)
            }
        }
    }

    private fun stopPlayback() {
        isPlaying = false
        try {
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        } catch (_: Exception) {}
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (_: Exception) {}
        synchronized(pendingPCMData) { pendingPCMData.clear() }
    }

    private const val MSG_PLAY_START = 1
    private const val MSG_PLAY_END = 2
    private const val MSG_PLAY_MP3 = 3
}