package com.inspiredandroid.kai.data

import com.inspiredandroid.kai.getAppFilesDirectory
import java.io.File

private const val LEGACY_FILE_NAME = "conversations.enc"

actual fun readLegacyConversationFile(): ByteArray? {
    val file = File(getAppFilesDirectory(), LEGACY_FILE_NAME)
    return if (file.exists()) file.readBytes() else null
}

actual fun deleteLegacyConversationFile() {
    val file = File(getAppFilesDirectory(), LEGACY_FILE_NAME)
    if (file.exists()) file.delete()
}
