@file:OptIn(ExperimentalUuidApi::class)

package com.inspiredandroid.kai.ui.chat

import com.inspiredandroid.kai.network.dtos.gemini.GeminiChatRequestDto
import com.inspiredandroid.kai.network.dtos.groq.GroqChatRequestDto
import io.github.vinceglb.filekit.core.PlatformFile
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class ChatUiState(
    val ask: (String, file: PlatformFile?) -> Unit,
    val history: List<History> = emptyList(),
    val isSpeechOutputEnabled: Boolean = false,
    val toggleSpeechOutput: () -> Unit,
    val retry: () -> Unit,
    val isLoading: Boolean = false,
    val error: String? = null,
    val clearHistory: () -> Unit,
    val isUsingSharedKey: Boolean = false,
    val allowFileAttachment: Boolean = false,
    val isSpeaking: Boolean = false,
    val isSpeakingContentId: String = "",
    val setIsSpeaking: (Boolean, String) -> Unit,
)

data class History(
    val id: String = Uuid.random().toString(),
    val role: Role,
    val content: String,
    val mimeType: String? = null,
    val data: String? = null,
) {
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
        parts = buildList {
            val data = if (data != null && mimeType != null) {
                GeminiChatRequestDto.InlineData(mime_type = mimeType, data = data)
            } else {
                null
            }
            if (data != null) {
                add(
                    GeminiChatRequestDto.Part(
                        inline_data = data,
                    ),
                )
            }
            add(
                GeminiChatRequestDto.Part(
                    text = content,
                ),
            )
        },
        role = role,
    )
}
