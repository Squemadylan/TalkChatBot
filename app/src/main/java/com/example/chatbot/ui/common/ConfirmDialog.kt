package com.example.chatbot.ui.common

import android.app.Dialog
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.example.chatbot.R
import com.example.chatbot.databinding.DialogConfirmBinding

class ConfirmDialog(
    private val title: String,
    private val message: String,
    private val positiveText: String = "确定",
    private val negativeText: String = "取消",
    private val onConfirm: (() -> Unit)? = null,
    private val onNegative: (() -> Unit)? = null
) : DialogFragment() {

    private var _binding: DialogConfirmBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.Theme_Chatbot_Dialog)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            window?.setBackgroundDrawableResource(android.R.color.transparent)
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            val width = (resources.displayMetrics.widthPixels * 0.85f).toInt()
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            window.setGravity(Gravity.CENTER)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogConfirmBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.tvTitle.text = title
        binding.tvMessage.text = message
        binding.btnPositive.text = positiveText
        binding.btnNegative.text = negativeText

        binding.btnPositive.setOnClickListener {
            onConfirm?.invoke()
            dismiss()
        }

        binding.btnNegative.setOnClickListener {
            onNegative?.invoke()
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
