package com.inspiredandroid.kai.mcp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: Int,
    val method: String,
    val params: JsonElement? = null,
)

@Serializable
data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val id: Int? = null,
    val result: JsonElement? = null,
    val error: JsonRpcError? = null,
)

@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null,
)

@Serializable
data class McpToolDefinition(
    val name: String,
    val description: String? = null,
    val inputSchema: JsonObject? = null,
)

@Serializable
data class McpToolsResult(
    val tools: List<McpToolDefinition> = emptyList(),
)

@Serializable
data class McpCallToolResult(
    val content: List<McpContent> = emptyList(),
    @SerialName("isError")
    val isError: Boolean = false,
)

@Serializable
data class McpContent(
    val type: String,
    val text: String? = null,
)

data class McpToolMetadata(
    val serverId: String,
    val name: String,
    val description: String,
    val inputSchema: JsonObject?,
)
