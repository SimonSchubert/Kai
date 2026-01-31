package com.inspiredandroid.kai.data

import com.inspiredandroid.kai.getAppFilesDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

actual class ConversationStorage actual constructor(
    appSettings: AppSettings,
) : BaseConversationStorage(appSettings) {

    actual override val conversations: StateFlow<List<Conversation>>
        get() = super.conversations

    private fun getFile(): File {
        val dir = getAppFilesDirectory()
        return File(dir, FILE_NAME)
    }

    actual suspend fun loadConversations() {
        withContext(Dispatchers.IO) {
            try {
                val file = getFile()
                if (file.exists()) {
                    val encryptedData = file.readBytes()
                    val decryptedJson = decrypt(encryptedData)
                    val loaded = deserializeConversations(decryptedJson)
                    mutableConversations.value = loaded
                }
            } catch (e: Exception) {
                mutableConversations.value = emptyList()
            }
        }
    }

    actual suspend fun saveConversation(conversation: Conversation) {
        withContext(Dispatchers.IO) {
            updateConversation(conversation)
            saveToFile()
        }
    }

    actual suspend fun deleteConversation(id: String) {
        withContext(Dispatchers.IO) {
            removeConversation(id)
            saveToFile()
        }
    }

    actual suspend fun deleteAllConversations() {
        withContext(Dispatchers.IO) {
            clearAllConversations()
            val file = getFile()
            if (file.exists()) {
                file.delete()
            }
        }
    }

    private fun saveToFile() {
        val file = getFile()
        val jsonData = serializeConversations(mutableConversations.value)
        val encryptedData = encrypt(jsonData)
        file.writeBytes(encryptedData)
    }
}
