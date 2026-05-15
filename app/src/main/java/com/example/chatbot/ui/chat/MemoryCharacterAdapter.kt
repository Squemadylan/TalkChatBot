package com.example.chatbot.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.chatbot.databinding.ItemMemoryByCharacterBinding
import com.example.chatbot.util.AvatarStorage
import com.example.chatbot.util.UserPromptPlaceholders
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MemoryCharacterAdapter(
    private val onOpenChat: (Long) -> Unit,
    private val onMenu: (MemoryHubRow, View) -> Unit
) : ListAdapter<MemoryHubRow, MemoryCharacterAdapter.VH>(Diff()) {

    private val dateFmt = SimpleDateFormat("MM-dd", Locale.getDefault())
    private var userDisplayName: String = "用户"
    private var userPersona: String = ""

    fun updateUserPlaceholders(displayName: String, persona: String) {
        userDisplayName = displayName.ifBlank { "用户" }
        userPersona = persona
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemMemoryByCharacterBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(
            getItem(position),
            dateFmt,
            userDisplayName,
            userPersona,
            onOpenChat,
            onMenu
        )
    }

    class VH(private val binding: ItemMemoryByCharacterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(
            row: MemoryHubRow,
            dateFmt: SimpleDateFormat,
            userDisplayName: String,
            userPersona: String,
            onOpenChat: (Long) -> Unit,
            onMenu: (MemoryHubRow, View) -> Unit
        ) {
            val c = row.character
            binding.tvName.text = c.name
            AvatarStorage.loadInto(binding.ivAvatar, c.avatar.takeIf { it.isNotBlank() })

            val last = row.lastMessage
            if (last != null) {
                binding.tvDate.text = dateFmt.format(Date(last.timestamp))
                val snippet = UserPromptPlaceholders.apply(
                    last.content,
                    userDisplayName,
                    userPersona,
                    c.name
                )
                binding.tvSnippet.text = snippet.ifBlank { "（空消息）" }
            } else {
                binding.tvDate.text = ""
                binding.tvSnippet.text = "暂无回忆"
            }

            binding.root.setOnClickListener { onOpenChat(c.id) }
            binding.btnMenu.setOnClickListener { v -> onMenu(row, v) }
        }
    }

    class Diff : DiffUtil.ItemCallback<MemoryHubRow>() {
        override fun areItemsTheSame(a: MemoryHubRow, b: MemoryHubRow): Boolean =
            a.character.id == b.character.id

        override fun areContentsTheSame(a: MemoryHubRow, b: MemoryHubRow): Boolean =
            a == b
    }
}
