package com.example.chatbot.ui.chat

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.chatbot.data.model.Message
import com.example.chatbot.databinding.ItemMessageLeftBinding
import com.example.chatbot.databinding.ItemMessageRightBinding

class MessageAdapter : ListAdapter<Message, RecyclerView.ViewHolder>(MessageDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_ASSISTANT = 2
    }

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).isUser) VIEW_TYPE_USER else VIEW_TYPE_ASSISTANT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_USER) {
            val binding = ItemMessageRightBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            UserMessageViewHolder(binding)
        } else {
            val binding = ItemMessageLeftBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            AssistantMessageViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        if (holder is UserMessageViewHolder) {
            holder.bind(message)
        } else if (holder is AssistantMessageViewHolder) {
            holder.bind(message)
        }
    }

    class UserMessageViewHolder(private val binding: ItemMessageRightBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(message: Message) {
            binding.tvMessage.text = message.content
        }
    }

    class AssistantMessageViewHolder(private val binding: ItemMessageLeftBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(message: Message) {
            binding.tvMessage.text = message.content
        }
    }

    class MessageDiffCallback : DiffUtil.ItemCallback<Message>() {
        override fun areItemsTheSame(oldItem: Message, newItem: Message): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Message, newItem: Message): Boolean {
            return oldItem == newItem
        }
    }
}
