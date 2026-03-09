package com.inspiredandroid.kai.mcp

import com.inspiredandroid.kai.network.tools.ParameterSchema
import com.inspiredandroid.kai.network.tools.Tool
import com.inspiredandroid.kai.network.tools.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class McpTool(
    private val client: McpClient,
    private val metadata: McpToolMetadata,
) : Tool {

    override val schema: ToolSchema = ToolSchema(
        name = metadata.name,
        description = metadata.description,
        parameters = convertInputSchema(metadata.inputSchema),
    )

    override val timeout: Duration = 60.seconds

    override suspend fun execute(args: Map<String, Any>): Any {
        val jsonArgs = buildJsonObject {
            for ((key, value) in args) {
                when (value) {
                    is String -> put(key, JsonPrimitive(value))
                    is Boolean -> put(key, JsonPrimitive(value))
                    is Int -> put(key, JsonPrimitive(value))
                    is Long -> put(key, JsonPrimitive(value))
                    is Double -> put(key, JsonPrimitive(value))
                    is Number -> put(key, JsonPrimitive(value.toDouble()))
                    else -> put(key, JsonPrimitive(value.toString()))
                }
            }
        }
        return try {
            val result = client.callTool(metadata.name, jsonArgs)
            mapOf("success" to true, "result" to result)
        } catch (e: Exception) {
            mapOf("success" to false, "error" to (e.message ?: "MCP tool call failed"))
        }
    }

    companion object {
        fun toolId(serverId: String, toolName: String): String = "mcp_${serverId}_$toolName"

        fun convertInputSchema(inputSchema: JsonObject?): Map<String, ParameterSchema> {
            if (inputSchema == null) return emptyMap()
            val properties = inputSchema["properties"]?.jsonObject ?: return emptyMap()
            val required = try {
                inputSchema["required"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet() ?: emptySet()
            } catch (_: Exception) {
                emptySet()
            }

            return buildMap {
                for ((name, prop) in properties) {
                    try {
                        val propObj = prop.jsonObject
                        val type = propObj["type"]?.jsonPrimitive?.content ?: "string"
                        val description = propObj["description"]?.jsonPrimitive?.content ?: ""
                        put(name, ParameterSchema(type, description, name in required))
                    } catch (_: Exception) {
                        // Skip malformed properties
                    }
                }
            }
        }
    }
}
