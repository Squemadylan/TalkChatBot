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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.chatbot.App
import com.example.chatbot.R
import com.example.chatbot.data.repository.CharacterRepository
import com.example.chatbot.databinding.FragmentSettingBinding
import com.example.chatbot.ui.MainActivity
import com.example.chatbot.ui.common.ConfirmDialog
import com.example.chatbot.ui.common.DEFAULT_INPUT_MAX_LENGTH
import com.example.chatbot.ui.common.showTextInputPrompt
import com.example.chatbot.util.AppUpdateManager
import com.example.chatbot.util.AvatarStorage
import com.example.chatbot.util.BackupManager
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
        refreshPreferenceSummaries()
    }

    override fun onResume() {
        super.onResume()
        refreshChatBackgroundSummary()
        refreshPreferenceSummaries()
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
            showBubbleStyleDialog()
        }

        binding?.llVoiceSetting?.setOnClickListener {
            showToast("语音能力即将支持")
        }

        binding?.llReplySetting?.setOnClickListener {
            showReplyStyleDialog()
        }

        binding?.llImageSetting?.setOnClickListener {
            showToast("配图能力即将支持")
        }

        binding?.llStatusBarSetting?.setOnClickListener {
            showStatusBarDialog()
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

        // v2.0 4 层记忆开关
        binding?.switchFourTierMemory?.setOnCheckedChangeListener(null)
        binding?.switchFourTierMemory?.isChecked =
            com.example.chatbot.memory.MemoryConfig.isEnabled(requireContext().applicationContext)
        binding?.switchFourTierMemory?.setOnCheckedChangeListener { _, checked ->
            com.example.chatbot.memory.MemoryConfig.setEnabled(requireContext().applicationContext, checked)
            showToast(if (checked) "已开启 4 层记忆" else "已关闭 4 层记忆")
        }

        // offload mild 阈值
        val ctx = requireContext().applicationContext
        val mildNow = (com.example.chatbot.memory.MemoryConfig.offloadMildRatio(ctx) * 100).toInt()
        binding?.seekOffloadMild?.setOnSeekBarChangeListener(null)
        binding?.seekOffloadMild?.progress = mildNow.coerceIn(20, 90) - 20
        binding?.tvOffloadMildValue?.text = String.format("%.2f", mildNow / 100f)
        binding?.seekOffloadMild?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val v = (progress + 20) / 100f
                binding?.tvOffloadMildValue?.text = String.format("%.2f", v)
                if (fromUser) {
                    com.example.chatbot.memory.MemoryConfig.prefs(ctx).edit()
                        .putFloat(com.example.chatbot.memory.MemoryConfig.KEY_OFFLOAD_MILD_RATIO, v)
                        .apply()
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // embedding 状态
        refreshEmbedStatus()

        binding?.tvEmbedBackendValue?.setOnClickListener { showEmbedBackendPicker() }
        binding?.tvEmbedRemoteModel?.setOnClickListener { showRemoteModelEditor() }

        binding?.btnOpenMemoryViewer?.setOnClickListener {
            startActivity(android.content.Intent(requireContext(),
                com.example.chatbot.ui.memory.MemoryViewerActivity::class.java))
        }

        binding?.btnForceRunAllLayers?.setOnClickListener { showForceRunPicker() }
    }

    private fun showForceRunPicker() {
        val ctx = requireContext().applicationContext
        val app = (ctx as? com.example.chatbot.App) ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val chars = runCatching { app.characterRepository.allCharactersOnce() }
                .getOrDefault(emptyList())
            if (chars.isEmpty()) {
                showToast("还没有角色，先去添加一个吧")
                return@launch
            }
            val labels = chars.map { "${it.name}（id=${it.id}）" }
            val ids = chars.map { it.id }
            val app = ctx as com.example.chatbot.App
            AlertDialog.Builder(requireContext())
                .setTitle("选一个角色立即刷新 L1/L2/L3")
                .setItems(labels.toTypedArray()) { dlg, which ->
                    dlg.dismiss()
                    runForceRunFor(ctx, ids[which], labels[which])
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun runForceRunFor(ctx: android.content.Context, characterId: Long, label: String) {
        showToast("开始刷 $label …（会在后台跑 LLM，约几秒到十几秒）")
        val app = ctx as com.example.chatbot.App
        viewLifecycleOwner.lifecycleScope.launch {
            val cfg = runCatching { app.apiConfigRepository.getApiConfig() }.getOrNull()
            if (cfg == null || cfg.apiKey.isBlank() || cfg.baseUrl.isBlank()) {
                showToast("请先在「API 设置」里填 baseUrl/apiKey")
                return@launch
            }
            com.example.chatbot.memory.MemoryPipeline.forceRunAll(
                context = ctx,
                characterId = characterId,
                apiConfig = cfg,
                onDone = { summary ->
                    showToast(summary.take(240))
                    AlertDialog.Builder(requireContext())
                        .setTitle("执行摘要 · $label")
                        .setMessage(summary)
                        .setPositiveButton("好", null)
                        .setNeutralButton("打开记忆查看") { _, _ ->
                            startActivity(android.content.Intent(ctx,
                                com.example.chatbot.ui.memory.MemoryViewerActivity::class.java))
                        }
                        .show()
                }
            )
        }
    }

    private fun refreshEmbedStatus() {
        if (_binding == null) return
        val ctx = requireContext().applicationContext
        val tag = com.example.chatbot.memory.embed.EmbedderFactory.tag()
        val model = if (tag == "remote") com.example.chatbot.memory.MemoryConfig.embedRemoteModel(ctx) else "(无向量)"
        binding?.tvEmbedStatus?.text = "Embedding 状态：" +
            if (tag == "bm25") "BM25 兜底（向量召回已禁用；先在 API 设置里填 baseUrl/apiKey）"
            else "就绪（远程 · $model）"
        // 后端恒为远程；旧 prefs 里的 "local" / "auto" 已被 MemoryConfig 归一化为 "remote"
        binding?.tvEmbedBackendValue?.text = "远程（固定）"
        binding?.tvEmbedRemoteModel?.text = com.example.chatbot.memory.MemoryConfig.embedRemoteModel(ctx)
        binding?.tvEmbedDim?.text = "向量维度：${com.example.chatbot.memory.MemoryConfig.embedDim(ctx)}" +
            "（变更会触发 vec index 重建）"
    }

    private fun showEmbedBackendPicker() {
        // 不再可选；保留入口只作为「跳到 API 设置」的快捷提示
        showToast("embedding 仅支持远程（复用 Chat API）。请先在「API 设置」里填 baseUrl/apiKey。")
    }

    private fun showRemoteModelEditor() {
        val ctx = requireContext().applicationContext
        val cur = com.example.chatbot.memory.MemoryConfig.embedRemoteModel(ctx)
        val edit = android.widget.EditText(requireContext()).apply {
            setText(cur)
            hint = "BAAI/bge-large-zh-v1.5 / BAAI/bge-m3 / text-embedding-3-small …"
        }
        AlertDialog.Builder(requireContext())
            .setTitle("远程 embedding 模型")
            .setView(edit)
            .setPositiveButton("保存") { _, _ ->
                val v = edit.text.toString().trim().ifBlank { cur }
                com.example.chatbot.memory.MemoryConfig.setEmbedRemoteModel(ctx, v)
                com.example.chatbot.memory.embed.EmbedderFactory.rebuild(ctx)
                com.example.chatbot.memory.vec.VecIndexFactory.resetAll()
                refreshEmbedStatus()
                showToast("已更新为 $v")
            }
            .setNegativeButton("取消", null)
            .show()
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

    private fun refreshPreferenceSummaries() {
        if (_binding == null) return
        val bubbleStyle = prefs().getInt(
            App.KEY_CHAT_BUBBLE_STYLE,
            App.CHAT_BUBBLE_STYLE_DEFAULT
        )
        binding?.tvBubbleSummary?.text = "当前：${bubbleStyleLabel(bubbleStyle)}"

        val replyStyle = prefs().getInt(App.KEY_REPLY_STYLE, App.REPLY_STYLE_STANDARD)
        binding?.tvReplySummary?.text = replyStyleSummary(replyStyle)

        val immersive = prefs().getBoolean(App.KEY_STATUS_BAR_IMMERSIVE, false)
        binding?.tvStatusBarSummary?.text = if (immersive) {
            "沉浸式：透明状态栏，图标跟随主题"
        } else {
            "默认：保留系统状态栏间距，图标跟随主题"
        }
    }

    private fun showBubbleStyleDialog() {
        val options = arrayOf("默认", "紧凑", "圆角", "半透明")
        val current = prefs().getInt(
            App.KEY_CHAT_BUBBLE_STYLE,
            App.CHAT_BUBBLE_STYLE_DEFAULT
        ).coerceIn(options.indices)
        showChoiceDialog("气泡样式", options, current) { which ->
            prefs().edit().putInt(App.KEY_CHAT_BUBBLE_STYLE, which).apply()
            refreshPreferenceSummaries()
            showToast("气泡样式已切换为${options[which]}")
        }
    }

    private fun showReplyStyleDialog() {
        val options = arrayOf("标准", "短回复", "细腻")
        val current = prefs().getInt(
            App.KEY_REPLY_STYLE,
            App.REPLY_STYLE_STANDARD
        ).coerceIn(options.indices)
        showChoiceDialog("回复策略", options, current) { which ->
            prefs().edit().putInt(App.KEY_REPLY_STYLE, which).apply()
            refreshPreferenceSummaries()
            showToast("回复策略已切换为${options[which]}")
        }
    }

    private fun showStatusBarDialog() {
        val options = arrayOf("默认状态栏", "沉浸式状态栏")
        val current = if (prefs().getBoolean(App.KEY_STATUS_BAR_IMMERSIVE, false)) 1 else 0
        showChoiceDialog("状态栏设置", options, current) { which ->
            prefs().edit().putBoolean(App.KEY_STATUS_BAR_IMMERSIVE, which == 1).apply()
            (activity as? MainActivity)?.applyStatusBarSettings()
            refreshPreferenceSummaries()
            showToast("状态栏设置已更新")
        }
    }

    private fun showChoiceDialog(
        title: String,
        options: Array<String>,
        checkedItem: Int,
        onSelected: (Int) -> Unit
    ) {
        if (!isAdded || context == null) return
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setSingleChoiceItems(options, checkedItem.coerceIn(options.indices)) { dialog, which ->
                onSelected(which)
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun bubbleStyleLabel(style: Int): String = when (style) {
        App.CHAT_BUBBLE_STYLE_COMPACT -> "紧凑"
        App.CHAT_BUBBLE_STYLE_ROUNDED -> "圆角"
        App.CHAT_BUBBLE_STYLE_TRANSLUCENT -> "半透明"
        else -> "默认"
    }

    private fun replyStyleSummary(style: Int): String = when (style) {
        App.REPLY_STYLE_SHORT -> "短回复：优先简洁直接"
        App.REPLY_STYLE_DETAILED -> "细腻：增加情绪、细节和承接"
        else -> "标准：自然平衡，不额外约束"
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
            message = "将备份角色、个人资料及配置页中的 API 地址、密钥、模型、温度、最大 Token 等参数。\n\n点击\"恢复\"可从备份文件恢复",
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
                val apiConfig = withContext(Dispatchers.IO) {
                    app.database.apiConfigDao().getApiConfig()
                }

                val result = BackupManager.createBackup(requireContext(), characters, apiConfig)
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

                val result = BackupManager.restoreBackup(
                    requireContext(),
                    tempFile,
                    app.database.characterDao(),
                    app.database.apiConfigDao()
                )

                tempFile.delete()

                result.onSuccess { summary ->
                    if (isAdded && _binding != null) {
                        loadUserAvatar()
                        loadUserProfileTexts()
                    }
                    val extras = buildList {
                        if (summary.userProfileRestored) add("个人资料")
                        if (summary.apiConfigRestored) add("API 配置")
                    }
                    val extraLine = if (extras.isEmpty()) "" else "，${extras.joinToString("、")}已恢复"
                    showToast("恢复成功！\n共恢复 ${summary.characterCount} 个角色$extraLine")
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
