package com.inspiredandroid.kai.network.dtos.openaicompatible

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private val toolCallMarkerRegex = Regex("<TOOLCALL>[\\s\\S]*?</TOOLCALL>|<TOOLCALL>[\\s\\S]*$")

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
            val reasoning: String? = null,
            @SerialName("tool_calls")
            val toolCalls: List<ToolCall>? = null,
        ) {
            /** Returns [content] if non-blank, otherwise falls back to [reasoning]. */
            val effectiveContent: String?
                get() {
                    val raw = content?.takeIf { it.isNotBlank() } ?: reasoning
                    // Some providers (e.g. Ollama) embed tool calls as <TOOLCALL>[...] markers
                    // in the content field alongside structured tool_calls — strip them.
                    if (raw != null && !toolCalls.isNullOrEmpty()) {
                        val stripped = raw.replace(toolCallMarkerRegex, "").trim()
                        return stripped.takeIf { it.isNotBlank() }
                    }
                    return raw
                }

            /** True when the effective content comes from [reasoning] rather than [content]. */
            val isContentFromReasoning: Boolean
                get() = content.isNullOrBlank() && !reasoning.isNullOrBlank()
        }
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
