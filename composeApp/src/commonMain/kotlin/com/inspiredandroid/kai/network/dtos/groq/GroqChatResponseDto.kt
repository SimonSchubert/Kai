package com.inspiredandroid.kai.network.dtos.groq

import kotlinx.serialization.Serializable

@Serializable
data class GroqChatResponseDto(
    val choices: List<Choice>,
) {
    @Serializable
    data class Choice(val message: Message? = null)

    @Serializable
    data class Message(val role: String? = null, val content: String? = null)
}
