package com.inspiredandroid.kai.network.dtos.openaicompatible

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenAICompatibleChatResponseDto(
    val choices: List<Choice>,
) {
    @Serializable
    data class Choice(val message: Message? = null) {
        @Serializable
        data class Message(
            val role: String? = null,
            val content: String? = null,
            @SerialName("tool_calls")
            val toolCalls: List<ToolCall>? = null,
        )
    }

    @Serializable
    data class ToolCall(
        val id: String,
        val type: String = "function",
        val function: FunctionCall,
    )

    @Serializable
    data class FunctionCall(
        val name: String,
        val arguments: String,
    )
}
