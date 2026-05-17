package com.inspiredandroid.kai.data.providers

import com.inspiredandroid.kai.data.Service
import com.inspiredandroid.kai.network.dtos.openaicompatible.OpenAICompatibleChatRequestDto
import com.inspiredandroid.kai.ui.chat.History
import com.inspiredandroid.kai.ui.chat.toGroqMessageDto
import kotlinx.serialization.json.JsonPrimitive

internal fun buildOpenAIMessages(
    service: Service,
    messages: List<History>,
    systemPrompt: String?,
): List<OpenAICompatibleChatRequestDto.Message> = buildList {
    if (!systemPrompt.isNullOrEmpty()) {
        add(
            OpenAICompatibleChatRequestDto.Message(
                role = "system",
                content = JsonPrimitive(systemPrompt),
            ),
        )
    }
    addAll(
        messages.map { it.toGroqMessageDto(service.reasoningRequestMode) }
            .filter { msg ->
                if (msg.role == "tool" && msg.tool_call_id == null) return@filter false
                if (msg.role == "assistant" && msg.content == null && msg.tool_calls.isNullOrEmpty()) return@filter false
                true
            },
    )
}
