package com.example.chatbot.ui.config

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.chatbot.App
import com.example.chatbot.data.repository.ApiConfigRepository
import com.example.chatbot.data.model.ApiConfig
import com.example.chatbot.databinding.FragmentConfigBinding
import com.example.chatbot.viewmodel.ApiConfigViewModel

class ConfigFragment : Fragment() {

    private lateinit var binding: FragmentConfigBinding
    private lateinit var viewModel: ApiConfigViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentConfigBinding.inflate(inflater, container, false)

        val apiConfigRepository = ApiConfigRepository((requireActivity().application as App).database.apiConfigDao())
        viewModel = ViewModelProvider(this) {
            ApiConfigViewModel(apiConfigRepository)
        }.get(ApiConfigViewModel::class.java)

        viewModel.apiConfig.observe(viewLifecycleOwner) { config ->
            config?.let {
                binding.etBaseUrl.setText(it.baseUrl)
                binding.etApiKey.setText(it.apiKey)
                binding.etModel.setText(it.model)
                binding.etTemperature.setText(it.temperature.toString())
                binding.etMaxTokens.setText(it.maxTokens.toString())
            }
        }

        binding.btnSave.setOnClickListener {
            val config = ApiConfig(
                id = 1,
                baseUrl = binding.etBaseUrl.text.toString().trim(),
                apiKey = binding.etApiKey.text.toString().trim(),
                model = binding.etModel.text.toString().trim(),
                temperature = binding.etTemperature.text.toString().toDoubleOrNull() ?: 0.7,
                maxTokens = binding.etMaxTokens.text.toString().toIntOrNull() ?: 1024
            )
            viewModel.saveConfig(config)
            android.widget.Toast.makeText(context, "配置已保存", android.widget.Toast.LENGTH_SHORT).show()
        }

        return binding.root
    }
}
