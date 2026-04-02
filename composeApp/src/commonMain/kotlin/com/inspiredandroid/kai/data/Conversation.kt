package com.inspiredandroid.kai.data

import kotlinx.serialization.Serializable

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
        val mimeType: String? = null,
        val data: String? = null,
        val fileName: String? = null,
    )
}

@Serializable
data class ConversationsData(
    val version: Int = 2,
    val conversations: List<Conversation>,
)
