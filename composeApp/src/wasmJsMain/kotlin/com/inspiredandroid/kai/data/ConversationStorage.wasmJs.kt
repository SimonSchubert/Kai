package com.inspiredandroid.kai.data

import kotlinx.browser.localStorage
import kotlinx.coroutines.flow.StateFlow
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

actual class ConversationStorage actual constructor(
    appSettings: AppSettings,
) : BaseConversationStorage(appSettings) {

    actual override val conversations: StateFlow<List<Conversation>>
        get() = super.conversations

    @OptIn(ExperimentalEncodingApi::class)
    actual suspend fun loadConversations() {
        try {
            val encoded = localStorage.getItem(STORAGE_KEY)
            if (encoded != null) {
                val encryptedData = Base64.decode(encoded)
                val decryptedJson = decrypt(encryptedData)
                val loaded = deserializeConversations(decryptedJson)
                mutableConversations.value = loaded
            }
        } catch (e: Exception) {
            mutableConversations.value = emptyList()
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    actual suspend fun saveConversation(conversation: Conversation) {
        updateConversation(conversation)
        saveToStorage()
    }

    actual suspend fun deleteConversation(id: String) {
        removeConversation(id)
        saveToStorage()
    }

    actual suspend fun deleteAllConversations() {
        clearAllConversations()
        localStorage.removeItem(STORAGE_KEY)
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun saveToStorage() {
        val jsonData = serializeConversations(mutableConversations.value)
        val encryptedData = encrypt(jsonData)
        val encoded = Base64.encode(encryptedData)
        localStorage.setItem(STORAGE_KEY, encoded)
    }

    companion object {
        private const val STORAGE_KEY = "kaimutableConversations"
    }
}
