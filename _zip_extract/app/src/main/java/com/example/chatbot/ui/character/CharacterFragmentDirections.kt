package com.example.chatbot.ui.character

object CharacterFragmentDirections {
    fun actionCharacterFragmentToChatFragment(characterId: Long): android.os.Bundle {
        val args = android.os.Bundle()
        args.putLong("characterId", characterId)
        return args
    }
}
