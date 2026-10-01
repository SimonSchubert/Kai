package com.inspiredandroid.kai.data

import com.inspiredandroid.kai.getAvailableTools
import com.inspiredandroid.kai.getPlatformToolDefinitions
import com.inspiredandroid.kai.network.tools.Tool
import com.inspiredandroid.kai.smartTruncate
import kotlinx.coroutines.CancellationException
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.jetbrains.compose.resources.getString

private const val MAX_TOOL_RESULT_LENGTH = 20_000

/**
 * `{"success": false, "error": message}`, built as JSON rather than by string interpolation —
 * error messages routinely contain quotes and newlines that would otherwise produce invalid JSON.
 */
internal fun toolErrorJson(message: String): String = buildJsonObject {
    put("success", false)
    put("error", message)
}.toString()

class ToolExecutor(
    private val toolsProvider: () -> List<Tool> = { getAvailableTools() },
) {

    private val jsonParser = Json { ignoreUnknownKeys = true }

    suspend fun executeTool(
        name: String,
        arguments: String,
        conversationId: String? = null,
    ): String {
        val tools = toolsProvider()
        val tool = tools.find { it.schema.name == name }
            ?: return toolErrorJson("Unknown tool: $name")

        val args = try {
            parseJsonToMap(arguments)
        } catch (e: Exception) {
            return toolErrorJson("Failed to parse arguments: ${e.message}")
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

                else -> buildJsonObject { put("result", result.toString()) }.toString()
            }
            truncateResult(resultString)
        } catch (e: TimeoutCancellationException) {
            toolErrorJson("Tool '$name' timed out after ${tool.timeout}")
        } catch (e: CancellationException) {
            // Cooperative cancellation (user pressed stop) must propagate, not become a
            // fake tool result the loop would keep reasoning about.
            throw e
        } catch (e: Exception) {
            toolErrorJson("Tool execution failed: ${e.message}")
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

    // An explicit JSON null means "not provided": dropping the key lets tools fall back to their
    // defaults instead of receiving the string "null".
    private fun JsonObject.toMap(): Map<String, Any> = entries
        .filter { (_, value) -> value !is JsonNull }
        .associate { (key, value) -> key to jsonElementToAny(value) }

    private fun jsonElementToAny(element: JsonElement): Any = when (element) {
        JsonNull -> "null"

        is JsonPrimitive -> when {
            element.isString -> element.content
            element.booleanOrNull != null -> element.boolean
            element.intOrNull != null -> element.int
            element.doubleOrNull != null -> element.double
            else -> element.content
        }

        is JsonObject -> element.toMap()

        is JsonArray -> element.map { jsonElementToAny(it) }
    }

    suspend fun getToolDisplayName(toolId: String): String {
        val toolInfo = getPlatformToolDefinitions().find { it.id == toolId } ?: return toolId
        return toolInfo.nameRes?.let { getString(it) } ?: toolInfo.name
    }
}
