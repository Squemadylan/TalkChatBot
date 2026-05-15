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
import com.example.chatbot.App
import com.example.chatbot.R
import com.example.chatbot.data.model.ApiConfig
import com.example.chatbot.data.repository.ApiConfigRepository
import com.example.chatbot.databinding.FragmentConfigBinding
import com.example.chatbot.util.ModelDefaultTokens
import com.example.chatbot.viewmodel.ApiConfigViewModel
import com.example.chatbot.viewmodel.ApiConfigViewModelFactory

class ConfigFragment : Fragment() {

    private var _binding: FragmentConfigBinding? = null
    private val binding get() = _binding

    private var viewModel: ApiConfigViewModel? = null
    private var applyingRemoteConfig = false
    private var apiKeyVisible = false

    private val autoSaveHandler = Handler(Looper.getMainLooper())
    private val autoSaveRunnable = Runnable { persistConfigIfValid() }

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
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error updating config UI", e)
            } finally {
                applyingRemoteConfig = false
            }
        }
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

    private fun persistConfigIfValid() {
        if (_binding == null || applyingRemoteConfig) return

        val app = try {
            requireActivity().application as? App
        } catch (e: Exception) {
            return
        }

        if (app == null || !app.isDatabaseInitialized()) return

        if (!isInputValidForSave()) return

        try {
            val modelTrim = binding?.etModel?.text?.toString()?.trim() ?: ""
            val maxTokens = binding?.etMaxTokens?.text?.toString()?.trim()?.toIntOrNull()
                ?: ModelDefaultTokens.recommendedMaxOutputTokens(modelTrim)

            val config = ApiConfig(
                id = 1,
                baseUrl = binding?.etBaseUrl?.text?.toString()?.trim() ?: "",
                apiKey = binding?.etApiKey?.text?.toString()?.trim() ?: "",
                model = modelTrim,
                temperature = binding?.etTemperature?.text?.toString()?.toDoubleOrNull() ?: 0.7,
                maxTokens = maxTokens
            )

            viewModel?.saveConfig(config)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error auto-saving config", e)
        }
    }

    private fun isInputValidForSave(): Boolean {
        if (_binding == null) return false

        val baseUrl = binding?.etBaseUrl?.text?.toString()?.trim() ?: ""
        val apiKey = binding?.etApiKey?.text?.toString()?.trim() ?: ""
        val model = binding?.etModel?.text?.toString()?.trim() ?: ""
        val temperatureText = binding?.etTemperature?.text?.toString() ?: ""
        val maxTokensText = binding?.etMaxTokens?.text?.toString()?.trim() ?: ""

        if (baseUrl.isEmpty() || apiKey.isEmpty() || model.isEmpty()) return false

        val temperature = temperatureText.toDoubleOrNull() ?: return false
        if (temperature < 0 || temperature > 2) return false

        if (maxTokensText.isNotEmpty()) {
            val maxTokens = maxTokensText.toIntOrNull() ?: return false
            if (maxTokens < 1 || maxTokens > ModelDefaultTokens.MAX_OUTPUT_TOKENS_CAP) return false
        }

        return true
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
