package com.example.chatbot.ui.setting

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.chatbot.App
import com.example.chatbot.R
import com.example.chatbot.data.repository.CharacterRepository
import com.example.chatbot.databinding.FragmentSettingBinding
import com.example.chatbot.ui.common.ConfirmDialog
import com.example.chatbot.ui.common.DEFAULT_INPUT_MAX_LENGTH
import com.example.chatbot.ui.common.showTextInputPrompt
import com.example.chatbot.util.AppUpdateManager
import com.example.chatbot.util.AvatarStorage
import com.example.chatbot.util.BackupManager
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SettingFragment : Fragment() {

    private var _binding: FragmentSettingBinding? = null
    private val binding get() = _binding

    private var isInitializing = false

    private val storagePermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.isEmpty()) return@registerForActivityResult
        val allOk = granted.values.all { it }
        if (!allOk && isAdded) {
            showToast("部分存储权限未授予，备份到「下载/ChatBot」仍可能成功；写入根目录 ChatBot 在旧系统需要全部允许")
        }
    }

    private val pickBackupFile = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { performRestore(it) }
    }

    private val pickUserAvatar = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null || _binding == null) return@registerForActivityResult
        val ctx = requireContext().applicationContext
        val path = AvatarStorage.saveFromUri(ctx, uri, "user_avatar.jpg")
        if (path != null) {
            ctx.getSharedPreferences(App.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(App.KEY_USER_AVATAR_PATH, path)
                .apply()
            AvatarStorage.loadInto(binding!!.ivAvatar, path)
            showToast("头像已更新")
        } else {
            showToast("保存头像失败")
        }
    }

    private val pickChatBackground = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null || _binding == null) return@registerForActivityResult
        val ctx = requireContext().applicationContext
        val path = AvatarStorage.saveFromUri(ctx, uri, "chat_background.jpg")
        if (path != null) {
            prefs().edit().putString(App.KEY_CHAT_BACKGROUND_PATH, path).apply()
            refreshChatBackgroundSummary()
            showToast("聊天底图已更新")
        } else {
            showToast("保存底图失败")
        }
    }

    private fun prefs() =
        requireContext().applicationContext.getSharedPreferences(App.PREFS_NAME, Context.MODE_PRIVATE)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return try {
            _binding = FragmentSettingBinding.inflate(inflater, container, false)
            binding?.root
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to inflate layout", e)
            if (isAdded && context != null) {
                Toast.makeText(requireContext(), "设置页面加载失败", Toast.LENGTH_SHORT).show()
            }
            View(requireContext())
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (_binding == null) {
            if (isAdded && context != null) {
                Toast.makeText(requireContext(), "设置页面加载失败", Toast.LENGTH_SHORT).show()
            }
            return
        }

        loadUserAvatar()
        loadUserProfileTexts()
        loadAppVersion()
        setupClickListeners()
        setupSwitchListeners()
        updateDarkModeSwitch()
        bindMemoryAndAvatarPrefs()
        refreshChatBackgroundSummary()
    }

    override fun onResume() {
        super.onResume()
        refreshChatBackgroundSummary()
        tryRequestStoragePermissionsOnEnter()
    }

    private fun loadUserProfileTexts() {
        if (_binding == null) return
        val name = prefs().getString(App.KEY_USER_DISPLAY_NAME, null)?.trim()?.takeIf { it.isNotEmpty() }
            ?: "用户"
        binding?.tvUsername?.text = name
        val persona = prefs().getString(App.KEY_USER_PERSONA, null)?.trim().orEmpty()
        binding?.tvUserPersona?.text = persona.ifBlank { "补充你的身份和设定" }
    }

    private fun loadUserAvatar() {
        val path = requireContext().applicationContext
            .getSharedPreferences(App.PREFS_NAME, Context.MODE_PRIVATE)
            .getString(App.KEY_USER_AVATAR_PATH, null)
        binding?.ivAvatar?.let { AvatarStorage.loadInto(it, path) }
    }

    private fun loadAppVersion() {
        try {
            val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            val versionName = packageInfo.versionName
            binding?.tvVersion?.text = "当前版本: v$versionName"
        } catch (e: Exception) {
            binding?.tvVersion?.text = "当前版本: v1.0.0"
        }
    }

    private fun setupClickListeners() {
        val launchPick = {
            pickUserAvatar.launch("image/*")
        }
        binding?.ivAvatar?.setOnClickListener { launchPick() }
        binding?.btnAvatarEdit?.setOnClickListener { launchPick() }

        binding?.tvUsername?.setOnClickListener {
            val currentText = binding?.tvUsername?.text?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: "用户"
            showInputDialog("修改用户名", currentText, allowEmpty = false) { newName ->
                safeAction {
                    binding?.tvUsername?.text = newName
                    prefs().edit().putString(App.KEY_USER_DISPLAY_NAME, newName).apply()
                }
            }
        }

        binding?.tvUserPersona?.setOnClickListener {
            val stored = prefs().getString(App.KEY_USER_PERSONA, "").orEmpty()
            showInputDialog("修改人设", stored, allowEmpty = true, isMultiline = true) { newPersona ->
                safeAction {
                    prefs().edit().putString(App.KEY_USER_PERSONA, newPersona).apply()
                    binding?.tvUserPersona?.text =
                        newPersona.trim().ifBlank { "补充你的身份和设定" }
                }
            }
        }

        binding?.llBackgroundSetting?.setOnClickListener {
            if (!isAdded || context == null) return@setOnClickListener
            val path = prefs().getString(App.KEY_CHAT_BACKGROUND_PATH, null)
            val hasBackground = !path.isNullOrBlank() && File(path).exists()
            
            if (hasBackground) {
                ConfirmDialog(
                    title = "聊天底图",
                    message = "选择新图片或恢复默认背景？",
                    positiveText = "选择图片",
                    negativeText = "恢复默认",
                    onConfirm = {
                        pickChatBackground.launch("image/*")
                    },
                    onNegative = {
                        clearChatBackground()
                    }
                ).show(childFragmentManager, "BackgroundOptionsDialog")
            } else {
                showBackgroundOptionsDialog()
            }
        }

        binding?.llBubbleSetting?.setOnClickListener {
            showToast("气泡设置")
        }

        binding?.llVoiceSetting?.setOnClickListener {
            showToast("语音设置")
        }

        binding?.llReplySetting?.setOnClickListener {
            showToast("回复设置")
        }

        binding?.llImageSetting?.setOnClickListener {
            showToast("配图设置")
        }

        binding?.llStatusBarSetting?.setOnClickListener {
            showToast("状态栏设置")
        }

        binding?.llBackup?.setOnClickListener {
            showBackupRestoreDialog()
        }

        binding?.llCheckUpdate?.setOnClickListener {
            val activity = activity as? AppCompatActivity ?: return@setOnClickListener
            AppUpdateManager.runManualCheck(activity)
        }

        binding?.llManualUpdate?.setOnClickListener {
            AppUpdateManager.openManualUpdatePage(requireContext())
        }
    }

    private fun updateDarkModeSwitch() {
        if (_binding == null) return

        val prefs = requireContext().applicationContext.getSharedPreferences(
            App.PREFS_NAME,
            Context.MODE_PRIVATE
        )
        val savedMode = prefs.getInt(
            App.KEY_NIGHT_MODE,
            AppCompatDelegate.MODE_NIGHT_YES
        )
        val isNightMode = savedMode == AppCompatDelegate.MODE_NIGHT_YES

        binding?.switchDarkMode?.apply {
            setOnCheckedChangeListener(null)
            isChecked = isNightMode
            setOnCheckedChangeListener { _, isChecked ->
                if (isInitializing) return@setOnCheckedChangeListener

                try {
                    val mode = if (isChecked) {
                        AppCompatDelegate.MODE_NIGHT_YES
                    } else {
                        AppCompatDelegate.MODE_NIGHT_NO
                    }
                    prefs.edit().putInt(App.KEY_NIGHT_MODE, mode).apply()
                    AppCompatDelegate.setDefaultNightMode(mode)
                    if (isChecked) {
                        showToast("已开启深色模式")
                    } else {
                        showToast("已开启浅色模式")
                    }
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Error toggling dark mode", e)
                }
            }
        }
        isInitializing = false
    }

    private fun setupSwitchListeners() {
    }

    private fun bindMemoryAndAvatarPrefs() {
        val seek = binding?.seekMemoryContext ?: return
        val mem = prefs().getInt(App.KEY_MEMORY_CONTEXT_COUNT, 5).coerceIn(0, 10)
        seek.setOnSeekBarChangeListener(null)
        seek.progress = mem
        binding?.tvMemoryStrengthValue?.text = mem.toString()
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                binding?.tvMemoryStrengthValue?.text = progress.toString()
                if (fromUser) {
                    prefs().edit().putInt(App.KEY_MEMORY_CONTEXT_COUNT, progress).apply()
                }
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        binding?.switchChatShowAvatars?.setOnCheckedChangeListener(null)
        binding?.switchChatShowAvatars?.isChecked = prefs().getBoolean(App.KEY_CHAT_SHOW_AVATARS, true)
        binding?.switchChatShowAvatars?.setOnCheckedChangeListener { _, checked ->
            prefs().edit().putBoolean(App.KEY_CHAT_SHOW_AVATARS, checked).apply()
        }
    }

    private fun refreshChatBackgroundSummary() {
        if (_binding == null) return
        val path = prefs().getString(App.KEY_CHAT_BACKGROUND_PATH, null)
        val ok = !path.isNullOrBlank() && File(path).exists()
        binding?.tvBackgroundSummary?.text =
            if (ok) "已设置底图（铺满聊天区）" else "未设置（使用默认背景）"
    }

    private fun clearChatBackground() {
        val path = prefs().getString(App.KEY_CHAT_BACKGROUND_PATH, null)
        AvatarStorage.deleteFileIfExists(path)
        prefs().edit().remove(App.KEY_CHAT_BACKGROUND_PATH).apply()
        refreshChatBackgroundSummary()
        showToast("已恢复默认背景")
    }

    private fun showBackgroundOptionsDialog() {
        ConfirmDialog(
            title = "聊天底图",
            message = "选择图片作为聊天背景？",
            positiveText = "选择图片",
            negativeText = "取消",
            onConfirm = {
                pickChatBackground.launch("image/*")
            }
        ).show(childFragmentManager, "BackgroundDialog")
    }

    private fun showInputDialog(
        title: String,
        currentValue: String,
        allowEmpty: Boolean = false,
        isMultiline: Boolean = false,
        onConfirm: (String) -> Unit
    ) {
        try {
            if (!isAdded || context == null) return
            
            showTextInputPrompt(
                title = title,
                initialValue = currentValue,
                allowEmpty = allowEmpty,
                isMultiline = isMultiline,
                maxLength = DEFAULT_INPUT_MAX_LENGTH,
                onConfirm = onConfirm
            )
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to show dialog", e)
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

    /** 当前系统仍应向用户申请的存储类权限（未授权则加入列表） */
    private fun missingStoragePermissions(): Array<String> {
        if (!isAdded || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return emptyArray()
        val ctx = requireContext()
        val need = LinkedHashSet<String>()
        when {
            Build.VERSION.SDK_INT >= 33 -> {
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_MEDIA_IMAGES) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    need.add(Manifest.permission.READ_MEDIA_IMAGES)
                }
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_MEDIA_VIDEO) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    need.add(Manifest.permission.READ_MEDIA_VIDEO)
                }
            }
            else -> {
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_EXTERNAL_STORAGE) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    need.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    need.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        }
        return need.toTypedArray()
    }

    /**
     * 进入设置 / 回到设置页时自动弹出系统授权框（Android 6+）。
     * - Android 13+：[READ_MEDIA_IMAGES]、[READ_MEDIA_VIDEO]
     * - Android 10～12：[READ_EXTERNAL_STORAGE]、[WRITE_EXTERNAL_STORAGE]
     * - Android 9 及以下：同上（写入主存储 ChatBot/Backups 依赖 WRITE）
     */
    private fun tryRequestStoragePermissionsOnEnter() {
        if (!isAdded) return
        val missing = missingStoragePermissions()
        if (missing.isNotEmpty()) {
            storagePermissionsLauncher.launch(missing)
        }
    }

    private fun ensureLegacyWriteForBackup(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return true
        val writeOk = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        if (writeOk) return true
        storagePermissionsLauncher.launch(
            arrayOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        )
        showToast("请先允许存储权限，以在手机存储/ChatBot 目录保存备份")
        return false
    }

    private fun showBackupRestoreDialog() {
        ConfirmDialog(
            title = "备份与恢复",
            message = "是否备份所有角色数据？\n\n点击\"恢复\"可从备份文件恢复",
            positiveText = "备份",
            negativeText = "恢复",
            onConfirm = {
                performBackup()
            },
            onNegative = {
                pickBackupFile.launch("application/zip")
            }
        ).show(childFragmentManager, "BackupDialog")
    }

    private fun performBackup() {
        val app = requireActivity().application as? App ?: return
        if (!app.isDatabaseInitialized()) {
            showToast("数据库初始化中，请稍后")
            return
        }
        if (!ensureLegacyWriteForBackup()) return

        lifecycleScope.launch {
            try {
                showToast("正在备份...")
                val characterRepository = CharacterRepository(app.database.characterDao())
                val characters = withContext(Dispatchers.IO) {
                    characterRepository.allCharacters.first()
                }

                val result = BackupManager.createBackup(requireContext(), characters)
                result.onSuccess { info ->
                    if (!isAdded) return@launch
                    Toast.makeText(
                        requireContext(),
                        "备份成功\n${info.userVisiblePath}",
                        Toast.LENGTH_LONG
                    ).show()
                }.onFailure { e ->
                    showToast("备份失败：${e.message}")
                }
            } catch (e: Exception) {
                showToast("备份失败：${e.message}")
            }
        }
    }

    private fun performRestore(uri: Uri) {
        val app = requireActivity().application as? App ?: return
        if (!app.isDatabaseInitialized()) {
            showToast("数据库初始化中，请稍后")
            return
        }

        lifecycleScope.launch {
            try {
                showToast("正在恢复...")
                
                val tempFile = File(requireContext().cacheDir, "temp_backup.zip")
                requireContext().contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val result = BackupManager.restoreBackup(requireContext(), tempFile, app.database.characterDao())

                tempFile.delete()

                result.onSuccess { summary ->
                    if (isAdded && _binding != null) {
                        loadUserAvatar()
                        loadUserProfileTexts()
                    }
                    val userLine =
                        if (summary.userProfileRestored) "，个人资料已恢复" else ""
                    showToast("恢复成功！\n共恢复 ${summary.characterCount} 个角色$userLine")
                }.onFailure { e ->
                    showToast("恢复失败：${e.message}")
                }
            } catch (e: Exception) {
                showToast("恢复失败：${e.message}")
            }
        }
    }

    private fun showToast(message: String) {
        if (isAdded && context != null) {
            try {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to show toast", e)
            }
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
