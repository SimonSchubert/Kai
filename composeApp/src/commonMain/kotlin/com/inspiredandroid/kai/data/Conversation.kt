package com.inspiredandroid.kai.data

import kotlinx.serialization.Serializable

@Serializable
data class Conversation(
    val id: String,
    val messages: List<Message>,
    val createdAt: Long,
    val updatedAt: Long,
) {
    @Serializable
    data class Message(
        val id: String,
        val role: String,
        val content: String,
        val mimeType: String? = null,
        val data: String? = null,
    )
}

@Serializable
data class ConversationsData(
    val version: Int = 2,
    val conversations: List<Conversation>,
)
