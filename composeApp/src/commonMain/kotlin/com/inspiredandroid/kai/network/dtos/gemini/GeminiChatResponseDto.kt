package com.inspiredandroid.kai.network.dtos.gemini

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class GeminiChatResponseDto(
    val candidates: List<Candidate>,
) {
    @Serializable
    data class Candidate(val content: Content? = null)

    @Serializable
    data class Content(val parts: List<Part>? = null)

    @Serializable
    data class Part(
        val text: String? = null,
        val functionCall: FunctionCall? = null,
        val thoughtSignature: String? = null,
        val thought: Boolean? = null,
    ) {
        val isThought: Boolean get() = thought == true
    }

    @Serializable
    data class FunctionCall(
        val name: String,
        val args: Map<String, JsonElement>? = null,
    )
}

fun GeminiChatResponseDto.extractText(): String = candidates.firstOrNull()?.content?.parts
    ?.filterNot { it.isThought }
    ?.joinToString("\n") { it.text ?: "" }
    ?: ""
