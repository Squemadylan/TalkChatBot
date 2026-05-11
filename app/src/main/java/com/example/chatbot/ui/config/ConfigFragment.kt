package com.example.chatbot.ui.config

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.chatbot.App
import com.example.chatbot.data.repository.ApiConfigRepository
import com.example.chatbot.data.model.ApiConfig
import com.example.chatbot.databinding.FragmentConfigBinding
import com.example.chatbot.viewmodel.ApiConfigViewModel

class ConfigFragment : Fragment() {

    private var _binding: FragmentConfigBinding? = null
    private val binding get() = _binding ?: throw IllegalStateException("Binding is not initialized or already destroyed")

    private lateinit var viewModel: ApiConfigViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        try {
            _binding = FragmentConfigBinding.inflate(inflater, container, false)
        } catch (e: Exception) {
            showError("Failed to inflate layout: ${e.message}")
            return View(requireContext())
        }

        if (!isAppInitialized()) {
            showError("App initialization failed")
            return binding.root
        }

        initializeViewModel()
        setupSaveButton()

        return binding.root
    }

    private fun isAppInitialized(): Boolean {
        return try {
            val app = requireActivity().application as? App
            app?.isDatabaseInitialized() == true
        } catch (e: Exception) {
            false
        }
    }

    private fun initializeViewModel() {
        try {
            val app = requireActivity().application as App
            val apiConfigRepository = ApiConfigRepository(app.database.apiConfigDao())

            viewModel = ViewModelProvider(this) {
                ApiConfigViewModel(apiConfigRepository)
            }.get(ApiConfigViewModel::class.java)

            observeConfig()
        } catch (e: Exception) {
            showError("Failed to initialize ViewModel: ${e.message}")
        }
    }

    private fun observeConfig() {
        if (!::viewModel.isInitialized) return

        viewModel.apiConfig.observe(viewLifecycleOwner) { config ->
            safeAction {
                config?.let {
                    binding.etBaseUrl.setText(it.baseUrl)
                    binding.etApiKey.setText(it.apiKey)
                    binding.etModel.setText(it.model)
                    binding.etTemperature.setText(it.temperature.toString())
                    binding.etMaxTokens.setText(it.maxTokens.toString())
                }
            }
        }
    }

    private fun setupSaveButton() {
        binding.btnSave.setOnClickListener {
            if (!validateInput()) {
                return@setOnClickListener
            }

            try {
                val config = ApiConfig(
                    id = 1,
                    baseUrl = binding.etBaseUrl.text.toString().trim(),
                    apiKey = binding.etApiKey.text.toString().trim(),
                    model = binding.etModel.text.toString().trim(),
                    temperature = binding.etTemperature.text.toString().toDoubleOrNull() ?: 0.7,
                    maxTokens = binding.etMaxTokens.text.toString().toIntOrNull() ?: 1024
                )

                viewModel.saveConfig(config)
                showToast("配置已保存")
            } catch (e: Exception) {
                showError("保存失败: ${e.message}")
            }
        }
    }

    private fun validateInput(): Boolean {
        val baseUrl = binding.etBaseUrl.text.toString().trim()
        val apiKey = binding.etApiKey.text.toString().trim()
        val model = binding.etModel.text.toString().trim()
        val temperature = binding.etTemperature.text.toString().toDoubleOrNull()
        val maxTokens = binding.etMaxTokens.text.toString().toIntOrNull()

        if (baseUrl.isEmpty()) {
            showToast("请输入API地址")
            binding.etBaseUrl.requestFocus()
            return false
        }

        if (apiKey.isEmpty()) {
            showToast("请输入API密钥")
            binding.etApiKey.requestFocus()
            return false
        }

        if (model.isEmpty()) {
            showToast("请输入模型名称")
            binding.etModel.requestFocus()
            return false
        }

        if (temperature == null || temperature < 0 || temperature > 2) {
            showToast("温度参数应在0-2之间")
            binding.etTemperature.requestFocus()
            return false
        }

        if (maxTokens == null || maxTokens <= 0 || maxTokens > 4096) {
            showToast("最大Token数应在1-4096之间")
            binding.etMaxTokens.requestFocus()
            return false
        }

        return true
    }

    private fun safeAction(action: () -> Unit) {
        try {
            if (isAdded && _binding != null) {
                action()
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Safe action failed", e)
        }
    }

    private fun showToast(message: String) {
        if (isAdded && context != null) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showError(message: String) {
        android.util.Log.e(TAG, message)
        if (isAdded && context != null) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
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
