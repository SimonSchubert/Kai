@file:OptIn(ExperimentalForeignApi::class)

package com.inspiredandroid.kai.data

import com.inspiredandroid.kai.getAppFilesDirectory
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy

private const val LEGACY_FILE_NAME = "conversations.enc"

actual fun readLegacyConversationFile(): ByteArray? {
    val path = "${getAppFilesDirectory()}/$LEGACY_FILE_NAME"
    val data = NSData.dataWithContentsOfFile(path) ?: return null
    val size = data.length.toInt()
    if (size == 0) return null
    val bytes = ByteArray(size)
    bytes.usePinned { pinned ->
        memcpy(pinned.addressOf(0), data.bytes, data.length)
    }
    return bytes
}

actual fun deleteLegacyConversationFile() {
    val path = "${getAppFilesDirectory()}/$LEGACY_FILE_NAME"
    NSFileManager.defaultManager.removeItemAtPath(path, null)
}
