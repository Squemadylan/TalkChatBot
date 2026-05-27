package com.example.chatbot.ui.character

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.example.chatbot.App
import com.example.chatbot.R
import com.example.chatbot.data.repository.CharacterRepository
import com.example.chatbot.databinding.FragmentCharacterBinding
import com.example.chatbot.viewmodel.CharacterViewModel

class CharacterFragment : Fragment() {

    private lateinit var binding: FragmentCharacterBinding
    private lateinit var viewModel: CharacterViewModel
    private lateinit var adapter: CharacterAdapter
    
    private var isMyCharactersTab = true
    private var currentPage = 1
    private var totalPages = 1
    private var pageSize = 8

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentCharacterBinding.inflate(inflater, container, false)

        val characterRepository = CharacterRepository((requireActivity().application as App).database.characterDao())
        viewModel = ViewModelProvider(this) {
            CharacterViewModel(characterRepository)
        }.get(CharacterViewModel::class.java)

        setupRecyclerView()
        setupAddButton()
        setupTabButtons()
        setupPagination()
        setupSearchAndFilter()

        return binding.root
    }

    private fun setupRecyclerView() {
        adapter = CharacterAdapter { character ->
            val action = CharacterFragmentDirections.actionCharacterFragmentToChatFragment(character.id)
            findNavController().navigate(action)
        }

        binding.recyclerView.layoutManager = GridLayoutManager(context, 2)
        binding.recyclerView.adapter = adapter

        viewModel.allCharacters.observe(viewLifecycleOwner) { characters ->
            adapter.submitList(characters)
            // 更新分页信息
            totalPages = if (characters.isEmpty()) 1 else (characters.size + pageSize - 1) / pageSize
            binding.tvPageInfo.text = "$currentPage/$totalPages"
        }
    }

    private fun setupAddButton() {
        binding.fabAdd.setOnClickListener {
            val dialog = AddCharacterDialog { character ->
                viewModel.insertCharacter(character)
            }
            dialog.show(childFragmentManager, "AddCharacterDialog")
        }
    }

    private fun setupTabButtons() {
        binding.btnMarket.setOnClickListener {
            isMyCharactersTab = false
            updateTabUI()
            showToast("角色市场功能")
        }

        binding.btnMyCharacters.setOnClickListener {
            isMyCharactersTab = true
            updateTabUI()
        }
    }

    private fun updateTabUI() {
        if (isMyCharactersTab) {
            binding.btnMyCharacters.setBackgroundResource(R.drawable.bg_tab_selected)
            binding.btnMyCharacters.setTextColor(resources.getColor(R.color.text_primary))
            binding.btnMarket.setBackgroundResource(R.drawable.bg_tab_unselected)
            binding.btnMarket.setTextColor(resources.getColor(R.color.text_secondary))
            binding.fabAdd.visibility = View.VISIBLE
        } else {
            binding.btnMarket.setBackgroundResource(R.drawable.bg_tab_selected)
            binding.btnMarket.setTextColor(resources.getColor(R.color.text_primary))
            binding.btnMyCharacters.setBackgroundResource(R.drawable.bg_tab_unselected)
            binding.btnMyCharacters.setTextColor(resources.getColor(R.color.text_secondary))
            binding.fabAdd.visibility = View.GONE
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
            pageSize = if (pageSize == 8) 10 else 8
            binding.btnPageSize.text = "$pageSize个/页"
            showToast("每页显示$pageSize个")
        }
    }

    private fun updatePageInfo() {
        binding.tvPageInfo.text = "$currentPage/$totalPages"
    }

    private fun setupSearchAndFilter() {
        binding.btnFilter.setOnClickListener {
            showToast("筛选功能")
        }

        // 搜索功能可以在实际使用时添加
        binding.etSearch.setOnClickListener {
            showToast("搜索角色")
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}
