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

    private lateinit var binding: FragmentSettingBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSettingBinding.inflate(inflater, container, false)

        setupClickListeners()
        setupSwitchListeners()

        return binding.root
    }

    private fun setupClickListeners() {
        // 头像点击
        binding.ivAvatar.setOnClickListener {
            showToast("点击头像选择图片")
        }

        // 用户名点击
        binding.tvUsername.setOnClickListener {
            showInputDialog("修改用户名", binding.tvUsername.text.toString()) { newName ->
                binding.tvUsername.text = newName
            }
        }

        // 用户人设点击
        binding.tvUserPersona.setOnClickListener {
            showInputDialog("修改人设", binding.tvUserPersona.text.toString()) { newPersona ->
                binding.tvUserPersona.text = newPersona
            }
        }

        // 邀请系统
        binding.llInviteSystem.setOnClickListener {
            showToast("邀请系统")
        }

        // 背景音乐
        binding.llBackgroundMusic.setOnClickListener {
            showToast("背景音乐设置")
        }

        // 背景设置
        binding.llBackgroundSetting.setOnClickListener {
            showToast("背景设置")
        }

        // 气泡设置
        binding.llBubbleSetting.setOnClickListener {
            showToast("气泡设置")
        }

        // 语音设置
        binding.llVoiceSetting.setOnClickListener {
            showToast("语音设置")
        }

        // 记忆设置
        binding.llMemorySetting.setOnClickListener {
            showToast("记忆设置")
        }

        // 回复设置
        binding.llReplySetting.setOnClickListener {
            showToast("回复设置")
        }

        // 全局正则
        binding.llGlobalRegex.setOnClickListener {
            showToast("全局正则设置")
        }

        // 配图设置
        binding.llImageSetting.setOnClickListener {
            showToast("配图设置")
        }

        // 状态栏设置
        binding.llStatusBarSetting.setOnClickListener {
            showToast("状态栏设置")
        }

        // 备份与恢复
        binding.llBackup.setOnClickListener {
            showToast("备份与恢复")
        }

        // 检查更新
        binding.llCheckUpdate.setOnClickListener {
            showToast("检查更新中...")
        }

        // 官方网站
        binding.llOfficialWebsite.setOnClickListener {
            showToast("官方网站")
        }

        // 官方交流群
        binding.llOfficialGroup.setOnClickListener {
            showToast("官方交流群")
        }
    }

    private fun setupSwitchListeners() {
        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                showToast("已开启深色模式")
            } else {
                showToast("已关闭深色模式")
            }
        }

        binding.switchCuteFont.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                showToast("已开启可爱字体")
            } else {
                showToast("已关闭可爱字体")
            }
        }
    }

    private fun showInputDialog(title: String, currentValue: String, onConfirm: (String) -> Unit) {
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
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}
