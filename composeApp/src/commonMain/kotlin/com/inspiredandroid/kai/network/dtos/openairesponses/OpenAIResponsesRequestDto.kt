package com.inspiredandroid.kai.network.dtos.openairesponses

import com.inspiredandroid.kai.network.dtos.openaicompatible.OpenAICompatibleChatRequestDto
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Request body for OpenAI's Responses API (`POST /v1/responses`).
 *
 * Needed because OpenAI rejects function tools combined with reasoning on
 * `/v1/chat/completions` for the GPT-5.6 family:
 * `400 Function tools with reasoning_effort are not supported for gpt-5.6-terra in
 * /v1/chat/completions. To use function tools, use /v1/responses or set reasoning_effort to
 * 'none'.` Those models reason by default, so the rejection happens even though Kai never sends
 * `reasoning_effort`.
 *
 * [input] holds heterogeneous items (messages, `function_call`, `function_call_output`) whose
 * required fields differ per type, so they are built as raw JSON objects — see
 * `data/providers/OpenAIResponsesInput.kt`.
 */
@Serializable
data class OpenAIResponsesRequestDto(
    val input: List<JsonObject>,
    val model: String? = null,
    val tools: List<Tool>? = null,
    /**
     * Kept `false` so OpenAI does not retain the conversation server-side. Kai replays the whole
     * history on every request, so it never needs `previous_response_id` chaining.
     */
    val store: Boolean = false,
) {
    /**
     * Same JSON Schema as the chat-completions tool, minus the `function` wrapper — the Responses
     * API tags function tools inline. The parameter schema is byte-identical, so the shared
     * `OpenAISchemaDialect` conversion is reused rather than duplicated.
     *
     * [strict] is explicitly `false`: omitting it makes the API attempt strict mode, which
     * requires every property to be listed in `required`. Kai's tool schemas have optional
     * parameters, so strict mode would reject them.
     */
    @Serializable
    data class Tool(
        val type: String = "function",
        val name: String,
        val description: String? = null,
        val parameters: OpenAICompatibleChatRequestDto.Parameters? = null,
        val strict: Boolean = false,
    )
}
