package com.example.chatbot.ui.character

import android.app.Dialog
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.example.chatbot.R
import com.example.chatbot.data.model.Character
import com.example.chatbot.databinding.DialogAddCharacterBinding
import com.example.chatbot.ui.common.CHARACTER_DESCRIPTION_MAX_LENGTH
import com.example.chatbot.ui.common.CHARACTER_DESCRIPTION_VISIBLE_LINES
import com.example.chatbot.ui.common.DEFAULT_INPUT_MAX_LENGTH
import com.example.chatbot.ui.common.MULTILINE_DEFAULT_VISIBLE_LINES
import com.example.chatbot.ui.common.showTextInputPrompt
import com.example.chatbot.util.AvatarStorage
import kotlinx.coroutines.launch

class AddCharacterDialog(
    private val existingCharacter: Character? = null,
    private val onSubmit: (Character) -> Unit
) : DialogFragment() {

    private lateinit var binding: DialogAddCharacterBinding
    private var draftAvatarPath: String? = null

    private var tags: String = ""
    private var description: String = ""
    private var openingGreeting: String = ""
    private var enableLongTermMemory: Boolean = false
    private var enableAutoRead: Boolean = false

    private val pickAvatar = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null || !::binding.isInitialized) return@registerForActivityResult
        val ctx = requireContext().applicationContext
        val idPart = existingCharacter?.id?.takeIf { it > 0 }?.toString() ?: "new"
        val name = "char_${idPart}_${System.currentTimeMillis()}.img"
        val path = AvatarStorage.saveFromUri(ctx, uri, name)
        if (path != null) {
            AvatarStorage.deleteFileIfExists(draftAvatarPath)
            draftAvatarPath = path
            AvatarStorage.loadInto(binding.ivDialogAvatar, path)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            window?.setBackgroundDrawableResource(android.R.color.transparent)
        }
    }

    override fun onStart() {
        super.onStart()
        val h = (resources.displayMetrics.heightPixels * 0.9f).toInt()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, h)
        dialog?.window?.setGravity(Gravity.BOTTOM)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogAddCharacterBinding.inflate(inflater, container, false)

        existingCharacter?.let { c ->
            binding.tvSheetTitle.text = "编辑角色"
            binding.btnSave.text = "保存"
            binding.etName.setText(c.name)
            tags = c.tags
            description = c.description
            openingGreeting = c.openingGreeting
            enableLongTermMemory = c.enableLongTermMemory
            enableAutoRead = c.enableAutoRead
            draftAvatarPath = c.avatar.takeIf { it.isNotBlank() }
        }
        
        binding.switchLongTermMemory.isChecked = enableLongTermMemory
        binding.switchAutoRead.isChecked = enableAutoRead
        refreshMemorySummary()
        binding.btnResetCharacterMemory.setOnClickListener {
            val targetId = existingCharacter?.id ?: 0L
            if (targetId <= 0L) {
                showToast("保存角色后才能重置该角色的记忆")
                return@setOnClickListener
            }
            com.example.chatbot.ui.common.ConfirmDialog(
                title = "重置角色记忆",
                message = "将删除该角色下的 4 层记忆文件与向量索引，不可恢复。继续？",
                positiveText = "重置",
                onConfirm = {
                    val ctx = requireContext().applicationContext
                    kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        com.example.chatbot.memory.MemoryPipeline.resetCharacter(ctx, targetId)
                    }
                    showToast("已请求重置记忆")
                }
            ).show(parentFragmentManager, "ResetCharMemory_$targetId")
        }

        binding.rowAlternate.root.visibility = View.GONE
        binding.rowExamples.root.visibility = View.GONE

        AvatarStorage.loadInto(binding.ivDialogAvatar, draftAvatarPath)
        setupSettingRows()
        refreshRowSummaries()

        binding.btnClose.setOnClickListener { dismiss() }

        binding.btnRandomAvatar.setOnClickListener {
            val ctx = requireContext().applicationContext
            AvatarStorage.deleteFileIfExists(draftAvatarPath)
            val path = AvatarStorage.saveTintedCircleAvatar(ctx)
            if (path != null) {
                draftAvatarPath = path
                AvatarStorage.loadInto(binding.ivDialogAvatar, path)
            } else {
                draftAvatarPath = null
                AvatarStorage.loadInto(binding.ivDialogAvatar, null)
                showToast("生成头像失败")
            }
        }

        binding.btnPickDialogAvatar.setOnClickListener {
            pickAvatar.launch("image/*")
        }
        binding.ivDialogAvatar.setOnClickListener {
            pickAvatar.launch("image/*")
        }

        binding.rowTags.root.setOnClickListener {
            showInputDialog("标签", tags, "多个标签用逗号分隔", false, DEFAULT_INPUT_MAX_LENGTH) { newTags ->
                tags = newTags; refreshRowSummaries()
            }
        }
        binding.rowDescription.root.setOnClickListener {
            showInputDialog(
                "角色描述",
                description,
                "填写角色背景与性格等",
                true,
                CHARACTER_DESCRIPTION_MAX_LENGTH,
                multilineVisibleLines = CHARACTER_DESCRIPTION_VISIBLE_LINES
            ) { newDesc ->
                description = newDesc; refreshRowSummaries()
            }
        }
        binding.rowOpening.root.setOnClickListener {
            showInputDialog("主开场白", openingGreeting, "角色对用户的第一句话或情境", true, DEFAULT_INPUT_MAX_LENGTH) {
                openingGreeting = it; refreshRowSummaries()
            }
        }

        binding.btnSave.setOnClickListener {
            enableLongTermMemory = binding.switchLongTermMemory.isChecked
            enableAutoRead = binding.switchAutoRead.isChecked
            val name = binding.etName.text.toString().trim().ifEmpty { "新角色" }
            val avatarFinal = draftAvatarPath?.takeIf { it.isNotBlank() }
                ?: existingCharacter?.avatar?.takeIf { it.isNotBlank() }.orEmpty()
            val base = Character(
                name = name,
                avatar = avatarFinal,
                description = description.trim(),
                prompt = existingCharacter?.prompt?.trim().orEmpty(),
                tags = tags.trim(),
                openingGreeting = openingGreeting.trim(),
                enableLongTermMemory = enableLongTermMemory,
                enableAutoRead = enableAutoRead
            )
            val result = existingCharacter?.let {
                base.copy(id = it.id, createdAt = it.createdAt)
            } ?: base
            onSubmit(result)
            dismiss()
        }

        return binding.root
    }

    private fun setupSettingRows() {
        tintRowIcon(binding.rowTags.rowIcon, R.drawable.ic_label, R.color.kd_row_icon_tag)
        binding.rowTags.rowLabel.text = "标签"

        tintRowIcon(binding.rowDescription.rowIcon, R.drawable.ic_document, R.color.kd_row_icon_document)
        binding.rowDescription.rowLabel.text = "角色描述"

        tintRowIcon(binding.rowOpening.rowIcon, R.drawable.ic_chat, R.color.kd_row_icon_chat)
        binding.rowOpening.rowLabel.text = "主开场白"
    }

    private fun tintRowIcon(view: ImageView, drawableRes: Int, colorRes: Int) {
        view.setImageResource(drawableRes)
        val c = ContextCompat.getColor(requireContext(), colorRes)
        view.setColorFilter(c, PorterDuff.Mode.SRC_IN)
    }

    private fun refreshRowSummaries() {
        binding.rowTags.rowValue.text = if (tags.isBlank()) "无标签" else previewLine(tags)
        binding.rowDescription.rowValue.text =
            if (description.isBlank()) "无描述" else previewLine(description)
        binding.rowOpening.rowValue.text =
            if (openingGreeting.isBlank()) "无开场白" else previewLine(openingGreeting)
    }

    private fun refreshMemorySummary() {
        val targetId = existingCharacter?.id ?: 0L
        if (targetId <= 0L) {
            binding.tvMemoryLayersSummary.text =
                "开启后自动抽取 L1 原子、聚类 L2 场景、归纳 L3 画像"
            return
        }
        viewLifecycleScopeOrNull()?.launch(kotlinx.coroutines.Dispatchers.IO) {
            val ctx = context?.applicationContext ?: return@launch
            val persona = com.example.chatbot.memory.layers.L3PersonaUpdater.loadForCharacter(ctx, targetId)
            val scens = com.example.chatbot.memory.layers.L2ScenarioClusterer.loadAllScenarios(ctx, targetId)
            val atoms = runCatching {
                com.example.chatbot.memory.AtomJsonl.readAll(
                    com.example.chatbot.memory.MemoryPaths.atomsJsonl(ctx, targetId)
                )
            }.getOrDefault(emptyList())
            val canvas = com.example.chatbot.memory.layers.ShortTermCanvas.loadCanvasText(ctx, targetId)
            val canvasLines = if (canvas.isBlank()) 0 else canvas.lines().count { it.contains("[") }
            val text = buildString {
                append("L1 原子 ").append(atoms.size)
                append("  ·  L2 场景 ").append(scens.size)
                append("  ·  L3 画像 ")
                append(if (persona.content.isBlank()) "未生成" else "已生成")
                append("  ·  画布节点 ").append(canvasLines)
            }
            viewLifecycleScopeOrNull()?.launch(kotlinx.coroutines.Dispatchers.Main) {
                if (::binding.isInitialized) binding.tvMemoryLayersSummary.text = text
            }
        }
    }

    private fun viewLifecycleScopeOrNull() = try { viewLifecycleOwner.lifecycleScope } catch (e: Exception) { null }

    private fun previewLine(text: String): String {
        val line = text.trim().lineSequence().firstOrNull()?.trim().orEmpty()
        return if (line.length > 20) line.take(20) + "…" else line
    }

    private fun showInputDialog(
        title: String,
        initial: String,
        hint: String,
        isMultiline: Boolean,
        maxLength: Int = DEFAULT_INPUT_MAX_LENGTH,
        multilineVisibleLines: Int = MULTILINE_DEFAULT_VISIBLE_LINES,
        onSave: (String) -> Unit
    ) {
        showTextInputPrompt(
            title = title,
            initialValue = initial,
            hint = hint,
            allowEmpty = true,
            isMultiline = isMultiline,
            maxLength = maxLength,
            multilineVisibleLines = multilineVisibleLines,
            onConfirm = onSave
        )
    }

    private fun showToast(message: String) {
        if (isAdded) Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}
