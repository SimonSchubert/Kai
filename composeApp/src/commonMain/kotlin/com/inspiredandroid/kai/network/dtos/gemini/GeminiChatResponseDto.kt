package com.inspiredandroid.kai.network.dtos.gemini

import kotlinx.serialization.Serializable

@Serializable
data class GeminiChatResponseDto(
    val candidates: List<Candidate>,
) {
    @Serializable
    data class Candidate(val content: Content? = null)

    @Serializable
    data class Content(val parts: List<Part>? = null)

    @Serializable
    data class Part(val text: String? = null)
}
