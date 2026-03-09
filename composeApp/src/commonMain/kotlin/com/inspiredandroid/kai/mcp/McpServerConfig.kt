package com.inspiredandroid.kai.mcp

import kotlinx.serialization.Serializable

@Serializable
data class McpServerConfig(
    val id: String,
    val name: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val isEnabled: Boolean = true,
)
