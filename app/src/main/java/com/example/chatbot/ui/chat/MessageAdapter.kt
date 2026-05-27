package com.example.chatbot.ui.chat

import android.graphics.drawable.GradientDrawable
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.chatbot.App
import com.example.chatbot.R
import com.example.chatbot.data.model.Message
import com.example.chatbot.databinding.ItemMessageLeftBinding
import com.example.chatbot.databinding.ItemMessageRightBinding
import com.example.chatbot.util.AvatarStorage
import com.example.chatbot.util.UserPromptPlaceholders
import io.noties.markwon.Markwon

private const val PAYLOAD_ASSISTANT_TEXT = "payload_assistant_text"

private fun applyBubbleAppearance(
    bubble: View,
    textView: TextView,
    isUser: Boolean,
    style: Int
) {
    val res = bubble.resources
    val compact = style == App.CHAT_BUBBLE_STYLE_COMPACT
    val padding = res.getDimensionPixelSize(
        if (compact) R.dimen.kd_space_200 else R.dimen.kd_space_300
    )
    val radius = res.getDimension(
        when (style) {
            App.CHAT_BUBBLE_STYLE_ROUNDED -> R.dimen.kd_space_500
            App.CHAT_BUBBLE_STYLE_TRANSLUCENT -> R.dimen.kd_radius_300
            else -> R.dimen.kd_radius_200
        }
    )
    val baseColor = ContextCompat.getColor(
        bubble.context,
        if (isUser) R.color.user_message else R.color.assistant_message
    )
    val color = if (style == App.CHAT_BUBBLE_STYLE_TRANSLUCENT) {
        (baseColor and 0x00FFFFFF) or (0xCC shl 24)
    } else {
        baseColor
    }
    bubble.background = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = radius
    }
    bubble.setPadding(padding, padding, padding, padding)
    bubble.minimumWidth = if (compact) 0 else res.getDimensionPixelSize(R.dimen.kd_space_1200)
    textView.setTextColor(
        ContextCompat.getColor(
            textView.context,
            if (isUser) R.color.bubble_user_text else R.color.text_primary
        )
    )
    textView.setTextSize(
        TypedValue.COMPLEX_UNIT_PX,
        res.getDimension(if (compact) R.dimen.kd_font_sub_base else R.dimen.kd_font_base)
    )
}

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
    private var bubbleStyle: Int = App.CHAT_BUBBLE_STYLE_DEFAULT

    fun updateChatDisplay(show: Boolean, characterPath: String?, userPath: String?, style: Int) {
        showAvatars = show
        characterAvatarPath = characterPath
        userAvatarPath = userPath
        bubbleStyle = style
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
                userDisplayName, userPersona, characterDisplayName,
                bubbleStyle
            )
            is AssistantMessageViewHolder -> holder.bind(
                message, showAvatars, characterAvatarPath,
                userDisplayName, userPersona, characterDisplayName,
                streamingAssistantId = streamingId,
                bubbleStyle = bubbleStyle
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
                    streamingAssistantIdProvider(),
                    bubbleStyle
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
            characterDisplayName: String,
            bubbleStyle: Int
        ) {
            binding.tvMessage.movementMethod = LinkMovementMethod.getInstance()
            val text = UserPromptPlaceholders.apply(
                message.content, userDisplayName, userPersona, characterDisplayName
            )
            markwon.setMarkdown(binding.tvMessage, text)
            applyBubbleAppearance(binding.layoutBubble, binding.tvMessage, isUser = true, bubbleStyle)
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
            streamingAssistantId: Long,
            bubbleStyle: Int
        ) {
            binding.progressTyping.visibility = View.GONE
            binding.tvMessage.visibility = View.VISIBLE
            applyBubbleAppearance(binding.layoutBubble, binding.tvMessage, isUser = false, bubbleStyle)
            val text = UserPromptPlaceholders.apply(
                displayAssistantText(message),
                userDisplayName,
                userPersona,
                characterDisplayName
            )
            val streamPlain =
                message.isStreaming() && message.id == streamingAssistantId && streamingAssistantId > 0L
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
            streamingAssistantId: Long,
            bubbleStyle: Int
        ) {
            val raw = message.content
            val isStreaming =
                message.isStreaming() && message.id == streamingAssistantId && streamingAssistantId > 0L
            val isTyping = raw.isBlank() && isStreaming
            applyBubbleAppearance(binding.layoutBubble, binding.tvMessage, isUser = false, bubbleStyle)
            binding.progressTyping.visibility = if (isTyping) View.VISIBLE else View.GONE
            binding.tvMessage.visibility = if (isTyping) View.GONE else View.VISIBLE
            if (!isTyping) {
                binding.tvMessage.movementMethod = LinkMovementMethod.getInstance()
                val text = UserPromptPlaceholders.apply(
                    displayAssistantText(message),
                    userDisplayName,
                    userPersona,
                    characterDisplayName
                )
                if (isStreaming) {
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

        private fun displayAssistantText(message: Message): String {
            if (!message.isFailed()) {
                return message.content.ifBlank { "回复中断，请重新发送。" }
            }
            val reason = message.error.ifBlank { "网络异常，请重试。" }
            return if (message.content.isBlank()) {
                "回复失败：$reason"
            } else {
                "${message.content}\n\n（回复已中断：$reason）"
            }
        }
    }
}
