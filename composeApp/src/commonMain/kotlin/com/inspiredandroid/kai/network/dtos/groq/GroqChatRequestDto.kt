package com.inspiredandroid.kai.network.dtos.groq

import kotlinx.serialization.Serializable

@Serializable
data class GroqChatRequestDto(
    val messages: List<Message>,
    val model: String,
) {
    @Serializable
    data class Message(
        val role: String,
        val content: String,
    )
}
