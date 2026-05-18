package com.example.chatbot.ui.config

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.text.method.PasswordTransformationMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.chatbot.App
import com.example.chatbot.R
import com.example.chatbot.data.repository.ApiConfigRepository
import com.example.chatbot.databinding.FragmentConfigBinding
import com.example.chatbot.util.ModelDefaultTokens
import com.example.chatbot.viewmodel.ApiConfigViewModel
import com.example.chatbot.viewmodel.ApiConfigViewModelFactory
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class ConfigFragment : Fragment() {

    private var _binding: FragmentConfigBinding? = null
    private val binding get() = _binding

    private var viewModel: ApiConfigViewModel? = null
    private var applyingRemoteConfig = false
    private var apiKeyVisible = false

    private val autoSaveHandler = Handler(Looper.getMainLooper())
    private val autoSaveRunnable = Runnable { persistConfigAsync() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return try {
            _binding = FragmentConfigBinding.inflate(inflater, container, false)
            binding!!.root
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to inflate binding", e)
            val errorView = FrameLayout(requireContext()).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                addView(TextView(context).apply {
                    text = "配置页面加载失败"
                    textSize = 16f
                    setPadding(32, 32, 32, 32)
                })
            }
            try {
                Toast.makeText(requireContext(), "配置页面加载失败", Toast.LENGTH_SHORT).show()
            } catch (ignored: Exception) {}
            errorView
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (_binding == null) {
            return
        }

        initializeViewModel()
        setupRegisterSiliconflowButton()
        setupApiKeyVisibilityToggle()
        setupAutoSave()
        setupAutoMaxTokensFromModel()
    }

    override fun onResume() {
        super.onResume()
        viewModel?.loadConfig()
    }

    private fun initializeViewModel() {
        if (_binding == null) return

        try {
            val app = requireActivity().application as? App
            if (app == null) {
                showToastSafe("应用初始化失败")
                return
            }

            if (!app.isDatabaseInitialized()) {
                showToastSafe("数据库初始化中，请稍后重试")
                return
            }

            val apiConfigRepository = ApiConfigRepository(app.database.apiConfigDao())

            viewModel = ViewModelProvider(
                this,
                ApiConfigViewModelFactory(apiConfigRepository)
            )[ApiConfigViewModel::class.java]

            observeConfig()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to initialize ViewModel", e)
            showToastSafe("ViewModel初始化失败")
        }
    }

    private fun observeConfig() {
        if (_binding == null) return

        viewModel?.apiConfig?.observe(viewLifecycleOwner) { config ->
            if (_binding == null) return@observe

            try {
                applyingRemoteConfig = true
                config?.let {
                    binding?.etBaseUrl?.setText(it.baseUrl)
                    binding?.etApiKey?.setText(it.apiKey)
                    binding?.etModel?.setText(it.model)
                    binding?.etTemperature?.setText(it.temperature.toString())
                    binding?.etMaxTokens?.setText(it.maxTokens.toString())
                }
                applyApiKeyMasking()
                updateEffectiveConfigHint(config)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error updating config UI", e)
            } finally {
                applyingRemoteConfig = false
            }
        }
    }

    private fun updateEffectiveConfigHint(config: com.example.chatbot.data.model.ApiConfig?) {
        val tv = binding?.tvEffectiveConfig ?: return
        if (config == null || config.apiKey.isBlank() || config.model.isBlank()) {
            tv.text = getString(R.string.config_effective_empty)
            return
        }
        tv.text = getString(
            R.string.config_effective_hint,
            config.maxTokens,
            config.model
        )
    }

    private fun setupRegisterSiliconflowButton() {
        if (_binding == null) return

        binding?.btnRegisterSiliconflow?.setOnClickListener {
            openSiliconFlowRegisterPage()
        }
    }

    private fun openSiliconFlowRegisterPage() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(SILICONFLOW_REGISTER_URL))
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            showToastSafe(getString(R.string.config_open_register_failed))
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to open SiliconFlow register page", e)
            showToastSafe(getString(R.string.config_open_register_failed))
        }
    }

    private fun setupApiKeyVisibilityToggle() {
        if (_binding == null) return

        applyApiKeyMasking()
        binding?.btnToggleApiKeyVisibility?.setOnClickListener {
            apiKeyVisible = !apiKeyVisible
            applyApiKeyMasking()
        }
    }

    private fun applyApiKeyMasking() {
        val editText = binding?.etApiKey ?: return
        val selection = editText.selectionEnd
        if (apiKeyVisible) {
            editText.transformationMethod = null
            binding?.btnToggleApiKeyVisibility?.setImageResource(R.drawable.ic_visibility)
        } else {
            editText.transformationMethod = PasswordTransformationMethod.getInstance()
            binding?.btnToggleApiKeyVisibility?.setImageResource(R.drawable.ic_visibility_off)
        }
        editText.setSelection(selection.coerceIn(0, editText.text?.length ?: 0))
    }

    private fun setupAutoSave() {
        if (_binding == null) return

        val fields = listOfNotNull(
            binding?.etBaseUrl,
            binding?.etApiKey,
            binding?.etModel,
            binding?.etTemperature,
            binding?.etMaxTokens
        )

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (applyingRemoteConfig || _binding == null) return
                scheduleAutoSave()
            }
        }

        fields.forEach { it.addTextChangedListener(watcher) }
    }

    private fun scheduleAutoSave() {
        autoSaveHandler.removeCallbacks(autoSaveRunnable)
        autoSaveHandler.postDelayed(autoSaveRunnable, AUTO_SAVE_DELAY_MS)
    }

    private fun persistConfigAsync() {
        if (_binding == null || applyingRemoteConfig) return
        val vm = viewModel ?: return
        val snapshot = captureFormSnapshot() ?: return

        lifecycleScope.launch {
            when (val result = vm.persistFromFields(
                baseUrl = snapshot.baseUrl,
                apiKey = snapshot.apiKey,
                model = snapshot.model,
                temperature = snapshot.temperature,
                maxTokensRaw = snapshot.maxTokensRaw
            )) {
                is ApiConfigViewModel.PersistResult.Invalid -> {
                    showValidationError(result.error)
                }
                is ApiConfigViewModel.PersistResult.Failed -> {
                    showToastSafe(getString(R.string.config_save_failed))
                }
                is ApiConfigViewModel.PersistResult.Success -> {
                    updateEffectiveConfigHint(result.config)
                }
            }
        }
    }

    /**
     * 离开配置页时同步写入数据库，避免用户改完立刻去聊天仍读到旧 max_tokens。
     */
    private fun persistConfigBlocking(): ApiConfigViewModel.PersistResult? {
        if (_binding == null || applyingRemoteConfig) return null
        val vm = viewModel ?: return null
        val snapshot = captureFormSnapshot() ?: return null

        return runBlocking {
            vm.persistFromFields(
                baseUrl = snapshot.baseUrl,
                apiKey = snapshot.apiKey,
                model = snapshot.model,
                temperature = snapshot.temperature,
                maxTokensRaw = snapshot.maxTokensRaw
            )
        }
    }

    private data class FormSnapshot(
        val baseUrl: String,
        val apiKey: String,
        val model: String,
        val temperature: Double,
        val maxTokensRaw: String
    )

    private fun captureFormSnapshot(): FormSnapshot? {
        if (_binding == null) return null
        val temperature = binding?.etTemperature?.text?.toString()?.trim()?.toDoubleOrNull()
            ?: return null
        return FormSnapshot(
            baseUrl = binding?.etBaseUrl?.text?.toString().orEmpty(),
            apiKey = binding?.etApiKey?.text?.toString().orEmpty(),
            model = binding?.etModel?.text?.toString().orEmpty(),
            temperature = temperature,
            maxTokensRaw = binding?.etMaxTokens?.text?.toString().orEmpty()
        )
    }

    private fun showValidationError(error: ApiConfigViewModel.PersistFieldError) {
        val message = when (error) {
            ApiConfigViewModel.PersistFieldError.REQUIRED_FIELDS ->
                getString(R.string.config_save_required_fields)
            ApiConfigViewModel.PersistFieldError.INVALID_TEMPERATURE ->
                getString(R.string.config_save_invalid_temperature)
            ApiConfigViewModel.PersistFieldError.INVALID_MAX_TOKENS ->
                getString(
                    R.string.config_save_invalid_max_tokens,
                    ModelDefaultTokens.MAX_OUTPUT_TOKENS_CAP
                )
        }
        showToastSafe(message)
    }

    private fun setupAutoMaxTokensFromModel() {
        if (_binding == null) return
        binding?.etModel?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (applyingRemoteConfig || _binding == null) return
                val model = s?.toString()?.trim().orEmpty()
                if (model.isEmpty()) return
                val maxEt = binding?.etMaxTokens ?: return
                if (maxEt.text.isNullOrBlank()) {
                    maxEt.setText(ModelDefaultTokens.recommendedMaxOutputTokens(model).toString())
                }
            }
        })
    }

    private fun showToastSafe(message: String) {
        try {
            if (context != null) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to show toast", e)
        }
    }

    override fun onPause() {
        autoSaveHandler.removeCallbacks(autoSaveRunnable)
        when (val result = persistConfigBlocking()) {
            null -> Unit
            is ApiConfigViewModel.PersistResult.Invalid -> showValidationError(result.error)
            is ApiConfigViewModel.PersistResult.Failed -> showToastSafe(getString(R.string.config_save_failed))
            is ApiConfigViewModel.PersistResult.Success -> updateEffectiveConfigHint(result.config)
        }
        super.onPause()
    }

    override fun onDestroyView() {
        autoSaveHandler.removeCallbacks(autoSaveRunnable)
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "ConfigFragment"
        private const val SILICONFLOW_REGISTER_URL = "https://cloud.siliconflow.cn/i/jJkqmCeU"
        private const val AUTO_SAVE_DELAY_MS = 600L
    }
}
