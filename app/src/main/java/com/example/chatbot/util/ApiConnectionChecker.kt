package com.example.chatbot.util

import com.example.chatbot.data.model.ApiConfig
import com.example.chatbot.data.network.ApiService
import com.example.chatbot.data.network.ChatRequest
import com.example.chatbot.data.network.MessageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException

/**
 * API 连接检测结果
 */
data class ApiCheckResult(
    val success: Boolean,
    val errorType: ErrorType?,
    val errorMessage: String,
    val latencyMs: Long
)

enum class ErrorType {
    INVALID_KEY,           // HTTP 401
    INSUFFICIENT_QUOTA,    // HTTP 403 / 402
    MODEL_NOT_FOUND,        // HTTP 404
    INVALID_URL,           // URL 格式错误或无法连接
    NETWORK_ERROR,         // 网络不可达
    TIMEOUT,               // 请求超时
    SERVER_ERROR,          // 服务器错误 HTTP 5xx
    UNKNOWN                // 未知错误
}

/**
 * API 连接检测器
 */
object ApiConnectionChecker {

    private const val TIMEOUT_MS = 10000L
    private const val TEST_MODEL = "deepseek-ai/DeepSeek-V3.2"

    suspend fun checkConnection(config: ApiConfig): ApiCheckResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        val result = withTimeoutOrNull(TIMEOUT_MS) {
            performCheck(config)
        }

        val latency = System.currentTimeMillis() - startTime

        result ?: ApiCheckResult(
            success = false,
            errorType = ErrorType.TIMEOUT,
            errorMessage = "请求超时（${TIMEOUT_MS / 1000}秒）",
            latencyMs = latency
        )
    }

    private suspend fun performCheck(config: ApiConfig): ApiCheckResult? {
        val startTime = System.currentTimeMillis()

        return try {
            // 构建 URL
            val baseUrl = config.baseUrl.trim().let {
                if (it.endsWith("/")) it else "$it/"
            }
            val chatUrl = "${baseUrl}v1/chat/completions"

            // 创建 Retrofit 实例
            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val apiService = retrofit.create(ApiService::class.java)

            // 构造测试请求
            val request = ChatRequest(
                model = config.model.ifBlank { TEST_MODEL },
                messages = listOf(
                    MessageRequest(
                        role = "user",
                        content = "Hi"
                    )
                ),
                temperature = 0.7,
                maxTokens = 10,
                stream = false
            )

            // 发送请求
            val response = apiService.sendMessage(chatUrl, request)
            val latency = System.currentTimeMillis() - startTime

            ApiCheckResult(
                success = true,
                errorType = null,
                errorMessage = "连接成功",
                latencyMs = latency
            )

        } catch (e: retrofit2.HttpException) {
            val latency = System.currentTimeMillis() - startTime
            parseHttpError(e.code(), e.response()?.errorBody()?.string(), latency)

        } catch (e: IOException) {
            val latency = System.currentTimeMillis() - startTime
            when {
                e.message?.contains("Unable to resolve host") == true ||
                e.message?.contains("No address associated") == true -> {
                    ApiCheckResult(
                        success = false,
                        errorType = ErrorType.NETWORK_ERROR,
                        errorMessage = "网络不可达，请检查 Base URL 是否正确",
                        latencyMs = latency
                    )
                }
                else -> {
                    ApiCheckResult(
                        success = false,
                        errorType = ErrorType.INVALID_URL,
                        errorMessage = "连接失败：${e.message ?: "未知错误"}",
                        latencyMs = latency
                    )
                }
            }

        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            ApiCheckResult(
                success = false,
                errorType = ErrorType.UNKNOWN,
                errorMessage = "未知错误：${e.message ?: "未知原因"}",
                latencyMs = latency
            )
        }
    }

    private fun parseHttpError(code: Int, errorBody: String?, latency: Long): ApiCheckResult {
        val message = when (code) {
            401 -> "API Key 无效"
            403 -> "余额不足或无权限"
            404 -> "模型不存在或 API 地址错误"
            422 -> "请求参数错误，可能是模型名称不正确"
            429 -> "请求过于频繁，请稍后再试"
            in 500..599 -> "服务器错误（$code），请稍后再试"
            else -> "HTTP 错误（$code）"
        }

        val errorType = when (code) {
            401 -> ErrorType.INVALID_KEY
            403, 402 -> ErrorType.INSUFFICIENT_QUOTA
            404 -> ErrorType.MODEL_NOT_FOUND
            in 500..599 -> ErrorType.SERVER_ERROR
            else -> ErrorType.UNKNOWN
        }

        return ApiCheckResult(
            success = false,
            errorType = errorType,
            errorMessage = message,
            latencyMs = latency
        )
    }

    private data class ApiResult(
        val success: Boolean,
        val errorType: ErrorType,
        val errorMessage: String,
        val latencyMs: Long
    )
}