package com.example.chatbot.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.chatbot.data.model.ApiConfig
import com.example.chatbot.data.repository.ApiConfigRepository
import com.example.chatbot.util.ModelDefaultTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ApiConfigViewModel(private val repository: ApiConfigRepository) : ViewModel() {

    private val _apiConfig = MutableLiveData<ApiConfig?>(null)
    val apiConfig: LiveData<ApiConfig?> = _apiConfig

    init {
        loadConfig()
    }

    fun loadConfig() = viewModelScope.launch(Dispatchers.IO) {
        _apiConfig.postValue(repository.getApiConfig())
    }

    /** @deprecated 使用 [persistFromFields] 并在需要时等待完成 */
    fun saveConfig(config: ApiConfig) = viewModelScope.launch(Dispatchers.IO) {
        repository.saveApiConfig(config)
        _apiConfig.postValue(repository.getApiConfig() ?: config)
    }

    /**
     * 从表单写入数据库并读回校验。空白 maxTokens 时保留库中已有值，避免编辑过程中误写回旧值。
     */
    suspend fun persistFromFields(
        baseUrl: String,
        apiKey: String,
        model: String,
        temperature: Double,
        maxTokensRaw: String?,
        embedModel: String = "",
        embedApiKey: String = ""
    ): PersistResult = withContext(Dispatchers.IO) {
        val trimmedModel = model.trim()
        if (baseUrl.isBlank() || apiKey.isBlank() || trimmedModel.isEmpty()) {
            return@withContext PersistResult.Invalid(PersistFieldError.REQUIRED_FIELDS)
        }
        if (temperature !in 0.0..2.0) {
            return@withContext PersistResult.Invalid(PersistFieldError.INVALID_TEMPERATURE)
        }

        val maxTokensText = maxTokensRaw?.trim().orEmpty()
        if (maxTokensText.isNotEmpty()) {
            val parsed = parseMaxTokens(maxTokensText)
            if (parsed == null || parsed !in 1..ModelDefaultTokens.MAX_OUTPUT_TOKENS_CAP) {
                return@withContext PersistResult.Invalid(PersistFieldError.INVALID_MAX_TOKENS)
            }
        }

        val dbExisting = repository.getApiConfig()
        val maxTokens = when {
            maxTokensText.isNotEmpty() -> parseMaxTokens(maxTokensText)!!
            dbExisting != null -> dbExisting.maxTokens
            else -> ModelDefaultTokens.recommendedMaxOutputTokens(trimmedModel)
        }

        val trimmedChatKey = apiKey.trim()
        val trimmedEmbedKeyField = embedApiKey.trim()
        val storedEmbedKey = when {
            trimmedEmbedKeyField.isEmpty() -> ""
            trimmedEmbedKeyField == trimmedChatKey -> ""
            else -> trimmedEmbedKeyField
        }

        val config = ApiConfig(
            id = 1,
            baseUrl = baseUrl.trim(),
            apiKey = trimmedChatKey,
            model = trimmedModel,
            temperature = temperature,
            maxTokens = maxTokens,
            embedModel = embedModel.trim(),
            embedApiKey = storedEmbedKey
        )
        repository.saveApiConfig(config)
        val readBack = repository.getApiConfig()
            ?: return@withContext PersistResult.Failed

        _apiConfig.postValue(readBack)
        if (readBack.maxTokens != config.maxTokens) {
            PersistResult.Failed
        } else {
            PersistResult.Success(readBack)
        }
    }

    private fun parseMaxTokens(text: String): Int? {
        text.toIntOrNull()?.let { return it }
        val digitsOnly = text.filter { it.isDigit() }
        return digitsOnly.toIntOrNull()
    }

    sealed class PersistResult {
        data class Success(val config: ApiConfig) : PersistResult()
        data class Invalid(val error: PersistFieldError) : PersistResult()
        data object Failed : PersistResult()
    }

    enum class PersistFieldError {
        REQUIRED_FIELDS,
        INVALID_TEMPERATURE,
        INVALID_MAX_TOKENS
    }
}

class ApiConfigViewModelFactory(private val repository: ApiConfigRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ApiConfigViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ApiConfigViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
