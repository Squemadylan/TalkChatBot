package com.example.chatbot.ui.common

import android.app.Dialog
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.example.chatbot.R
import com.example.chatbot.databinding.DialogConfirmBinding
import com.example.chatbot.util.AppUpdateManager

class UpdateDialogFragment : DialogFragment() {

    private var _binding: DialogConfirmBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.Theme_Chatbot_Dialog)
        isCancelable = !requireArguments().getBoolean(ARG_FORCE_UPDATE, false)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            if (!isCancelable) {
                setCanceledOnTouchOutside(false)
            }
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
        val args = requireArguments()
        binding.tvTitle.text = args.getString(ARG_TITLE).orEmpty()
        binding.tvMessage.text = args.getString(ARG_MESSAGE).orEmpty()
        binding.btnPositive.text = args.getString(ARG_POSITIVE_TEXT).orEmpty()

        val force = args.getBoolean(ARG_FORCE_UPDATE, false)
        binding.btnNegative.visibility = View.VISIBLE
        binding.btnNegative.text = args.getString(ARG_NEGATIVE_TEXT).orEmpty()
        if (force) {
            binding.btnNegative.setOnClickListener {
                AppUpdateManager.openManualUpdatePage(
                    requireContext(),
                    urlOverride = manualUrlFromArgs()
                )
                // 强制更新不关闭弹窗，安装新版本后重启应用
            }
        } else {
            binding.btnNegative.setOnClickListener {
                emitResult(ACTION_LATER)
                dismiss()
            }
        }

        binding.btnPositive.setOnClickListener {
            emitResult(ACTION_UPDATE)
            dismiss()
        }
    }

    private fun manualUrlFromArgs(): String =
        requireArguments().getString(ARG_MANUAL_UPDATE_URL).orEmpty().ifBlank {
            getString(R.string.update_manual_url)
        }

    private fun emitResult(action: String) {
        parentFragmentManager.setFragmentResult(
            REQUEST_KEY,
            bundleOf(
                RESULT_ACTION to action,
                ARG_MANIFEST_JSON to requireArguments().getString(ARG_MANIFEST_JSON).orEmpty()
            )
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "app_update_dialog"
        const val REQUEST_KEY = "app_update_dialog_result"
        const val RESULT_ACTION = "action"
        const val ACTION_UPDATE = "update"
        const val ACTION_LATER = "later"

        private const val ARG_TITLE = "title"
        private const val ARG_MESSAGE = "message"
        private const val ARG_FORCE_UPDATE = "force_update"
        private const val ARG_POSITIVE_TEXT = "positive_text"
        private const val ARG_NEGATIVE_TEXT = "negative_text"
        const val ARG_MANIFEST_JSON = "manifest_json"
        private const val ARG_MANUAL_UPDATE_URL = "manual_update_url"

        fun newInstance(
            title: String,
            message: String,
            forceUpdate: Boolean,
            positiveText: String,
            negativeText: String,
            manifestJson: String,
            manualUpdateUrl: String
        ): UpdateDialogFragment = UpdateDialogFragment().apply {
            arguments = bundleOf(
                ARG_TITLE to title,
                ARG_MESSAGE to message,
                ARG_FORCE_UPDATE to forceUpdate,
                ARG_POSITIVE_TEXT to positiveText,
                ARG_NEGATIVE_TEXT to negativeText,
                ARG_MANIFEST_JSON to manifestJson,
                ARG_MANUAL_UPDATE_URL to manualUpdateUrl
            )
        }
    }
}
