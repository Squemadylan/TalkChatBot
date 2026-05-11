package com.example.chatbot.ui.character

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
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

    private var _binding: FragmentCharacterBinding? = null
    private val binding get() = _binding ?: throw IllegalStateException("Binding is not initialized or already destroyed")

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
        try {
            _binding = FragmentCharacterBinding.inflate(inflater, container, false)
        } catch (e: Exception) {
            showError("Failed to inflate layout: ${e.message}")
            return View(requireContext())
        }

        if (!isAppInitialized()) {
            showError("App initialization failed")
            return binding.root
        }

        initializeViewModel()
        setupRecyclerView()
        setupAddButton()
        setupTabButtons()
        setupPagination()
        setupSearchAndFilter()

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
            val characterRepository = CharacterRepository(app.database.characterDao())

            viewModel = ViewModelProvider(this) {
                CharacterViewModel(characterRepository)
            }.get(CharacterViewModel::class.java)

            observeCharacters()
        } catch (e: Exception) {
            showError("Failed to initialize ViewModel: ${e.message}")
        }
    }

    private fun observeCharacters() {
        if (!::viewModel.isInitialized) return

        viewModel.allCharacters.observe(viewLifecycleOwner) { characters ->
            safeAction {
                adapter.submitList(characters)
                totalPages = if (characters.isEmpty()) 1 else (characters.size + pageSize - 1) / pageSize
                binding.tvPageInfo.text = "$currentPage/$totalPages"

                if (characters.isEmpty()) {
                    showToast("暂无角色，点击右下角添加")
                }
            }
        }
    }

    private fun setupRecyclerView() {
        try {
            adapter = CharacterAdapter { character ->
                safeNavigate {
                    val action = CharacterFragmentDirections.actionCharacterFragmentToChatFragment(character.id)
                    findNavController().navigate(action)
                }
            }

            binding.recyclerView.layoutManager = GridLayoutManager(context, 2)
            binding.recyclerView.adapter = adapter
        } catch (e: Exception) {
            showError("Failed to setup RecyclerView: ${e.message}")
        }
    }

    private fun setupAddButton() {
        binding.fabAdd.setOnClickListener {
            try {
                val dialog = AddCharacterDialog { character ->
                    viewModel.insertCharacter(character)
                }
                dialog.show(childFragmentManager, "AddCharacterDialog")
            } catch (e: Exception) {
                showError("Failed to show dialog: ${e.message}")
            }
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
        safeAction {
            try {
                if (isMyCharactersTab) {
                    binding.btnMyCharacters.setBackgroundResource(R.drawable.bg_tab_selected)
                    binding.btnMyCharacters.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
                    binding.btnMarket.setBackgroundResource(R.drawable.bg_tab_unselected)
                    binding.btnMarket.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                    binding.fabAdd.visibility = View.VISIBLE
                } else {
                    binding.btnMarket.setBackgroundResource(R.drawable.bg_tab_selected)
                    binding.btnMarket.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
                    binding.btnMyCharacters.setBackgroundResource(R.drawable.bg_tab_unselected)
                    binding.btnMyCharacters.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                    binding.fabAdd.visibility = View.GONE
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to update tab UI", e)
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
            pageSize = if (pageSize == 8) 10 else 8
            binding.btnPageSize.text = "$pageSize个/页"
            showToast("每页显示$pageSize个")
        }
    }

    private fun updatePageInfo() {
        safeAction {
            binding.tvPageInfo.text = "$currentPage/$totalPages"
        }
    }

    private fun setupSearchAndFilter() {
        binding.btnFilter.setOnClickListener {
            showToast("筛选功能")
        }

        binding.etSearch.setOnClickListener {
            showToast("搜索角色")
        }
    }

    private fun safeNavigate(action: () -> Unit) {
        try {
            if (isAdded && _binding != null) {
                action()
            }
        } catch (e: Exception) {
            showError("Navigation failed: ${e.message}")
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
        private const val TAG = "CharacterFragment"
    }
}
