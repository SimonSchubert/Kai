package com.inspiredandroid.kai.network.dtos.gemini

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class GeminiChatRequestDto(
    val contents: List<Content>,
    val tools: List<GeminiTool>? = null,
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
        val functionCall: FunctionCall? = null,
        val functionResponse: FunctionResponse? = null,
        val thoughtSignature: String? = null,
    )

    @Serializable
    data class InlineData(
        val mime_type: String,
        val data: String,
    )

    @Serializable
    data class FunctionCall(
        val name: String,
        val args: Map<String, JsonElement>? = null,
    )

    @Serializable
    data class FunctionResponse(
        val name: String,
        val response: Map<String, JsonElement>,
    )
}

@Serializable
data class GeminiTool(
    val functionDeclarations: List<FunctionDeclaration>,
)

@Serializable
data class FunctionDeclaration(
    val name: String,
    val description: String,
    val parameters: FunctionParameters,
)

@Serializable
data class FunctionParameters(
    val type: String = "object",
    val properties: Map<String, PropertySchema>,
    val required: List<String> = emptyList(),
)

@Serializable
data class PropertySchema(
    val type: String,
    val description: String,
)
