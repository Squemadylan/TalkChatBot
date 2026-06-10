package com.example.chatbot.ui.chat

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.chatbot.App
import com.example.chatbot.R
import com.example.chatbot.data.repository.ApiConfigRepository
import com.example.chatbot.data.model.Message
import com.example.chatbot.data.repository.CharacterRepository
import com.example.chatbot.data.repository.MessageRepository
import com.example.chatbot.databinding.FragmentChatBinding
import com.example.chatbot.ui.common.ConfirmDialog
import com.example.chatbot.util.AvatarStorage
import com.example.chatbot.util.UserPromptPlaceholders
import com.example.chatbot.util.VoiceHelper
import com.example.chatbot.viewmodel.ChatViewModel
import com.example.chatbot.viewmodel.ChatViewModelFactory
import io.noties.markwon.Markwon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding

    private var viewModel: ChatViewModel? = null
    private var messageAdapter: MessageAdapter? = null
    private var memoryAdapter: MemoryCharacterAdapter? = null
    private var markwon: Markwon? = null

    private var characterId: Long = 0
    private var lastAppliedArgCharacterId: Long = Long.MIN_VALUE
    private var characterPrompt: String = ""
    private var characterAvatarPath: String = ""
    private var characterDisplayName: String = ""
    private var characterOpeningGreeting: String = ""
    private var hasSentGreeting: Boolean = false
    @Volatile
    private var greetingInFlight: Boolean = false
    private var userDisplayName: String = "用户"
    private var userPersona: String = ""

    private var hubRowsCache: List<MemoryHubRow> = emptyList()
    private var searchQuery: String = ""
    private var isSearchMode: Boolean = false
    private var chatSearchQuery: String = ""
    private var isVoiceInputActive: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return try {
            _binding = FragmentChatBinding.inflate(inflater, container, false)
            binding?.root
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to inflate binding", e)
            if (isAdded) {
                Toast.makeText(requireContext(), "聊天页面加载失败", Toast.LENGTH_SHORT).show()
            }
            null
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (_binding == null) {
            if (isAdded) {
                Toast.makeText(requireContext(), "聊天页面加载失败", Toast.LENGTH_SHORT).show()
            }
            return
        }

        markwon = Markwon.create(requireContext())

        initializeViewModel()
        applyCharacterContextFromArgs(isInitial = true)
        setupRecyclerForCurrentMode()
        setupSendButton()
        setupManageButton()
        setupSearchField()
        setupBackNavigation()
        setupVoiceInput()
        observeData()
    }

    /** 与系统返回键一致：弹出当前聊天页（由 NavHostFragment 处理物理返回，此处仅左上角按钮） */
    private fun navigateBackFromChat() {
        if (!isAdded) return
        val nav = findNavController()
        if (!nav.popBackStack()) {
            nav.navigate(R.id.characterFragment)
        }
    }

    private fun setupBackNavigation() {
        binding?.btnBack?.setOnClickListener { navigateBackFromChat() }
    }

    private fun setupSearchField() {
        binding?.btnSearchChat?.setOnClickListener {
            isSearchMode = !isSearchMode
            binding?.etSearch?.visibility = if (isSearchMode) View.VISIBLE else View.GONE
            if (!isSearchMode) {
                binding?.etSearch?.text?.clear()
                chatSearchQuery = ""
                submitChatSearchResults()
            } else {
                binding?.etSearch?.requestFocus()
            }
        }
        binding?.etSearch?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString()?.trim().orEmpty()
                if (characterId == 0L) {
                    searchQuery = text.lowercase(Locale.getDefault())
                    submitHubList()
                } else if (isSearchMode) {
                    chatSearchQuery = text
                    submitChatSearchResults()
                }
            }
        })
    }

    private fun submitChatSearchResults() {
        if (characterId == 0L) return
        if (chatSearchQuery.isEmpty()) {
            viewModel?.setActiveCharacterId(characterId)
        } else {
            viewModel?.searchMessagesInChat(characterId, chatSearchQuery)
        }
    }

    private fun setupVoiceInput() {
        binding?.btnVoiceInput?.visibility = View.VISIBLE
        binding?.btnVoiceInput?.setOnClickListener {
            if (isVoiceInputActive) {
                isVoiceInputActive = false
                binding?.btnVoiceInput?.alpha = 1.0f
                return@setOnClickListener
            }
            isVoiceInputActive = true
            binding?.btnVoiceInput?.alpha = 0.5f
            try {
                startActivityForResult(VoiceHelper.createSpeechRecognizerIntent(), REQUEST_SPEECH_RECOGNIZER)
            } catch (e: Exception) {
                isVoiceInputActive = false
                binding?.btnVoiceInput?.alpha = 1.0f
                showToast("语音识别不可用")
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_SPEECH_RECOGNIZER && resultCode == android.app.Activity.RESULT_OK) {
            val results = data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            val spoken = results?.firstOrNull()?.trim()
            if (!spoken.isNullOrBlank()) {
                binding?.etMessage?.setText(spoken)
                binding?.etMessage?.setSelection(spoken.length)
            }
        }
        isVoiceInputActive = false
        binding?.btnVoiceInput?.alpha = 1.0f
    }

    private fun speakReply(text: String) {
        VoiceHelper.init(requireContext()) { success, error ->
            if (!isAdded || _binding == null) return@init
            if (success) {
                val prefs = requireContext().getSharedPreferences(App.PREFS_NAME, android.content.Context.MODE_PRIVATE)
                val speed = prefs.getFloat(App.KEY_VOICE_SPEED, 1.0f)
                VoiceHelper.setSpeed(speed)
                VoiceHelper.speak(text) {}
            } else {
                showToast(error ?: "语音引擎初始化失败，请在系统设置中安装语音引擎")
                // 尝试打开系统 TTS 设置引导用户
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        VoiceHelper.openTtsSettings(requireContext())
                    } catch (_: Exception) {}
                }, 2000)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyCharacterContextFromArgs(isInitial = false)
        applyChatBackground()
        applyMessageAdapterDisplay()
        if (characterId == 0L) {
            applyUserPlaceholdersToMemoryAdapter()
            binding?.recyclerView?.post { memoryAdapter?.notifyDataSetChanged() }
        }
    }

    private fun readArgCharacterId(): Long =
        arguments?.getLong(ARG_CHARACTER_ID, 0L) ?: 0L

    private fun applyCharacterContextFromArgs(isInitial: Boolean) {
        val argId = readArgCharacterId()
        if (argId == lastAppliedArgCharacterId && !isInitial) return
        lastAppliedArgCharacterId = argId
        characterId = argId
        viewModel?.setActiveCharacterId(characterId)
        if (_binding == null) return

        if (characterId == 0L) {
            binding?.layoutInputBar?.visibility = View.GONE
            characterPrompt = ""
            characterAvatarPath = ""
            characterDisplayName = ""
            setupRecyclerForCurrentMode()
            submitHubList()
        } else {
            binding?.layoutInputBar?.visibility = View.VISIBLE
            loadCharacterSystemPrompt()
            setupRecyclerForCurrentMode()
        }
    }

    private fun setupRecyclerForCurrentMode() {
        if (_binding == null) return
        if (characterId == 0L) {
            if (memoryAdapter == null) {
                memoryAdapter = MemoryCharacterAdapter(
                    onOpenChat = { id ->
                        findNavController().navigate(
                            R.id.chatFragment,
                            Bundle().apply { putLong(ARG_CHARACTER_ID, id) },
                            NavOptions.Builder().setLaunchSingleTop(true).build()
                        )
                    },
                    onMenu = { row, anchor -> showMemoryRowMenu(row, anchor) }
                )
            }
            binding?.recyclerView?.layoutManager = LinearLayoutManager(requireContext())
            binding?.recyclerView?.adapter = memoryAdapter
        } else {
            val renderer = markwon ?: return
            if (messageAdapter == null) {
                messageAdapter = MessageAdapter(
                    renderer,
                    { viewModel?.streamingAssistantMessageIdForUi() ?: -1L },
                    { message, anchor -> showMessageLongClickMenu(message, anchor) }
                )
            }
            binding?.recyclerView?.layoutManager = LinearLayoutManager(requireContext())
            binding?.recyclerView?.itemAnimator = null
            binding?.recyclerView?.setHasFixedSize(true)
            binding?.recyclerView?.adapter = messageAdapter
            applyMessageAdapterDisplay()
        }
    }

    private fun showMessageLongClickMenu(message: Message, anchor: View) {
        val popup = PopupMenu(requireContext(), anchor, android.view.Gravity.TOP or android.view.Gravity.START)
        popup.menu.add(0, MENU_COPY_MESSAGE, 0, "复制")
        popup.menu.add(0, MENU_STAR_MESSAGE, 1, if (message.isStarred) "取消收藏" else "收藏")
        popup.menu.add(0, MENU_DELETE_MESSAGE, 2, "删除")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_COPY_MESSAGE -> {
                    val text = UserPromptPlaceholders.apply(
                        message.content,
                        userDisplayName,
                        userPersona,
                        characterDisplayName
                    )
                    val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("消息内容", text)
                    clipboard.setPrimaryClip(clip)
                    showToast("已复制到剪贴板")
                    true
                }
                MENU_STAR_MESSAGE -> {
                    viewModel?.toggleStarred(message.id, !message.isStarred)
                    showToast(if (message.isStarred) "已取消收藏" else "已收藏")
                    true
                }
                MENU_DELETE_MESSAGE -> {
                    anchor.post {
                        if (!isAdded) return@post
                        ConfirmDialog(
                            title = "删除消息",
                            message = "确定删除这条消息？",
                            positiveText = "删除",
                            onConfirm = {
                                viewModel?.deleteMessage(message.id)
                                showToast("已删除")
                            }
                        ).show(childFragmentManager, "DeleteMessage_${message.id}")
                    }
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun initializeViewModel() {
        try {
            val app = requireActivity().application as? App
            if (app == null) {
                showToast("应用初始化失败")
                return
            }

            if (!app.isDatabaseInitialized()) {
                showToast("数据库初始化中，请稍后")
                return
            }

            val messageRepository = MessageRepository(app.database.messageDao())
            val apiConfigRepository = ApiConfigRepository(app.database.apiConfigDao())
            val characterRepository = CharacterRepository(app.database.characterDao())

            viewModel = ViewModelProvider(
                requireActivity(),
                ChatViewModelFactory(messageRepository, apiConfigRepository, characterRepository)
            )[ChatViewModel::class.java]
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to initialize ViewModel", e)
            showToast("ViewModel初始化失败")
        }
    }

    private fun loadCharacterSystemPrompt() {
        if (characterId == 0L) {
            characterPrompt = ""
            characterAvatarPath = ""
            characterDisplayName = ""
            characterOpeningGreeting = ""
            hasSentGreeting = false
            return
        }
        val app = activity?.application as? App ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val character = withContext(Dispatchers.IO) {
                    try {
                        app.database.characterDao().getCharacterById(characterId)
                    } catch (_: Exception) {
                        null
                    }
                }
                characterPrompt = character?.systemPromptForChat().orEmpty()
                characterAvatarPath = character?.avatar.orEmpty()
                characterDisplayName = character?.name?.trim().orEmpty()
                characterOpeningGreeting = character?.openingGreeting?.trim().orEmpty()
                withContext(Dispatchers.Main) {
                    applyMessageAdapterDisplay()
                    checkAndSendGreeting()
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "loadCharacterSystemPrompt failed", e)
            }
        }
    }

    private fun checkAndSendGreeting() {
        if (characterOpeningGreeting.isBlank() || characterId == 0L) return
        if (viewModel == null || hasSentGreeting || greetingInFlight) return

        val app = activity?.application as? App ?: return
        if (!app.isDatabaseInitialized()) return
        greetingInFlight = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val hasMessages = withContext(Dispatchers.IO) {
                    runCatching {
                        app.database.messageDao().getMessageCount(characterId) > 0
                    }.getOrDefault(false)
                }
                if (!hasMessages && !hasSentGreeting) {
                    hasSentGreeting = true
                    viewModel?.sendGreetingMessage(
                        characterId,
                        characterOpeningGreeting,
                        characterPrompt,
                        requireContext()
                    )
                }
            } finally {
                greetingInFlight = false
            }
        }
    }

    private fun prefs() =
        requireContext().applicationContext.getSharedPreferences(App.PREFS_NAME, android.content.Context.MODE_PRIVATE)

    private fun applyChatBackground() {
        if (_binding == null || !isAdded) return
        val path = prefs().getString(App.KEY_CHAT_BACKGROUND_PATH, null)
        val iv = binding?.ivChatBackground ?: return
        if (path.isNullOrBlank() || !File(path).exists()) {
            iv.visibility = View.GONE
            iv.setImageDrawable(null)
            return
        }
        iv.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
        AvatarStorage.loadInto(iv, path)
        iv.visibility = View.VISIBLE
    }

    private fun applyMessageAdapterDisplay() {
        if (_binding == null || characterId == 0L || !isAdded) return
        applyUserPlaceholdersToMessageAdapter()
        val show = prefs().getBoolean(App.KEY_CHAT_SHOW_AVATARS, true)
        val userPath = prefs().getString(App.KEY_USER_AVATAR_PATH, null)
        val charPath = characterAvatarPath.ifBlank { null }
        val bubbleStyle = prefs().getInt(
            App.KEY_CHAT_BUBBLE_STYLE,
            App.CHAT_BUBBLE_STYLE_DEFAULT
        )
        messageAdapter?.updateChatDisplay(show, charPath, userPath, bubbleStyle)
    }

    private fun applyUserPlaceholdersToMessageAdapter() {
        if (_binding == null || characterId == 0L || !isAdded) return
        userDisplayName = prefs().getString(App.KEY_USER_DISPLAY_NAME, null)?.trim()?.takeIf { it.isNotEmpty() }
            ?: "用户"
        userPersona = prefs().getString(App.KEY_USER_PERSONA, null)?.trim().orEmpty()
        messageAdapter?.updateUserPlaceholders(userDisplayName, userPersona, characterDisplayName)
    }

    private fun applyUserPlaceholdersToMemoryAdapter() {
        if (_binding == null || characterId != 0L || !isAdded) return
        userDisplayName = prefs().getString(App.KEY_USER_DISPLAY_NAME, null)?.trim()?.takeIf { it.isNotEmpty() }
            ?: "用户"
        userPersona = prefs().getString(App.KEY_USER_PERSONA, null)?.trim().orEmpty()
        memoryAdapter?.updateUserPlaceholders(userDisplayName, userPersona)
    }

    private fun setupSendButton() {
        binding?.btnSend?.setOnClickListener {
            val message = binding?.etMessage?.text?.toString()?.trim() ?: ""
            if (message.isNotEmpty()) {
                checkApiConfigAndSend(message)
            } else {
                showToast("请输入消息")
            }
        }
    }

    private fun checkApiConfigAndSend(message: String) {
        if (characterId == 0L) return
        if (viewModel == null) {
            showToast("请先配置大模型API")
            return
        }

        if (_binding == null) {
            showToast("聊天页面未正确加载")
            return
        }

        binding?.etMessage?.text?.clear()
        viewModel?.sendMessage(characterId, message, characterPrompt, requireContext())
    }

    private fun setupManageButton() {
        binding?.btnDelete?.setOnClickListener {
            if (characterId != 0L) {
                showChatManageMenu(it)
                return@setOnClickListener
            }
            ConfirmDialog(
                title = "清空所有回忆",
                message = "将删除所有角色下的全部聊天记录，且不可恢复。确定继续？",
                positiveText = "清空",
                onConfirm = {
                    viewModel?.deleteAllMessages(requireContext())
                    showToast("已清空全部回忆")
                }
            ).show(childFragmentManager, "ClearAllChatDialog")
        }
    }

    private fun showChatManageMenu(anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add(0, MENU_SAVE_LONG_MEMORY, 0, "保存长久记忆")
        popup.menu.add(0, MENU_EXPORT_CHAT, 1, "导出聊天记录")
        popup.menu.add(0, MENU_DELETE_CHAT_MESSAGES, 2, "删除聊天记录")
        popup.menu.add(0, MENU_DELETE_LONG_MEMORY, 3, "删除长久记忆")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_SAVE_LONG_MEMORY -> {
                    viewModel?.saveLongTermMemoryNow(characterId, requireContext())
                    true
                }
                MENU_EXPORT_CHAT -> {
                    showExportDialog()
                    true
                }
                MENU_DELETE_CHAT_MESSAGES -> {
                    ConfirmDialog(
                        title = "删除聊天记录",
                        message = "确定删除与该角色的全部聊天记录？长久记忆会保留。",
                        positiveText = "删除",
                        onConfirm = {
                            viewModel?.deleteChatMessagesOnlyForCharacter(characterId)
                            showToast("已删除聊天记录")
                        }
                    ).show(childFragmentManager, "DeleteChatMessages_$characterId")
                    true
                }
                MENU_DELETE_LONG_MEMORY -> {
                    ConfirmDialog(
                        title = "删除长久记忆",
                        message = "确定删除该角色已保存的长久记忆？聊天记录会保留。",
                        positiveText = "删除",
                        onConfirm = {
                            viewModel?.deleteLongTermMemoryForCharacter(characterId, requireContext())
                        }
                    ).show(childFragmentManager, "DeleteLongMemory_$characterId")
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showExportDialog() {
        if (characterId == 0L) return
        val options = arrayOf("导出为 Markdown", "导出为 TXT")
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("导出聊天记录")
            .setItems(options) { _, which ->
                val format = if (which == 0) "md" else "txt"
                viewModel?.exportChat(characterId, characterDisplayName, format)
            }
            .show()
    }

    private fun showMemoryRowMenu(row: MemoryHubRow, anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add(0, MENU_CLEAR_CHARACTER, 0, "清空该角色回忆")
        popup.menu.add(0, MENU_DELETE_CHARACTER, 1, "删除角色（含全部回忆）")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_CLEAR_CHARACTER -> {
                    anchor.post {
                        if (!isAdded) return@post
                        ConfirmDialog(
                            title = "清空回忆",
                            message = "确定删除「${row.character.name}」的全部聊天记录？（角色保留）",
                            positiveText = "清空",
                            onConfirm = {
                                viewModel?.deleteMessagesForCharacter(row.character.id, requireContext())
                                showToast("已清空该角色回忆")
                            }
                        ).show(childFragmentManager, "ClearMemory_${row.character.id}")
                    }
                    true
                }
                MENU_DELETE_CHARACTER -> {
                    anchor.post {
                        if (!isAdded) return@post
                        ConfirmDialog(
                            title = "删除角色",
                            message = "将永久删除角色「${row.character.name}」及其全部聊天记录，不可恢复。确定？",
                            positiveText = "删除",
                            onConfirm = {
                                viewModel?.deleteCharacterWithMessages(row.character.id, requireContext())
                                showToast("已删除角色")
                            }
                        ).show(childFragmentManager, "DeleteCharacter_${row.character.id}")
                    }
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun filteredHubRows(): List<MemoryHubRow> {
        if (searchQuery.isEmpty()) return hubRowsCache
        return hubRowsCache.filter { row ->
            val name = row.character.name.lowercase(Locale.getDefault())
            val snippet = row.lastMessage?.content?.lowercase(Locale.getDefault()).orEmpty()
            name.contains(searchQuery) || snippet.contains(searchQuery)
        }
    }

    private fun submitHubList() {
        if (characterId != 0L) return
        applyUserPlaceholdersToMemoryAdapter()
        memoryAdapter?.submitList(filteredHubRows())
    }

    private fun observeData() {
        val vm = viewModel ?: return

        vm.memoryHubRows.observe(viewLifecycleOwner) { rows ->
            if (!isAdded || _binding == null) return@observe
            hubRowsCache = rows ?: emptyList()
            if (characterId == 0L) {
                submitHubList()
            }
        }

        vm.assistantMarkdownRefresh.observe(viewLifecycleOwner) { messageId ->
            if (!isAdded || _binding == null) return@observe
            if (messageId > 0L) {
                messageAdapter?.refreshAssistantMarkdown(messageId)
            }
        }

        vm.assistantMessageToSpeak.observe(viewLifecycleOwner) { text ->
            if (!isAdded || _binding == null) return@observe
            text?.let { speakReply(it) }
        }

        vm.messagesForActiveCharacter.observe(viewLifecycleOwner) { messages ->
            if (!isAdded || _binding == null) return@observe
            if (characterId == 0L) return@observe

            try {
                applyUserPlaceholdersToMessageAdapter()
                messageAdapter?.submitList(messages)
                if (messages.isNotEmpty()) {
                    binding?.recyclerView?.scrollToPosition(messages.size - 1)
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error updating messages", e)
            }
        }

        vm.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (!isAdded || _binding == null) return@observe

            try {
                binding?.progressBar?.visibility = View.GONE
                binding?.btnSend?.isEnabled = !isLoading
                binding?.btnDelete?.isEnabled = !isLoading
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error updating loading state", e)
            }
        }

        vm.errorMessage.observe(viewLifecycleOwner) { error ->
            error?.let {
                if (isAdded && _binding != null) {
                    showToast(it)
                }
            }
        }

        vm.searchResults.observe(viewLifecycleOwner) { results ->
            if (!isAdded || _binding == null) return@observe
            if (characterId == 0L) return@observe
            if (isSearchMode && chatSearchQuery.isNotEmpty()) {
                messageAdapter?.submitList(results)
                if (results.isNotEmpty()) {
                    binding?.recyclerView?.scrollToPosition(results.size - 1)
                }
            }
        }

        vm.exportChatEvent.observe(viewLifecycleOwner) { event ->
            if (!isAdded || _binding == null) return@observe
            val payload = event?.getContentIfNotHandled() ?: return@observe
            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_SUBJECT, payload.subject)
                putExtra(android.content.Intent.EXTRA_TEXT, payload.content)
            }
            startActivity(
                android.content.Intent.createChooser(shareIntent, payload.chooserTitle)
            )
        }
    }

    private fun showToast(message: String) {
        if (isAdded && context != null) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        messageAdapter = null
        memoryAdapter = null
        markwon = null
    }

    companion object {
        private const val TAG = "ChatFragment"
        const val ARG_CHARACTER_ID = "characterId"
        private const val MENU_CLEAR_CHARACTER = 1
        private const val MENU_DELETE_CHARACTER = 2
        private const val MENU_COPY_MESSAGE = 3
        private const val MENU_STAR_MESSAGE = 8
        private const val MENU_DELETE_MESSAGE = 4
        private const val MENU_SAVE_LONG_MEMORY = 5
        private const val MENU_DELETE_CHAT_MESSAGES = 6
        private const val MENU_DELETE_LONG_MEMORY = 7
        private const val MENU_EXPORT_CHAT = 9
        private const val REQUEST_SPEECH_RECOGNIZER = 1001
    }
}
