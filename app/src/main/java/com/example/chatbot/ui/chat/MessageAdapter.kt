package com.example.chatbot.ui.chat

import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.chatbot.R
import com.example.chatbot.data.model.Message
import com.example.chatbot.databinding.ItemMessageLeftBinding
import com.example.chatbot.databinding.ItemMessageRightBinding
import com.example.chatbot.util.AvatarStorage
import com.example.chatbot.util.UserPromptPlaceholders
import io.noties.markwon.Markwon

private const val PAYLOAD_ASSISTANT_TEXT = "payload_assistant_text"

private fun attachMessageLongPress(
    message: Message,
    root: View,
    bubble: View,
    textView: android.widget.TextView,
    onMessageLongClick: (Message, View) -> Unit
) {
    val listener = View.OnLongClickListener { anchor ->
        onMessageLongClick(message, anchor)
        true
    }
    root.setOnLongClickListener(listener)
    bubble.setOnLongClickListener(listener)
    textView.setOnLongClickListener(listener)
    textView.isLongClickable = true
    textView.isClickable = true
    textView.setTextIsSelectable(false)
}

class MessageAdapter(
    private val markwon: Markwon,
    /** 当前正在流式输出中的助手消息 id；无则返回 ≤0 */
    private val streamingAssistantIdProvider: () -> Long,
    /** 长按消息时的回调 */
    private val onMessageLongClick: (Message, View) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<Message>()

    private var showAvatars: Boolean = true
    private var characterAvatarPath: String? = null
    private var userAvatarPath: String? = null
    private var userDisplayName: String = "用户"
    private var userPersona: String = ""
    private var characterDisplayName: String = ""

    fun updateChatDisplay(show: Boolean, characterPath: String?, userPath: String?) {
        showAvatars = show
        characterAvatarPath = characterPath
        userAvatarPath = userPath
        notifyDataSetChanged()
    }

    fun updateUserPlaceholders(displayName: String, persona: String, characterName: String) {
        userDisplayName = displayName.ifBlank { "用户" }
        userPersona = persona
        characterDisplayName = characterName
        notifyDataSetChanged()
    }

    fun submitList(newList: List<Message>) {
        val old = ArrayList(items)
        items.clear()
        items.addAll(newList)
        val result = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = old.size
            override fun getNewListSize(): Int = items.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return old[oldItemPosition].id == items[newItemPosition].id
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return old[oldItemPosition] == items[newItemPosition]
            }

            override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any? {
                val o = old[oldItemPosition]
                val n = items[newItemPosition]
                if (o.id != n.id) return null
                if (o == n) return null
                if (!n.isUser &&
                    o.timestamp == n.timestamp &&
                    o.characterId == n.characterId &&
                    o.content != n.content
                ) {
                    return PAYLOAD_ASSISTANT_TEXT
                }
                return null
            }
        })
        result.dispatchUpdatesTo(this)
    }

    /** 流式结束后再跑一次 Markdown 渲染（避免流式阶段反复 setMarkdown 闪烁） */
    fun refreshAssistantMarkdown(messageId: Long) {
        val idx = items.indexOfFirst { it.id == messageId }
        if (idx >= 0) {
            notifyItemChanged(idx)
        }
    }

    override fun getItemCount(): Int = items.size

    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_ASSISTANT = 2
    }

    override fun getItemViewType(position: Int): Int {
        return if (items[position].isUser) VIEW_TYPE_USER else VIEW_TYPE_ASSISTANT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_USER) {
            val binding = ItemMessageRightBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            UserMessageViewHolder(binding, markwon, onMessageLongClick)
        } else {
            val binding = ItemMessageLeftBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            AssistantMessageViewHolder(binding, markwon, onMessageLongClick)
        }
    }

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return items[position].id
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = items[position]
        val streamingId = streamingAssistantIdProvider()
        when (holder) {
            is UserMessageViewHolder -> holder.bind(
                message, showAvatars, userAvatarPath,
                userDisplayName, userPersona, characterDisplayName
            )
            is AssistantMessageViewHolder -> holder.bind(
                message, showAvatars, characterAvatarPath,
                userDisplayName, userPersona, characterDisplayName,
                streamingAssistantId = streamingId
            )
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isNotEmpty() && holder is AssistantMessageViewHolder) {
            if (payloads.any { it == PAYLOAD_ASSISTANT_TEXT }) {
                holder.bindAssistantTextOnly(
                    items[position],
                    userDisplayName,
                    userPersona,
                    characterDisplayName,
                    streamingAssistantIdProvider()
                )
                return
            }
        }
        onBindViewHolder(holder, position)
    }

    class UserMessageViewHolder(
        private val binding: ItemMessageRightBinding,
        private val markwon: Markwon,
        private val onMessageLongClick: (Message, View) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            message: Message,
            showAvatars: Boolean,
            userAvatarPath: String?,
            userDisplayName: String,
            userPersona: String,
            characterDisplayName: String
        ) {
            binding.tvMessage.movementMethod = LinkMovementMethod.getInstance()
            val text = UserPromptPlaceholders.apply(
                message.content, userDisplayName, userPersona, characterDisplayName
            )
            markwon.setMarkdown(binding.tvMessage, text)
            attachMessageLongPress(
                message,
                binding.root,
                binding.layoutBubble,
                binding.tvMessage,
                onMessageLongClick
            )
            val margin = binding.root.resources.getDimensionPixelSize(R.dimen.kd_space_300)
            val lp = binding.layoutBubble.layoutParams as ConstraintLayout.LayoutParams
            lp.marginStart = margin
            lp.marginEnd = if (showAvatars) margin else 0
            binding.layoutBubble.layoutParams = lp
            binding.ivAvatar.visibility = if (showAvatars) View.VISIBLE else View.GONE
            if (showAvatars) {
                AvatarStorage.loadInto(binding.ivAvatar, userAvatarPath)
            }
        }
    }

    class AssistantMessageViewHolder(
        private val binding: ItemMessageLeftBinding,
        private val markwon: Markwon,
        private val onMessageLongClick: (Message, View) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bindAssistantTextOnly(
            message: Message,
            userDisplayName: String,
            userPersona: String,
            characterDisplayName: String,
            streamingAssistantId: Long
        ) {
            val raw = message.content
            binding.progressTyping.visibility = View.GONE
            binding.tvMessage.visibility = View.VISIBLE
            val text = UserPromptPlaceholders.apply(
                raw, userDisplayName, userPersona, characterDisplayName
            )
            val streamPlain = message.id == streamingAssistantId && streamingAssistantId > 0L
            if (streamPlain) {
                binding.tvMessage.text = text
            } else {
                binding.tvMessage.movementMethod = LinkMovementMethod.getInstance()
                markwon.setMarkdown(binding.tvMessage, text)
            }
            attachMessageLongPress(
                message,
                binding.root,
                binding.layoutBubble,
                binding.tvMessage,
                onMessageLongClick
            )
        }

        fun bind(
            message: Message,
            showAvatars: Boolean,
            characterAvatarPath: String?,
            userDisplayName: String,
            userPersona: String,
            characterDisplayName: String,
            streamingAssistantId: Long
        ) {
            val raw = message.content
            val isTyping = raw.isBlank()
            binding.progressTyping.visibility = if (isTyping) View.VISIBLE else View.GONE
            binding.tvMessage.visibility = if (isTyping) View.GONE else View.VISIBLE
            if (!isTyping) {
                binding.tvMessage.movementMethod = LinkMovementMethod.getInstance()
                val text = UserPromptPlaceholders.apply(
                    raw, userDisplayName, userPersona, characterDisplayName
                )
                val streamPlain =
                    message.id == streamingAssistantId && streamingAssistantId > 0L
                if (streamPlain) {
                    binding.tvMessage.text = text
                } else {
                    markwon.setMarkdown(binding.tvMessage, text)
                }
                attachMessageLongPress(
                    message,
                    binding.root,
                    binding.layoutBubble,
                    binding.tvMessage,
                    onMessageLongClick
                )
            } else {
                binding.tvMessage.text = ""
                binding.root.setOnLongClickListener(null)
                binding.layoutBubble.setOnLongClickListener(null)
                binding.tvMessage.setOnLongClickListener(null)
            }
            binding.ivAvatar.visibility = if (showAvatars) View.VISIBLE else View.GONE
            if (showAvatars) {
                AvatarStorage.loadInto(binding.ivAvatar, characterAvatarPath)
            }
        }
    }
}
