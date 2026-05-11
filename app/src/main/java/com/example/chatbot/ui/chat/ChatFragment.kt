package com.example.chatbot.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.chatbot.App
import com.example.chatbot.R
import com.example.chatbot.data.repository.ApiConfigRepository
import com.example.chatbot.data.repository.MessageRepository
import com.example.chatbot.databinding.FragmentChatBinding
import com.example.chatbot.viewmodel.ChatViewModel

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding ?: throw IllegalStateException("Binding is not initialized or already destroyed")

    private lateinit var viewModel: ChatViewModel
    private lateinit var adapter: MessageAdapter

    private var characterId: Long = 0
    private var characterPrompt: String = ""

    private var currentPage = 1
    private var totalPages = 1
    private var pageSize = 10

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        try {
            _binding = FragmentChatBinding.inflate(inflater, container, false)
        } catch (e: Exception) {
            showError("Failed to inflate layout: ${e.message}")
            return View(requireContext())
        }

        characterId = arguments?.getLong(ARG_CHARACTER_ID, 0) ?: 0

        if (!isAppInitialized()) {
            showError("App initialization failed")
            return binding.root
        }

        initializeViewModel()
        setupRecyclerView()
        setupSendButton()
        setupPagination()
        setupSearchAndDelete()
        observeData()

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
            val messageRepository = MessageRepository(app.database.messageDao())
            val apiConfigRepository = ApiConfigRepository(app.database.apiConfigDao())

            viewModel = ViewModelProvider(this) {
                ChatViewModel(messageRepository, apiConfigRepository)
            }.get(ChatViewModel::class.java)
        } catch (e: Exception) {
            showError("Failed to initialize ViewModel: ${e.message}")
        }
    }

    private fun observeData() {
        if (!::viewModel.isInitialized) return

        viewModel.getMessages(characterId).observe(viewLifecycleOwner) { messages ->
            safeAction {
                adapter.submitList(messages)
                totalPages = if (messages.isEmpty()) 1 else (messages.size + pageSize - 1) / pageSize
                binding.tvPageInfo.text = "$currentPage/$totalPages"

                if (messages.isNotEmpty()) {
                    binding.recyclerView.scrollToPosition(messages.size - 1)
                }
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            safeAction {
                binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
                binding.btnSend.isEnabled = !isLoading
            }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            error?.let {
                safeAction {
                    showToast(it)
                }
            }
        }
    }

    private fun setupRecyclerView() {
        try {
            adapter = MessageAdapter()
            binding.recyclerView.layoutManager = LinearLayoutManager(context)
            binding.recyclerView.adapter = adapter
        } catch (e: Exception) {
            showError("Failed to setup RecyclerView: ${e.message}")
        }
    }

    private fun setupSendButton() {
        binding.btnSend.setOnClickListener {
            val message = binding.etMessage.text.toString().trim()
            if (message.isNotEmpty()) {
                binding.etMessage.text.clear()
                viewModel.sendMessage(characterId, message, characterPrompt, requireContext())
            } else {
                showToast("请输入消息")
            }
        }
    }

    private fun setupPagination() {
        binding.btnFirstPage.setOnClickListener {
            if (currentPage > 1) {
                currentPage = 1
                updatePageInfo()
                showToast("第一页")
            }
        }

        binding.btnPrevPage.setOnClickListener {
            if (currentPage > 1) {
                currentPage--
                updatePageInfo()
                showToast("上一页")
            }
        }

        binding.btnNextPage.setOnClickListener {
            if (currentPage < totalPages) {
                currentPage++
                updatePageInfo()
                showToast("下一页")
            }
        }

        binding.btnLastPage.setOnClickListener {
            if (currentPage < totalPages) {
                currentPage = totalPages
                updatePageInfo()
                showToast("最后一页")
            }
        }

        binding.btnPageSize.setOnClickListener {
            pageSize = if (pageSize == 10) 20 else 10
            binding.btnPageSize.text = "$pageSize条/页"
            showToast("每页显示$pageSize条")
        }
    }

    private fun updatePageInfo() {
        safeAction {
            binding.tvPageInfo.text = "$currentPage/$totalPages"
        }
    }

    private fun setupSearchAndDelete() {
        binding.btnDelete.setOnClickListener {
            showToast("删除功能")
        }

        binding.etSearch.setOnClickListener {
            showToast("搜索回忆")
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
        private const val TAG = "ChatFragment"
        private const val ARG_CHARACTER_ID = "characterId"
    }
}
