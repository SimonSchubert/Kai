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
            // DeepSeek returns `reasoning_content`; OpenRouter returns `reasoning`.
            @SerialName("reasoning_content")
            val reasoningContent: String? = null,
            val reasoning: String? = null,
            @SerialName("tool_calls")
            val toolCalls: List<ToolCall>? = null,
        ) {
            /** Whichever reasoning field the provider used, normalized to one accessor. */
            val effectiveReasoning: String?
                get() = reasoningContent ?: reasoning

            /** Returns [content] if non-blank, otherwise falls back to reasoning. */
            val effectiveContent: String?
                get() {
                    val raw = content?.takeIf { it.isNotBlank() } ?: effectiveReasoning
                    // Some providers (e.g. Ollama) embed tool calls as <TOOLCALL>[...] markers
                    // in the content field alongside structured tool_calls — strip them.
                    if (raw != null && !toolCalls.isNullOrEmpty()) {
                        val stripped = raw.replace(toolCallMarkerRegex, "").trim()
                        return stripped.takeIf { it.isNotBlank() }
                    }
                    return raw
                }

            /** True when the effective content comes from reasoning rather than [content]. */
            val isContentFromReasoning: Boolean
                get() = content.isNullOrBlank() && !effectiveReasoning.isNullOrBlank()

            /**
             * Reasoning trace with the answer text trimmed off if the provider appended it.
             * LongCat (flash thinking) and a few others stream the final answer as the tail of
             * `reasoning_content`, then return the same text in `content` — without this, the
             * "Thinking" section duplicates the answer rendered below it.
             */
            fun reasoningTraceFor(answer: String?): String? {
                val reasoning = effectiveReasoning ?: return null
                if (answer.isNullOrBlank() || isContentFromReasoning) return reasoning
                val trimmedReasoning = reasoning.trimEnd()
                val trimmedAnswer = answer.trim()
                if (!trimmedReasoning.endsWith(trimmedAnswer)) return reasoning
                return trimmedReasoning.removeSuffix(trimmedAnswer).trimEnd().takeIf { it.isNotBlank() }
            }
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
