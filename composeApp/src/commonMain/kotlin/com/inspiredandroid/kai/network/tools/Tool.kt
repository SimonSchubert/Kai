package com.inspiredandroid.kai.network.tools

import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Serializable
data class ToolSchema(
    val name: String,
    val description: String,
    val parameters: Map<String, ParameterSchema>, // e.g., type: "string", required: true
)

@Serializable
data class ParameterSchema(val type: String, val description: String, val required: Boolean)

interface Tool {
    val schema: ToolSchema
    val timeout: Duration get() = 30.seconds
    suspend fun execute(args: Map<String, Any>): Any // Return result as JSON-serializable
}
