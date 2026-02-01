package com.inspiredandroid.kai.network.dtos.openaicompatible

import kotlinx.serialization.Serializable

@Serializable
data class OpenAICompatibleChatRequestDto(
    val messages: List<Message>,
    val model: String,
    val tools: List<Tool>? = null, // Optional: list of tools the model can call
    // You can add more fields as needed, e.g.:
    // val tool_choice: String? = null,           // "auto" | "none" | "required" | { "type": "function", "function": { "name": "..." } }
    // val response_format: ResponseFormat? = null,
    // val temperature: Double? = null,
    // etc.
) {
    @Serializable
    data class Message(
        val role: String, // "system", "user", "assistant", "tool"
        val content: String? = null, // Can be null for tool messages sometimes
        val tool_calls: List<ToolCall>? = null,
        val tool_call_id: String? = null, // Required for "tool" role messages
    )

    @Serializable
    data class Tool(
        val type: String = "function", // Currently only "function" is widely supported
        val function: Function,
    )

    @Serializable
    data class Function(
        val name: String,
        val description: String? = null,
        val parameters: Parameters? = null,
        val strict: Boolean? = null, // Optional (for Structured Outputs / strict mode)
    )

    @Serializable
    data class Parameters(
        val type: String = "object",
        val properties: Map<String, PropertySchema>,
        val required: List<String>? = null,
        val additionalProperties: Boolean? = null,
        // You can add more JSON Schema fields if needed (enum, items, etc.)
    )

    @Serializable
    data class PropertySchema(
        val type: String, // "string", "number", "boolean", "integer", "array", "object"
        val description: String? = null,
        val enum: List<String>? = null, // Optional enum values
        val items: PropertySchema? = null, // For type: "array"
        val properties: Map<String, PropertySchema>? = null, // For type: "object"
        val required: List<String>? = null,
        val additionalProperties: Boolean? = null,
        // Add more nested schema support as needed (minLength, maximum, etc.)
    )

    // Optional: if you want to represent tool calls in responses
    @Serializable
    data class ToolCall(
        val id: String,
        val type: String = "function",
        val function: FunctionCall,
    )

    @Serializable
    data class FunctionCall(
        val name: String,
        val arguments: String, // JSON string of the args → parse it in your code
    )
}
