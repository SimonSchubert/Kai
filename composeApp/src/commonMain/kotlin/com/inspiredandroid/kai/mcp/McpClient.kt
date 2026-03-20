package com.inspiredandroid.kai.mcp

import com.inspiredandroid.kai.httpClient
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

class McpClient(
    private val url: String,
    private val headers: Map<String, String>,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val client: HttpClient = httpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 10_000
        }
    }
    private var sessionId: String? = null
    private var requestId = 0

    private fun nextId(): Int = ++requestId

    private suspend fun sendRequest(method: String, params: kotlinx.serialization.json.JsonElement? = null): JsonRpcResponse {
        val request = JsonRpcRequest(
            id = nextId(),
            method = method,
            params = params,
        )
        val requestBody = json.encodeToString(JsonRpcRequest.serializer(), request)

        val response = client.post(url) {
            contentType(ContentType.Application.Json)
            header("Accept", "application/json, text/event-stream")
            sessionId?.let { header("Mcp-Session-Id", it) }
            this@McpClient.headers.keys.forEach { key ->
                header(key, this@McpClient.headers[key]!!)
            }
            setBody(requestBody)
        }

        // Track session ID from response
        response.headers["Mcp-Session-Id"]?.let { sessionId = it }

        val responseText = response.bodyAsText()

        // Handle SSE response
        if (response.headers["Content-Type"]?.contains("text/event-stream") == true) {
            return parseSseResponse(responseText)
        }

        return json.decodeFromString(JsonRpcResponse.serializer(), responseText)
    }

    private fun parseSseResponse(sseText: String): JsonRpcResponse {
        // Parse SSE format: look for "data: " lines and find the JSON-RPC response
        val lines = sseText.lines()
        val dataLines = lines.filter { it.startsWith("data: ") }
        for (line in dataLines) {
            val data = line.removePrefix("data: ").trim()
            if (data.isEmpty()) continue
            try {
                return json.decodeFromString(JsonRpcResponse.serializer(), data)
            } catch (_: Exception) {
                // Not a valid JSON-RPC response, continue
            }
        }
        throw McpException("No valid JSON-RPC response found in SSE stream")
    }

    suspend fun initialize() {
        val params = buildJsonObject {
            put("protocolVersion", JsonPrimitive("2024-11-05"))
            put(
                "capabilities",
                buildJsonObject {},
            )
            put(
                "clientInfo",
                buildJsonObject {
                    put("name", JsonPrimitive("Kai"))
                    put("version", JsonPrimitive("1.0"))
                },
            )
        }
        val response = sendRequest("initialize", params)
        if (response.error != null) {
            throw McpException("Initialize failed: ${response.error.message}")
        }

        // Send initialized notification (no id, no response expected)
        sendNotification("notifications/initialized")
    }

    private suspend fun sendNotification(method: String) {
        val body = buildJsonObject {
            put("jsonrpc", JsonPrimitive("2.0"))
            put("method", JsonPrimitive(method))
        }
        val requestBody = json.encodeToString(JsonObject.serializer(), body)

        client.post(url) {
            contentType(ContentType.Application.Json)
            sessionId?.let { header("Mcp-Session-Id", it) }
            this@McpClient.headers.keys.forEach { key ->
                header(key, this@McpClient.headers[key]!!)
            }
            setBody(requestBody)
        }
    }

    suspend fun listTools(): List<McpToolDefinition> {
        val response = sendRequest("tools/list")
        if (response.error != null) {
            throw McpException("tools/list failed: ${response.error.message}")
        }
        val result = response.result ?: return emptyList()
        val toolsResult = json.decodeFromJsonElement<McpToolsResult>(result)
        return toolsResult.tools
    }

    suspend fun callTool(name: String, arguments: JsonObject): String {
        val params = buildJsonObject {
            put("name", JsonPrimitive(name))
            put("arguments", arguments)
        }
        val response = sendRequest("tools/call", params)
        if (response.error != null) {
            throw McpException("tools/call failed: ${response.error.message}")
        }
        val result = response.result ?: return ""
        val callResult = json.decodeFromJsonElement<McpCallToolResult>(result)
        if (callResult.isError) {
            val errorText = callResult.content.mapNotNull { it.text }.joinToString("\n")
            throw McpException("Tool error: $errorText")
        }
        return callResult.content.mapNotNull { it.text }.joinToString("\n")
    }

    fun close() {
        client.close()
    }
}

class McpException(message: String) : Exception(message)
