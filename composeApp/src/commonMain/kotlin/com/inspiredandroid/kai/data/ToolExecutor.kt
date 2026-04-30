package com.inspiredandroid.kai.data

import com.inspiredandroid.kai.getAvailableTools
import com.inspiredandroid.kai.getPlatformToolDefinitions
import com.inspiredandroid.kai.smartTruncate
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.getString

private const val MAX_TOOL_RESULT_LENGTH = 20_000

class ToolExecutor {

    private val jsonParser = Json { ignoreUnknownKeys = true }

    fun formatJsonElement(element: JsonElement): String = when {
        element is JsonNull -> "null"
        element is JsonPrimitive && element.isString -> "\"${element.content}\""
        element is JsonPrimitive -> element.content
        else -> element.toString()
    }

    suspend fun executeTool(
        name: String,
        arguments: String,
        conversationId: String? = null,
    ): String {
        val tools = getAvailableTools()
        val tool = tools.find { it.schema.name == name }
            ?: return """{"success": false, "error": "Unknown tool: $name"}"""

        val args = try {
            parseJsonToMap(arguments)
        } catch (e: Exception) {
            return """{"success": false, "error": "Failed to parse arguments: ${e.message}"}"""
        }

        return try {
            val result = withTimeout(tool.timeout) {
                if (conversationId != null) {
                    withContext(ConversationIdElement(conversationId)) { tool.execute(args) }
                } else {
                    tool.execute(args)
                }
            }
            val resultString = when (result) {
                is Map<*, *> -> {
                    val jsonObject = JsonObject(
                        result.entries.associate { (k, v) ->
                            k.toString() to anyToJsonElement(v)
                        },
                    )
                    jsonParser.encodeToString(JsonElement.serializer(), jsonObject)
                }

                is String -> result

                else -> """{"result": "$result"}"""
            }
            truncateResult(resultString)
        } catch (e: TimeoutCancellationException) {
            """{"success": false, "error": "Tool '$name' timed out after ${tool.timeout}"}"""
        } catch (e: Exception) {
            """{"success": false, "error": "Tool execution failed: ${e.message}"}"""
        }
    }

    private fun truncateResult(result: String): String = result.smartTruncate(MAX_TOOL_RESULT_LENGTH)

    private fun anyToJsonElement(value: Any?): JsonElement = when (value) {
        null -> JsonNull

        is String -> JsonPrimitive(value)

        is Boolean -> JsonPrimitive(value)

        is Number -> JsonPrimitive(value)

        is Map<*, *> -> JsonObject(
            value.entries.associate { (k, v) -> k.toString() to anyToJsonElement(v) },
        )

        is List<*> -> JsonArray(value.map { anyToJsonElement(it) })

        else -> JsonPrimitive(value.toString())
    }

    private fun parseJsonToMap(json: String): Map<String, Any> {
        val jsonObject = jsonParser.parseToJsonElement(json).jsonObject
        return jsonObject.toMap()
    }

    private fun JsonObject.toMap(): Map<String, Any> = entries.associate { (key, value) ->
        key to when (value) {
            is JsonPrimitive if value.isString -> value.content
            is JsonPrimitive if value.booleanOrNull != null -> value.boolean
            is JsonPrimitive if value.intOrNull != null -> value.int
            is JsonPrimitive if value.doubleOrNull != null -> value.double
            is JsonObject -> value.toMap()
            else -> value.toString()
        }
    }

    suspend fun getToolDisplayName(toolId: String): String {
        val toolInfo = getPlatformToolDefinitions().find { it.id == toolId } ?: return toolId
        return toolInfo.nameRes?.let { getString(it) } ?: toolInfo.name
    }
}
