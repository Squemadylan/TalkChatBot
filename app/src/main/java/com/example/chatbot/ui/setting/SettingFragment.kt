package com.example.chatbot.ui.setting

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.chatbot.R
import com.example.chatbot.databinding.FragmentSettingBinding

class SettingFragment : Fragment() {

    private var _binding: FragmentSettingBinding? = null
    private val binding get() = _binding ?: throw IllegalStateException("Binding is not initialized or already destroyed")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        try {
            _binding = FragmentSettingBinding.inflate(inflater, container, false)
        } catch (e: Exception) {
            showError("Failed to inflate layout: ${e.message}")
            return View(requireContext())
        }

        setupClickListeners()
        setupSwitchListeners()

        return binding.root
    }

    private fun setupClickListeners() {
        binding.ivAvatar.setOnClickListener {
            showToast("点击头像选择图片")
        }

        binding.tvUsername.setOnClickListener {
            showInputDialog("修改用户名", binding.tvUsername.text.toString()) { newName ->
                safeAction {
                    binding.tvUsername.text = newName
                }
            }
        }

        binding.tvUserPersona.setOnClickListener {
            showInputDialog("修改人设", binding.tvUserPersona.text.toString()) { newPersona ->
                safeAction {
                    binding.tvUserPersona.text = newPersona
                }
            }
        }

        binding.llInviteSystem.setOnClickListener {
            showToast("邀请系统")
        }

        binding.llBackgroundMusic.setOnClickListener {
            showToast("背景音乐设置")
        }

        binding.llBackgroundSetting.setOnClickListener {
            showToast("背景设置")
        }

        binding.llBubbleSetting.setOnClickListener {
            showToast("气泡设置")
        }

        binding.llVoiceSetting.setOnClickListener {
            showToast("语音设置")
        }

        binding.llMemorySetting.setOnClickListener {
            showToast("记忆设置")
        }

        binding.llReplySetting.setOnClickListener {
            showToast("回复设置")
        }

        binding.llGlobalRegex.setOnClickListener {
            showToast("全局正则设置")
        }

        binding.llImageSetting.setOnClickListener {
            showToast("配图设置")
        }

        binding.llStatusBarSetting.setOnClickListener {
            showToast("状态栏设置")
        }

        binding.llBackup.setOnClickListener {
            showToast("备份与恢复")
        }

        binding.llCheckUpdate.setOnClickListener {
            showToast("检查更新中...")
        }

        binding.llOfficialWebsite.setOnClickListener {
            showToast("官方网站")
        }

        binding.llOfficialGroup.setOnClickListener {
            showToast("官方交流群")
        }
    }

    private fun setupSwitchListeners() {
        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            safeAction {
                showToast(if (isChecked) "已开启深色模式" else "已关闭深色模式")
            }
        }

        binding.switchCuteFont.setOnCheckedChangeListener { _, isChecked ->
            safeAction {
                showToast(if (isChecked) "已开启可爱字体" else "已关闭可爱字体")
            }
        }
    }

    private fun showInputDialog(title: String, currentValue: String, onConfirm: (String) -> Unit) {
        try {
            if (!isAdded || context == null) return

            val editText = EditText(requireContext())
            editText.setText(currentValue)
            editText.setPadding(48, 24, 48, 24)

            AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setView(editText)
                .setPositiveButton("确定") { _, _ ->
                    val newValue = editText.text.toString().trim()
                    if (newValue.isNotEmpty()) {
                        onConfirm(newValue)
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        } catch (e: Exception) {
            showError("Failed to show dialog: ${e.message}")
        }
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
        private const val TAG = "SettingFragment"
    }
}
