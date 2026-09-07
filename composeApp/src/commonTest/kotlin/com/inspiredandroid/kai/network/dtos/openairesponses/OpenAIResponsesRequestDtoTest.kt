package com.inspiredandroid.kai.network.dtos.openairesponses

import com.inspiredandroid.kai.network.dtos.openaicompatible.OpenAICompatibleChatRequestDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Locks the wire shape of `/v1/responses` requests, using the same serializer settings the HTTP
 * client installs. `store` and `strict` carry meaning only because `encodeDefaults` emits them as
 * `false` instead of dropping them.
 */
class OpenAIResponsesRequestDtoTest {

    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
    }

    private fun encode(dto: OpenAIResponsesRequestDto): JsonObject = json.parseToJsonElement(
        json.encodeToString(OpenAIResponsesRequestDto.serializer(), dto),
    ) as JsonObject

    @Test
    fun `serializes a tool request the responses api accepts`() {
        val body = encode(
            OpenAIResponsesRequestDto(
                input = listOf(
                    buildJsonObject {
                        put("role", "user")
                        put("content", "hi")
                    },
                ),
                model = "gpt-5.6-luna",
                tools = listOf(
                    OpenAIResponsesRequestDto.Tool(
                        name = "get_local_time",
                        description = "Current time",
                        parameters = OpenAICompatibleChatRequestDto.Parameters(
                            properties = mapOf("tz" to OpenAICompatibleChatRequestDto.PropertySchema(type = "string")),
                            required = listOf("tz"),
                        ),
                    ),
                ),
            ),
        )

        assertEquals("gpt-5.6-luna", body["model"]?.jsonPrimitive?.content)
        // Nothing is retained server-side.
        assertEquals(false, body["store"]?.jsonPrimitive?.content?.toBoolean())

        val tool = (body["tools"] as JsonArray).single() as JsonObject
        // Function tools are tagged inline, not wrapped in a `function` object as on chat completions.
        assertEquals("function", tool["type"]?.jsonPrimitive?.content)
        assertEquals("get_local_time", tool["name"]?.jsonPrimitive?.content)
        assertNull(tool["function"])
        // Strict mode would reject Kai's optional tool parameters, so it must be opted out of.
        assertEquals(false, tool["strict"]?.jsonPrimitive?.content?.toBoolean())

        val parameters = tool["parameters"] as JsonObject
        assertEquals("object", parameters["type"]?.jsonPrimitive?.content)
        assertTrue((parameters["properties"] as JsonObject).containsKey("tz"))
    }

    @Test
    fun `omits tools entirely when none are declared`() {
        val body = encode(OpenAIResponsesRequestDto(input = emptyList(), model = "gpt-5.6-luna", tools = null))

        assertNull(body["tools"])
        assertEquals(0, (body["input"] as JsonArray).size)
    }
}
