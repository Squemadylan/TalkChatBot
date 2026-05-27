package com.example.chatbot.ui.character

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.example.chatbot.data.model.Character
import com.example.chatbot.databinding.DialogAddCharacterBinding

class AddCharacterDialog(private val onAdd: (Character) -> Unit) : DialogFragment() {

    private lateinit var binding: DialogAddCharacterBinding

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            window?.setBackgroundDrawableResource(android.R.color.transparent)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogAddCharacterBinding.inflate(inflater, container, false)

        binding.btnSave.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            val description = binding.etDescription.text.toString().trim()
            val prompt = binding.etPrompt.text.toString().trim()
            val tags = binding.etTags.text.toString().trim()

            if (name.isNotEmpty()) {
                val character = Character(
                    name = name,
                    avatar = "",
                    description = description,
                    prompt = prompt,
                    tags = tags
                )
                onAdd(character)
                dismiss()
            }
        }

        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        return binding.root
    }
}
