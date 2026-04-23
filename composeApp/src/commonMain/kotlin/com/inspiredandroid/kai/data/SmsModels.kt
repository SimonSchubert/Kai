package com.inspiredandroid.kai.data

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class SmsMessage(
    val id: Long,
    val address: String,
    val date: Long,
    val preview: String,
    val body: String = "",
    val read: Boolean = false,
)

@Serializable
data class SmsSyncState(
    val lastSeenId: Long = 0L,
    val lastSyncEpochMs: Long = 0L,
    val lastAttemptEpochMs: Long = 0L,
    val unreadCount: Int = 0,
    val lastError: String? = null,
)

enum class SmsDraftStatus {
    PENDING,
    SENDING,
    SENT,
    FAILED,
}

/**
 * An outgoing SMS the AI has staged. Nothing is actually sent until the user
 * taps Send in the `PendingSmsBanner` — the existence of a draft is the
 * defensive gate between AI intent and real-world action.
 */
@Immutable
@Serializable
data class SmsDraft(
    val id: String,
    val address: String,
    val body: String,
    val createdAtEpochMs: Long,
    val inReplyToSmsId: Long? = null,
    val status: SmsDraftStatus = SmsDraftStatus.PENDING,
    val lastError: String? = null,
)
