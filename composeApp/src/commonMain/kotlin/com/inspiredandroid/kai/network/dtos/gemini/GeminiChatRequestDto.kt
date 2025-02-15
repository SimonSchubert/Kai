package com.inspiredandroid.kai.network.dtos.gemini

import kotlinx.serialization.Serializable

@Serializable
data class GeminiChatRequestDto(
    val contents: List<Content>,
) {
    @Serializable
    data class Content(
        val parts: List<Part>,
        val role: String,
    )

    @Serializable
    data class Part(
        val text: String? = null,
        val inline_data: InlineData? = null,
    )

    @Serializable
    data class InlineData(
        val mime_type: String,
        val data: String,
    )
}
