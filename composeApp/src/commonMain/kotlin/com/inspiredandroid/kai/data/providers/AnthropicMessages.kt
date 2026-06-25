package com.inspiredandroid.kai.data.providers

import com.inspiredandroid.kai.network.dtos.anthropic.AnthropicChatRequestDto
import com.inspiredandroid.kai.ui.chat.History
import com.inspiredandroid.kai.ui.chat.toAnthropicContentBlocks
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

internal fun buildAnthropicMessages(
    messages: List<History>,
): List<AnthropicChatRequestDto.Message> = buildList {
    // Mark the penultimate user turn with a cache breakpoint so the accumulated
    // conversation history is reused on the next request. The last user message
    // is always new, so caching it gives zero benefit.
    val userIndices = messages.indices.filter { messages[it].role == History.Role.USER }
    val cacheBreakpointIndex = if (userIndices.size >= 2) userIndices[userIndices.size - 2] else -1
    val lastUserIndex = userIndices.lastOrNull() ?: -1

    var pendingToolResults = mutableListOf<JsonElement>()

    for ((index, msg) in messages.withIndex()) {
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
                val addCache = index == cacheBreakpointIndex
                val addTimestamp = msg.role == History.Role.USER && index == lastUserIndex
                add(
                    AnthropicChatRequestDto.Message(
                        role = if (msg.role == History.Role.ASSISTANT) "assistant" else "user",
                        content = msg.toAnthropicContentBlocks(addCacheBreakpoint = addCache, addTimestamp = addTimestamp),
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
