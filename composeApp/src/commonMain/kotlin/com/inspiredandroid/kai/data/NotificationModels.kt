package com.inspiredandroid.kai.data

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class NotificationRecord(
    val id: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val subtext: String = "",
    val postedAtEpochMs: Long,
    val isOngoing: Boolean = false,
    val category: String = "",
    val preview: String = text.take(PREVIEW_CHARS),
) {
    companion object {
        const val PREVIEW_CHARS = 200
    }
}

@Serializable
data class NotificationSyncState(
    val listenerBound: Boolean = false,
    val lastBoundEpochMs: Long = 0L,
    val lastError: String? = null,
)
