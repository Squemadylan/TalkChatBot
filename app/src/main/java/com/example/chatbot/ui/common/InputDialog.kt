package com.example.chatbot.ui.common

import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.chatbot.databinding.DialogInputBinding
import kotlin.math.ceil

/** 普通输入、创建角色（标签/开场白）等默认上限 */
const val DEFAULT_INPUT_MAX_LENGTH = 500

/** 仅「创建角色 → 角色描述」弹窗使用 */
const val CHARACTER_DESCRIPTION_MAX_LENGTH = 5000

/** 多行提示框默认可见高度（行数），超出在框内滚动 */
const val MULTILINE_DEFAULT_VISIBLE_LINES = 5

/** 「创建角色 → 角色描述」输入框最多显示的可见行数 */
const val CHARACTER_DESCRIPTION_VISIBLE_LINES = 15

/**
 * 文本输入弹窗。使用 [AlertDialog] 而非 [androidx.fragment.app.DialogFragment]，
 * 避免在 [androidx.fragment.app.DialogFragment]（如创建角色）内再嵌套子 Fragment 时崩溃。
 *
 * @param maxLength 最大字符数；创建角色描述请传 [CHARACTER_DESCRIPTION_MAX_LENGTH]
 * @param multilineVisibleLines 多行模式下输入框最大可见高度（按行高折算的行数），超出在框内滚动
 */
fun Fragment.showTextInputPrompt(
    title: String,
    initialValue: String = "",
    hint: String = "",
    allowEmpty: Boolean = false,
    isMultiline: Boolean = false,
    maxLength: Int = DEFAULT_INPUT_MAX_LENGTH,
    multilineVisibleLines: Int = MULTILINE_DEFAULT_VISIBLE_LINES,
    onConfirm: (String) -> Unit
) {
    if (!isAdded) return

    val activity = requireActivity()
    val binding = DialogInputBinding.inflate(LayoutInflater.from(activity), null, false)
    binding.tvTitle.text = title

    val cap = maxLength.coerceAtLeast(1)
    val clipped = initialValue.take(cap)

    binding.etInput.apply {
        filters = arrayOf(InputFilter.LengthFilter(cap))
        setText(clipped)
        if (hint.isNotEmpty()) setHint(hint)
        if (isMultiline) {
            minLines = 1
            maxLines = Int.MAX_VALUE
            setSingleLine(false)
            setHorizontallyScrolling(false)
            isVerticalScrollBarEnabled = true
            scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
            // 不用 ScrollingMovementMethod，否则会拦截长按选词/复制
            movementMethod = null
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setTextIsSelectable(true)
            isFocusableInTouchMode = true
        } else {
            minLines = 1
            maxLines = 1
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
        }
    }

    val dialog = AlertDialog.Builder(activity)
        .setView(binding.root)
        .create()

    dialog.setCanceledOnTouchOutside(true)
    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

    binding.btnPositive.setOnClickListener {
        val value = binding.etInput.text.toString().trim()
        if (value.isNotEmpty() || allowEmpty) {
            onConfirm(value)
            dialog.dismiss()
        }
    }
    binding.btnNegative.setOnClickListener { dialog.dismiss() }

    dialog.setOnShowListener {
        val w = dialog.window ?: return@setOnShowListener
        val width = (activity.resources.displayMetrics.widthPixels * 0.85f).toInt()
        w.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        w.setGravity(Gravity.CENTER)
        w.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
        if (isMultiline) {
            binding.etInput.post {
                applyMultilineMaxHeight(binding.etInput, multilineVisibleLines.coerceAtLeast(1))
            }
        }
        binding.etInput.requestFocus()
        val len = binding.etInput.text?.length ?: 0
        try {
            binding.etInput.setSelection(len)
        } catch (_: IndexOutOfBoundsException) {
            binding.etInput.setSelection(0)
        }
    }

    dialog.show()
}

private fun applyMultilineMaxHeight(editText: EditText, visibleLines: Int) {
    val lh = editText.lineHeight.takeIf { it > 0 }
        ?: ceil((editText.textSize * 1.25f).toDouble()).toInt().coerceAtLeast(1)
    editText.maxHeight = lh * visibleLines
}
