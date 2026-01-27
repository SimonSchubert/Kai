package com.inspiredandroid.kai.network.dtos.openaicompatible

import kotlinx.serialization.Serializable

@Serializable
data class OpenAICompatibleChatResponseDto(
    val choices: List<Choice>,
) {
    @Serializable
    data class Choice(val message: Message? = null) {
        @Serializable
        data class Message(val role: String? = null, val content: String? = null)
    }
}
