@file:OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.inspiredandroid.kai.data

import com.inspiredandroid.kai.getAppFilesDirectory
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.posix.memcpy

actual class ConversationStorage actual constructor(
    appSettings: AppSettings,
) : BaseConversationStorage(appSettings) {

    actual override val conversations: StateFlow<List<Conversation>>
        get() = super.conversations

    private fun getFilePath(): String {
        val dir = getAppFilesDirectory()
        return "$dir/$FILE_NAME"
    }

    actual suspend fun loadConversations() {
        withContext(Dispatchers.IO) {
            try {
                val filePath = getFilePath()
                val data = NSData.dataWithContentsOfFile(filePath)
                if (data != null) {
                    val bytes = data.toByteArray()
                    val decryptedJson = decrypt(bytes)
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
            val filePath = getFilePath()
            NSFileManager.defaultManager.removeItemAtPath(filePath, null)
        }
    }

    private fun saveToFile() {
        val filePath = getFilePath()
        val jsonData = serializeConversations(mutableConversations.value)
        val encryptedData = encrypt(jsonData)
        val nsData = encryptedData.toNSData()
        nsData.writeToFile(filePath, atomically = true)
    }

    private fun NSData.toByteArray(): ByteArray {
        val size = this.length.toInt()
        val bytes = ByteArray(size)
        if (size > 0) {
            bytes.usePinned { pinned ->
                memcpy(pinned.addressOf(0), this.bytes, this.length)
            }
        }
        return bytes
    }

    private fun ByteArray.toNSData(): NSData = memScoped {
        NSData.create(bytes = allocArrayOf(this@toNSData), length = this@toNSData.size.toULong())
    }
}
