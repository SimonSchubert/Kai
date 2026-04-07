package com.inspiredandroid.kai.data

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class EmailAccount(
    val id: String,
    val email: String,
    val displayName: String = "",
    val imapHost: String,
    val imapPort: Int = 993,
    val smtpHost: String,
    val smtpPort: Int = 587,
    val username: String = "",
    val useStartTls: Boolean = true,
)

@Serializable
data class EmailMessage(
    val uid: Long,
    val accountId: String,
    val from: String,
    val to: String = "",
    val subject: String,
    val date: String = "",
    val preview: String = "",
    val body: String = "",
    val messageId: String = "",
    val isRead: Boolean = false,
)

@Serializable
data class EmailSyncState(
    val accountId: String,
    val lastSeenUid: Long = 0L,
    val lastSyncEpochMs: Long = 0L,
    val unreadCount: Int = 0,
)
