package com.inspiredandroid.kai.network.dtos.openairesponses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response body of OpenAI's Responses API (`POST /v1/responses`).
 *
 * Unlike chat completions there is no single `message` object: [output] is an ordered list of
 * typed items — `reasoning`, `message`, `function_call` — and a turn can contain several of each.
 * One flat [OutputItem] covers all of them (fields a given type doesn't use stay null) because the
 * union is small and kotlinx polymorphism would need a registered hierarchy for no benefit.
 */
@Serializable
data class OpenAIResponsesResponseDto(
    val output: List<OutputItem> = emptyList(),
    val status: String? = null,
    val error: ResponseError? = null,
) {
    @Serializable
    data class OutputItem(
        val type: String? = null,
        val role: String? = null,
        /** `message` items: the assistant's answer, as `output_text` parts. */
        val content: List<ContentPart>? = null,
        /** `reasoning` items: the chain-of-thought summary, as `summary_text` parts. */
        val summary: List<ContentPart>? = null,
        /** `function_call` items: the id the matching `function_call_output` must reference. */
        @SerialName("call_id")
        val callId: String? = null,
        val name: String? = null,
        val arguments: String? = null,
    )

    @Serializable
    data class ContentPart(
        val type: String? = null,
        val text: String? = null,
    )

    @Serializable
    data class ResponseError(
        val message: String? = null,
        val code: String? = null,
    )

    /** Assistant answer text, concatenated across every `message` item in the turn. */
    val outputText: String?
        get() = output.filter { it.type == "message" }
            .flatMap { it.content.orEmpty() }
            .mapNotNull { it.text?.takeIf { text -> text.isNotEmpty() } }
            .joinToString("")
            .takeIf { it.isNotBlank() }

    /**
     * Reasoning summary, when the model returned one. OpenAI only emits summaries for verified
     * organizations that request them, so this is usually null — the raw chain-of-thought is
     * never exposed by this API.
     */
    val reasoningSummary: String?
        get() = output.filter { it.type == "reasoning" }
            .flatMap { it.summary.orEmpty() }
            .mapNotNull { it.text?.takeIf { text -> text.isNotBlank() } }
            .joinToString("\n\n")
            .takeIf { it.isNotBlank() }

    /** Tool calls requested by this turn, in the order the model emitted them. */
    val functionCalls: List<OutputItem>
        get() = output.filter { it.type == "function_call" && it.name != null }
}
