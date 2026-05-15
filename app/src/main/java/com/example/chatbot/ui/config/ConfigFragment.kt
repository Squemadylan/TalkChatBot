package com.example.chatbot.ui.config

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.chatbot.App
import com.example.chatbot.data.repository.ApiConfigRepository
import com.example.chatbot.data.model.ApiConfig
import com.example.chatbot.databinding.FragmentConfigBinding
import com.example.chatbot.util.ModelDefaultTokens
import com.example.chatbot.viewmodel.ApiConfigViewModel
import com.example.chatbot.viewmodel.ApiConfigViewModelFactory

class ConfigFragment : Fragment() {

    private var _binding: FragmentConfigBinding? = null
    private val binding get() = _binding

    private var viewModel: ApiConfigViewModel? = null
    private var applyingRemoteConfig = false

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
        setupSaveButton()
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
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error updating config UI", e)
            } finally {
                applyingRemoteConfig = false
            }
        }
    }

    private fun setupSaveButton() {
        if (_binding == null) return

        binding?.btnSave?.setOnClickListener {
            if (_binding == null) {
                showToastSafe("配置页面未正确加载")
                return@setOnClickListener
            }

            if (!validateInput()) {
                return@setOnClickListener
            }

            val app = try {
                requireActivity().application as? App
            } catch (e: Exception) {
                showToastSafe("应用初始化失败")
                return@setOnClickListener
            }

            if (app == null || !app.isDatabaseInitialized()) {
                showToastSafe("数据库初始化中，请稍后重试")
                return@setOnClickListener
            }

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
                showToastSafe("配置已保存")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error saving config", e)
                showToastSafe("保存失败")
            }
        }
    }

    private fun validateInput(): Boolean {
        if (_binding == null) {
            showToastSafe("配置页面未正确加载")
            return false
        }

        val baseUrl = binding?.etBaseUrl?.text?.toString()?.trim() ?: ""
        val apiKey = binding?.etApiKey?.text?.toString()?.trim() ?: ""
        val model = binding?.etModel?.text?.toString()?.trim() ?: ""
        val temperatureText = binding?.etTemperature?.text?.toString() ?: ""
        val maxTokensText = binding?.etMaxTokens?.text?.toString()?.trim() ?: ""

        if (baseUrl.isEmpty()) {
            showToastSafe("请输入API地址")
            try {
                binding?.etBaseUrl?.requestFocus()
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to request focus", e)
            }
            return false
        }

        if (apiKey.isEmpty()) {
            showToastSafe("请输入API密钥")
            try {
                binding?.etApiKey?.requestFocus()
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to request focus", e)
            }
            return false
        }

        if (model.isEmpty()) {
            showToastSafe("请输入模型名称")
            try {
                binding?.etModel?.requestFocus()
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to request focus", e)
            }
            return false
        }

        val temperature = temperatureText.toDoubleOrNull()
        if (temperature == null || temperature < 0 || temperature > 2) {
            showToastSafe("温度参数应在0-2之间")
            try {
                binding?.etTemperature?.requestFocus()
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to request focus", e)
            }
            return false
        }

        if (maxTokensText.isNotEmpty()) {
            val maxTokens = maxTokensText.toIntOrNull()
            if (maxTokens == null || maxTokens < 1 || maxTokens > ModelDefaultTokens.MAX_OUTPUT_TOKENS_CAP) {
                showToastSafe(
                    "最大 Token 须在 1–${ModelDefaultTokens.MAX_OUTPUT_TOKENS_CAP} 之间，或留空使用按模型推荐值"
                )
                try {
                    binding?.etMaxTokens?.requestFocus()
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Failed to request focus", e)
                }
                return false
            }
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
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "ConfigFragment"
    }
}
