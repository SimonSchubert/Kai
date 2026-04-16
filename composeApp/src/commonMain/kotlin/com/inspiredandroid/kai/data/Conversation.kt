package com.inspiredandroid.kai.data

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * A single file attachment on a chat message. Used both in-memory on `History` and
 * persisted on `Conversation.Message`. Binary content is base64-encoded.
 */
@Immutable
@Serializable
data class Attachment(
    val data: String,
    val mimeType: String,
    val fileName: String? = null,
)

@Serializable
data class Conversation(
    val id: String,
    val messages: List<Message>,
    val createdAt: Long,
    val updatedAt: Long,
    val title: String = "",
    val type: String = TYPE_CHAT,
) {
    companion object {
        const val TYPE_CHAT = "chat"
        const val TYPE_HEARTBEAT = "heartbeat"
        const val TYPE_INTERACTIVE = "interactive"
    }

    @Serializable
    data class Message(
        val id: String,
        val role: String,
        val content: String,
        val attachments: List<Attachment> = emptyList(),
        val uiSubmission: UiSubmission? = null,
        // Legacy single-file fields — retained for reading old persisted conversations.
        // New code writes only `attachments`; these remain null on newly saved messages.
        val mimeType: String? = null,
        val data: String? = null,
        val fileName: String? = null,
    )
}

/**
 * Snapshot of a kai-ui form the user submitted. Attached to the resulting User message so
 * the bubble renders as a frozen form (with the values the user picked) instead of the
 * cryptic "Responded with: ..." text. `sourceContent` holds the assistant message body that
 * originated the form — it's re-parsed at render time.
 */
@Immutable
@Serializable
data class UiSubmission(
    val sourceContent: String,
    val values: Map<String, String> = emptyMap(),
    val pressedEvent: String? = null,
)

@Serializable
data class ConversationsData(
    val version: Int = 2,
    val conversations: List<Conversation>,
)
