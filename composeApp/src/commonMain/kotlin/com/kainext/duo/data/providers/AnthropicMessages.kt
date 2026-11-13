package com.kainext.duo.data.providers

import com.kainext.duo.network.dtos.anthropic.AnthropicChatRequestDto
import com.kainext.duo.ui.chat.History
import com.kainext.duo.ui.chat.toAnthropicContentBlocks
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

internal fun buildAnthropicMessages(
    messages: List<History>,
): List<AnthropicChatRequestDto.Message> = buildList {
    var pendingToolResults = mutableListOf<JsonElement>()

    for (msg in messages) {
        when (msg.role) {
            History.Role.TOOL_EXECUTING -> { /* skip */ }

            History.Role.TOOL -> {
                val blocks = msg.toAnthropicContentBlocks()
                if (blocks is JsonArray) {
                    pendingToolResults.addAll(blocks)
                }
            }

            else -> {
                if (pendingToolResults.isNotEmpty()) {
                    add(
                        AnthropicChatRequestDto.Message(
                            role = "user",
                            content = JsonArray(pendingToolResults),
                        ),
                    )
                    pendingToolResults = mutableListOf()
                }
                add(
                    AnthropicChatRequestDto.Message(
                        role = if (msg.role == History.Role.ASSISTANT) "assistant" else "user",
                        content = msg.toAnthropicContentBlocks(),
                    ),
                )
            }
        }
    }
    if (pendingToolResults.isNotEmpty()) {
        add(
            AnthropicChatRequestDto.Message(
                role = "user",
                content = JsonArray(pendingToolResults),
            ),
        )
    }
}
