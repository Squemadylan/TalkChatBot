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

    private lateinit var binding: FragmentChatBinding
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
        binding = FragmentChatBinding.inflate(inflater, container, false)

        characterId = arguments?.getLong("characterId") ?: 0

        val messageRepository = MessageRepository((requireActivity().application as App).database.messageDao())
        val apiConfigRepository = ApiConfigRepository((requireActivity().application as App).database.apiConfigDao())

        viewModel = ViewModelProvider(this) {
            ChatViewModel(messageRepository, apiConfigRepository)
        }.get(ChatViewModel::class.java)

        setupRecyclerView()
        setupSendButton()
        setupPagination()
        setupSearchAndDelete()

        viewModel.getMessages(characterId).observe(viewLifecycleOwner) { messages ->
            adapter.submitList(messages)
            // 更新分页信息
            totalPages = if (messages.isEmpty()) 1 else (messages.size + pageSize - 1) / pageSize
            binding.tvPageInfo.text = "$currentPage/$totalPages"
            
            if (messages.isNotEmpty()) {
                binding.recyclerView.scrollToPosition(messages.size - 1)
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.btnSend.isEnabled = !isLoading
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            }
        }

        return binding.root
    }

    private fun setupRecyclerView() {
        adapter = MessageAdapter()
        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        binding.recyclerView.adapter = adapter
    }

    private fun setupSendButton() {
        binding.btnSend.setOnClickListener {
            val message = binding.etMessage.text.toString().trim()
            if (message.isNotEmpty()) {
                binding.etMessage.text.clear()
                viewModel.sendMessage(characterId, message, characterPrompt)
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
            // 切换每页显示数量
            pageSize = if (pageSize == 10) 20 else 10
            binding.btnPageSize.text = "$pageSize条/页"
            showToast("每页显示$pageSize条")
        }
    }

    private fun updatePageInfo() {
        binding.tvPageInfo.text = "$currentPage/$totalPages"
    }

    private fun setupSearchAndDelete() {
        binding.btnDelete.setOnClickListener {
            showToast("删除功能")
        }

        binding.etSearch.setOnClickListener {
            showToast("搜索回忆")
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}
