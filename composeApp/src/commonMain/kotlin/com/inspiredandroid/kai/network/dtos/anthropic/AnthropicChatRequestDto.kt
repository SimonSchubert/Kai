package com.inspiredandroid.kai.network.dtos.anthropic

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class AnthropicChatRequestDto(
    val model: String,
    val messages: List<Message>,
    val max_tokens: Int = 8192,
    val system: String? = null,
    val tools: List<Tool>? = null,
) {
    @Serializable
    data class Message(
        val role: String,
        val content: JsonElement,
    )

    @Serializable
    data class Tool(
        val name: String,
        val description: String,
        val input_schema: InputSchema,
    )

    @Serializable
    data class InputSchema(
        val type: String = "object",
        val properties: Map<String, PropertySchema>,
        val required: List<String> = emptyList(),
    )

    @Serializable
    data class PropertySchema(
        val type: String,
        val description: String? = null,
        val enum: List<String>? = null,
        val items: PropertySchema? = null,
        val properties: Map<String, PropertySchema>? = null,
        val required: List<String>? = null,
    )
}
