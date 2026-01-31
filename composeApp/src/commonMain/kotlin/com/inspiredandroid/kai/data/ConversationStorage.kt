package com.inspiredandroid.kai.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random

expect class ConversationStorage(appSettings: AppSettings) {
    val conversations: StateFlow<List<Conversation>>

    suspend fun loadConversations()
    suspend fun saveConversation(conversation: Conversation)
    suspend fun deleteConversation(id: String)
    suspend fun deleteAllConversations()
}

@OptIn(ExperimentalEncodingApi::class)
abstract class BaseConversationStorage(private val appSettings: AppSettings) {
    protected val mutableConversations = MutableStateFlow<List<Conversation>>(emptyList())
    open val conversations: StateFlow<List<Conversation>> = mutableConversations.asStateFlow()

    protected val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    protected fun getEncryptionKey(): ByteArray {
        val existingKey = appSettings.getEncryptionKey()
        if (existingKey != null) {
            return existingKey
        }

        val newKey = ByteArray(32)
        Random.nextBytes(newKey)
        appSettings.setEncryptionKey(newKey)
        return newKey
    }

    protected fun encrypt(data: String): ByteArray {
        val key = getEncryptionKey()
        val bytes = data.encodeToByteArray()
        val encrypted = ByteArray(bytes.size)
        for (i in bytes.indices) {
            encrypted[i] = (bytes[i].toInt() xor key[i % key.size].toInt()).toByte()
        }
        return encrypted
    }

    protected fun decrypt(data: ByteArray): String {
        val key = getEncryptionKey()
        val decrypted = ByteArray(data.size)
        for (i in data.indices) {
            decrypted[i] = (data[i].toInt() xor key[i % key.size].toInt()).toByte()
        }
        return decrypted.decodeToString()
    }

    protected fun serializeConversations(conversations: List<Conversation>): String = json.encodeToString(ConversationsData(conversations = conversations))

    protected fun deserializeConversations(data: String): List<Conversation> = try {
        json.decodeFromString<ConversationsData>(data).conversations
    } catch (e: Exception) {
        emptyList()
    }

    fun updateConversation(conversation: Conversation) {
        mutableConversations.update { current ->
            val index = current.indexOfFirst { it.id == conversation.id }
            if (index >= 0) {
                current.toMutableList().apply { set(index, conversation) }
            } else {
                current + conversation
            }
        }
    }

    fun removeConversation(id: String) {
        mutableConversations.update { current ->
            current.filter { it.id != id }
        }
    }

    fun clearAllConversations() {
        mutableConversations.update { emptyList() }
    }

    companion object {
        const val FILE_NAME = "conversations.enc"
    }
}
