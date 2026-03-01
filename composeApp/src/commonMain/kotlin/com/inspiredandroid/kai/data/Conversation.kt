package com.inspiredandroid.kai.data

import kotlinx.serialization.Serializable

@Serializable
data class Conversation(
    val id: String,
    val title: String,
    val messages: List<Message>,
    val createdAt: Long,
    val updatedAt: Long,
    val serviceId: String,
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
    val version: Int = 1,
    val conversations: List<Conversation>,
)
