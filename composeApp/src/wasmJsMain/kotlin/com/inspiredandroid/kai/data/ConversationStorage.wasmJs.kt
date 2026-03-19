package com.inspiredandroid.kai.data

import kotlinx.browser.localStorage
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private const val LEGACY_STORAGE_KEY = "kaimutableConversations"

@OptIn(ExperimentalEncodingApi::class)
actual fun readLegacyConversationFile(): ByteArray? {
    val stored = localStorage.getItem(LEGACY_STORAGE_KEY) ?: return null
    if (stored.startsWith("{")) return null // Already plain JSON, not legacy
    return try {
        Base64.decode(stored)
    } catch (_: Exception) {
        null
    }
}

actual fun deleteLegacyConversationFile() {
    localStorage.removeItem(LEGACY_STORAGE_KEY)
}
