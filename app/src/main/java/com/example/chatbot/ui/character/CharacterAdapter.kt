package com.example.chatbot.ui.character

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.chatbot.data.model.Character
import com.example.chatbot.databinding.ItemCharacterBinding
import com.example.chatbot.util.AvatarStorage

class CharacterAdapter(
    private val onCardClick: (Character) -> Unit,
    private val onAvatarEdit: (Character) -> Unit,
    private val onAvatarLongClick: (Character) -> Unit
) : ListAdapter<Character, CharacterAdapter.CharacterViewHolder>(CharacterDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CharacterViewHolder {
        val binding = ItemCharacterBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CharacterViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CharacterViewHolder, position: Int) {
        val character = getItem(position)
        holder.bind(character, onCardClick, onAvatarEdit, onAvatarLongClick)
    }

    class CharacterViewHolder(private val binding: ItemCharacterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(
            character: Character,
            onCardClick: (Character) -> Unit,
            onAvatarEdit: (Character) -> Unit,
            onAvatarLongClick: (Character) -> Unit
        ) {
            binding.tvName.text = character.name
            AvatarStorage.loadInto(binding.ivAvatar, character.avatar.takeIf { it.isNotBlank() })

            val tags = character.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (tags.isNotEmpty()) {
                binding.tvTag1.text = tags[0]
                binding.tvTag1.visibility = View.VISIBLE

                if (tags.size > 1) {
                    binding.tvTag2.text = tags[1]
                    binding.tvTag2.visibility = View.VISIBLE
                } else {
                    binding.tvTag2.visibility = View.GONE
                }
            } else {
                binding.tvTag1.visibility = View.GONE
                binding.tvTag2.visibility = View.GONE
            }

            binding.root.setOnClickListener { onCardClick(character) }
            binding.btnAvatarEdit.setOnClickListener { onAvatarEdit(character) }
            binding.ivAvatar.setOnLongClickListener {
                onAvatarLongClick(character)
                true
            }
        }
    }

    class CharacterDiffCallback : DiffUtil.ItemCallback<Character>() {
        override fun areItemsTheSame(oldItem: Character, newItem: Character): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Character, newItem: Character): Boolean {
            return oldItem == newItem
        }
    }
}
