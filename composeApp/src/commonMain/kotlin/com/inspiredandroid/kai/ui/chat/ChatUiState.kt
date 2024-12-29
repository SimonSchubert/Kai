@file:OptIn(ExperimentalUuidApi::class)

package com.inspiredandroid.kai.ui.chat

import com.inspiredandroid.kai.network.dtos.gemini.GeminiChatRequestDto
import com.inspiredandroid.kai.network.dtos.groq.GroqChatRequestDto
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class ChatUiState(
    val ask: (String) -> Unit,
    val history: List<History> = emptyList(),
    val isSpeechOutputEnabled: Boolean = false,
    val toggleSpeechOutput: () -> Unit,
    val retry: () -> Unit,
    val isLoading: Boolean = false,
    val error: String? = null,
    val clearHistory: () -> Unit,
    val isUsingSharedKey: Boolean = false,
)

data class History(val id: String = Uuid.random().toString(), val role: Role, val content: String) {
    enum class Role {
        USER,
        ASSISTANT,
    }
}

fun History.toGroqMessageDto(): GroqChatRequestDto.Message {
    val role = when (role) {
        History.Role.USER -> "user"
        History.Role.ASSISTANT -> "assistant"
    }
    return GroqChatRequestDto.Message(role = role, content = content)
}

fun History.toGeminiMessageDto(): GeminiChatRequestDto.Content {
    val role = when (role) {
        History.Role.USER -> "user"
        History.Role.ASSISTANT -> "model"
    }
    return GeminiChatRequestDto.Content(
        parts = listOf(GeminiChatRequestDto.Part(content)),
        role = role,
    )
}
