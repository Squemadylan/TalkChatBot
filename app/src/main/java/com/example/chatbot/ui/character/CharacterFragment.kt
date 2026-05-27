package com.example.chatbot.ui.character

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.example.chatbot.App
import com.example.chatbot.R
import com.example.chatbot.data.model.Character
import com.example.chatbot.data.repository.CharacterRepository
import com.example.chatbot.data.repository.MessageRepository
import com.example.chatbot.databinding.FragmentCharacterBinding
import com.example.chatbot.ui.common.ConfirmDialog
import com.example.chatbot.ui.common.MultiChoiceDialog
import com.example.chatbot.util.AvatarStorage
import com.example.chatbot.viewmodel.CharacterViewModel
import com.example.chatbot.viewmodel.CharacterViewModelFactory
import kotlinx.coroutines.launch
import java.util.Locale

class CharacterFragment : Fragment() {

    private var _binding: FragmentCharacterBinding? = null
    private val binding get() = _binding ?: throw IllegalStateException("Binding is not initialized or already destroyed")

    private lateinit var viewModel: CharacterViewModel
    private lateinit var adapter: CharacterAdapter

    private var pendingAvatarCharacterId: Long? = null

    private var allCharactersCache: List<Character> = emptyList()
    private var currentTagFilter: Set<String> = emptySet()

    private val pickCharacterAvatar = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        val id = pendingAvatarCharacterId ?: return@registerForActivityResult
        pendingAvatarCharacterId = null
        if (uri == null || !::viewModel.isInitialized) return@registerForActivityResult
        viewLifecycleOwner.lifecycleScope.launch {
            val character = viewModel.getCharacterById(id) ?: return@launch
            val path = AvatarStorage.saveFromUri(
                requireContext().applicationContext,
                uri,
                "character_${id}.jpg"
            )
            if (path != null) {
                viewModel.updateCharacter(character.copy(avatar = path))
                showToast("角色头像已更新")
            } else {
                showToast("保存头像失败")
            }
        }
    }

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
            val messageRepository = MessageRepository(app.database.messageDao())

            viewModel = ViewModelProvider(
                this,
                CharacterViewModelFactory(characterRepository, messageRepository)
            )[CharacterViewModel::class.java]

            observeCharacters()
        } catch (e: Exception) {
            showError("Failed to initialize ViewModel: ${e.message}")
        }
    }

    private fun observeCharacters() {
        if (!::viewModel.isInitialized) return

        viewModel.allCharacters.observe(viewLifecycleOwner) { characters ->
            safeAction {
                allCharactersCache = characters ?: emptyList()
                updateCharacterList()
                if (allCharactersCache.isEmpty()) {
                    showToast("暂无角色，点击右上方 + 添加")
                }
            }
        }
    }

    private fun setupRecyclerView() {
        try {
            adapter = CharacterAdapter(
                onCardClick = { character ->
                    safeNavigate {
                        findNavController().navigate(
                            R.id.chatFragment,
                            Bundle().apply { putLong("characterId", character.id) },
                            androidx.navigation.NavOptions.Builder()
                                .setLaunchSingleTop(true)
                                .build()
                        )
                    }
                },
                onAvatarEdit = { character ->
                    pendingAvatarCharacterId = character.id
                    pickCharacterAvatar.launch("image/*")
                },
                onAvatarLongClick = { character, anchor ->
                    showCharacterMenu(character, anchor)
                },
                onCardLongClick = { character, anchor ->
                    showCharacterMenu(character, anchor)
                }
            )

            binding.recyclerView.layoutManager = GridLayoutManager(requireContext(), 2)
            binding.recyclerView.adapter = adapter
        } catch (e: Exception) {
            showError("Failed to setup RecyclerView: ${e.message}")
        }
    }

    private fun setupAddButton() {
        binding.btnAddCharacter.setOnClickListener {
            try {
                val dialog = AddCharacterDialog(existingCharacter = null) { character ->
                    viewModel.insertCharacter(character)
                }
                dialog.show(childFragmentManager, "AddCharacterDialog")
            } catch (e: Exception) {
                showError("Failed to show dialog: ${e.message}")
            }
        }
    }

    private fun showCharacterMenu(character: Character, anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add(0, MENU_EDIT_CHARACTER, 0, "编辑角色")
        popup.menu.add(0, MENU_DELETE_CHARACTER, 1, "删除角色")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_EDIT_CHARACTER -> {
                    showEditCharacterDialog(character)
                    true
                }
                MENU_DELETE_CHARACTER -> {
                    ConfirmDialog(
                        title = "删除角色",
                        message = "将永久删除角色「${character.name}」及其全部聊天记录，不可恢复。确定？",
                        positiveText = "删除",
                        onConfirm = {
                            viewModel.deleteCharacterWithMessages(character.id, requireContext())
                            showToast("已删除角色")
                        }
                    ).show(childFragmentManager, "DeleteCharacter_${character.id}")
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showEditCharacterDialog(character: Character) {
        try {
            AddCharacterDialog(existingCharacter = character) { updated ->
                viewModel.updateCharacter(updated)
            }.show(childFragmentManager, "EditCharacterDialog_${character.id}")
        } catch (e: Exception) {
            showError("打开编辑失败: ${e.message}")
        }
    }

    private fun setupSearchAndFilter() {
        binding.btnFilter.setOnClickListener {
            showFilterDialog()
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateCharacterList()
            }
        })
    }

    private fun showFilterDialog() {
        val allTags = allCharactersCache
            .flatMap { it.tags.split(",", "，") }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

        if (allTags.isEmpty()) {
            showToast("暂无标签可筛选")
            return
        }

        val checkedItems = BooleanArray(allTags.size) { index ->
            allTags[index] in currentTagFilter
        }

        MultiChoiceDialog(
            title = "按标签筛选",
            items = allTags,
            checkedItems = checkedItems
        ) { selectedIndices ->
            val selectedTags = selectedIndices.map { allTags[it] }.toSet()
            applyTagFilter(selectedTags)
        }.show(childFragmentManager, "FilterDialog")
    }

    private fun applyTagFilter(tags: Set<String>) {
        currentTagFilter = tags
        if (tags.isEmpty()) {
            showToast("已清除筛选")
        } else {
            showToast("已筛选：${tags.size} 个标签")
        }
        updateCharacterList()
    }

    private fun updateCharacterList() {
        val query = binding.etSearch.text.toString().trim().lowercase(Locale.getDefault())

        var filtered = if (currentTagFilter.isEmpty()) {
            allCharactersCache
        } else {
            allCharactersCache.filter { character ->
                val charTags = character.tags
                    .split(",", "，")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .toSet()
                currentTagFilter.any { it in charTags }
            }
        }

        if (query.isNotEmpty()) {
            filtered = filtered.filter { character ->
                character.name.lowercase(Locale.getDefault()).contains(query) ||
                        character.tags.lowercase(Locale.getDefault()).contains(query) ||
                        character.description.lowercase(Locale.getDefault()).contains(query)
            }
        }

        adapter.submitList(filtered)
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
        private const val MENU_EDIT_CHARACTER = 1
        private const val MENU_DELETE_CHARACTER = 2
    }
}
