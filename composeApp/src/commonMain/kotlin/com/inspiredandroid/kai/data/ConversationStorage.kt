package com.inspiredandroid.kai.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

expect fun readLegacyConversationFile(): ByteArray?
expect fun deleteLegacyConversationFile()

class ConversationStorage(private val appSettings: AppSettings) {
    private val mutableConversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = mutableConversations.asStateFlow()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun loadConversations() {
        val data = appSettings.getConversationsJson()
        if (data != null) {
            mutableConversations.value = deserialize(data)
        } else {
            migrateLegacy()
        }
    }

    fun saveConversation(conversation: Conversation) {
        mutableConversations.update { current ->
            val index = current.indexOfFirst { it.id == conversation.id }
            if (index >= 0) {
                current.toMutableList().apply { set(index, conversation) }
            } else {
                current + conversation
            }
        }
        persist()
    }

    fun deleteConversation(id: String) {
        mutableConversations.update { current ->
            current.filter { it.id != id }
        }
        persist()
    }

    private fun persist() {
        val data = json.encodeToString(ConversationsData(conversations = mutableConversations.value))
        appSettings.setConversationsJson(data)
    }

    private fun deserialize(data: String): List<Conversation> = try {
        json.decodeFromString<ConversationsData>(data).conversations
    } catch (_: Exception) {
        emptyList()
    }

    private fun migrateLegacy() {
        val legacyData = readLegacyConversationFile() ?: return
        val key = appSettings.getEncryptionKey() ?: return
        val decrypted = ByteArray(legacyData.size)
        for (i in legacyData.indices) {
            decrypted[i] = (legacyData[i].toInt() xor key[i % key.size].toInt()).toByte()
        }
        val conversations = deserialize(decrypted.decodeToString())
        mutableConversations.value = conversations
        persist()
        deleteLegacyConversationFile()
    }
}
