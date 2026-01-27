package com.inspiredandroid.kai.network.dtos.openaicompatible

import kotlinx.serialization.Serializable

@Serializable
data class OpenAICompatibleChatRequestDto(
    val messages: List<Message>,
    val model: String,
) {
    @Serializable
    data class Message(
        val role: String,
        val content: String,
    )
}
