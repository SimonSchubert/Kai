package com.inspiredandroid.kai.data.providers

import com.inspiredandroid.kai.data.Service
import com.inspiredandroid.kai.data.requiresResponsesApi
import com.inspiredandroid.kai.network.dtos.openaicompatible.OpenAICompatibleChatRequestDto
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Guards the chat-completions → Responses API translation that lets the GPT-5.6 family use tools
 * (issue #469). A wrong shape here is a hard 400 from OpenAI, not a degraded answer.
 */
class OpenAIResponsesInputTest {

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.content

    @Test
    fun `system and plain messages become easy input messages`() {
        val input = toResponsesInput(
            listOf(
                OpenAICompatibleChatRequestDto.Message(role = "system", content = JsonPrimitive("be brief")),
                OpenAICompatibleChatRequestDto.Message(role = "user", content = JsonPrimitive("hi")),
                OpenAICompatibleChatRequestDto.Message(role = "assistant", content = JsonPrimitive("hello")),
            ),
        )

        assertEquals(3, input.size)
        assertEquals(listOf("system", "user", "assistant"), input.map { it.str("role") })
        assertEquals(listOf("be brief", "hi", "hello"), input.map { it.str("content") })
        assertTrue(input.none { it.containsKey("type") }, "easy messages must not carry a type tag")
    }

    @Test
    fun `assistant tool calls become function_call items keyed by call_id`() {
        val input = toResponsesInput(
            listOf(
                OpenAICompatibleChatRequestDto.Message(
                    role = "assistant",
                    content = null,
                    tool_calls = listOf(
                        OpenAICompatibleChatRequestDto.ToolCall(
                            id = "call_1",
                            function = OpenAICompatibleChatRequestDto.FunctionCall(name = "get_local_time", arguments = """{"tz":"UTC"}"""),
                        ),
                    ),
                ),
                OpenAICompatibleChatRequestDto.Message(role = "tool", content = JsonPrimitive("12:00"), tool_call_id = "call_1"),
            ),
        )

        assertEquals(2, input.size)
        val call = input[0]
        assertEquals("function_call", call.str("type"))
        assertEquals("call_1", call.str("call_id"))
        assertEquals("get_local_time", call.str("name"))
        assertEquals("""{"tz":"UTC"}""", call.str("arguments"))
        // No `id` field: replaying the provider's item id without its reasoning item is a 400.
        assertNull(call["id"])

        val output = input[1]
        assertEquals("function_call_output", output.str("type"))
        assertEquals("call_1", output.str("call_id"))
        assertEquals("12:00", output.str("output"))
    }

    @Test
    fun `assistant text alongside tool calls is emitted before the calls`() {
        val input = toResponsesInput(
            listOf(
                OpenAICompatibleChatRequestDto.Message(
                    role = "assistant",
                    content = JsonPrimitive("checking the clock"),
                    tool_calls = listOf(
                        OpenAICompatibleChatRequestDto.ToolCall(
                            id = "call_1",
                            function = OpenAICompatibleChatRequestDto.FunctionCall(name = "get_local_time", arguments = "{}"),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(2, input.size)
        assertEquals("assistant", input[0].str("role"))
        assertEquals("checking the clock", input[0].str("content"))
        assertEquals("function_call", input[1].str("type"))
    }

    @Test
    fun `user content parts are retagged for the responses api`() {
        val chatParts = JsonArray(
            listOf(
                buildJsonObject {
                    put("type", "text")
                    put("text", "what is this")
                },
                buildJsonObject {
                    put("type", "image_url")
                    put(
                        "image_url",
                        buildJsonObject { put("url", "data:image/png;base64,AAA") },
                    )
                },
            ),
        )

        val input = toResponsesInput(listOf(OpenAICompatibleChatRequestDto.Message(role = "user", content = chatParts)))

        assertEquals(1, input.size)
        val parts = input[0]["content"] as JsonArray
        assertEquals("input_text", (parts[0] as JsonObject).str("type"))
        assertEquals("what is this", (parts[0] as JsonObject).str("text"))
        val image = parts[1] as JsonObject
        assertEquals("input_image", image.str("type"))
        // Flat string, not the nested {url} object chat completions uses.
        assertEquals("data:image/png;base64,AAA", image["image_url"]?.jsonPrimitive?.content)
    }

    @Test
    fun `missing content serializes as empty text rather than null`() {
        val input = toResponsesInput(listOf(OpenAICompatibleChatRequestDto.Message(role = "assistant", content = null)))

        assertEquals("", input[0].str("content"))
    }

    @Test
    fun `only openai endpoints route the gpt-5_6 family to the responses api`() {
        assertTrue(requiresResponsesApi(Service.OpenAI, "gpt-5.6-luna"))
        assertTrue(requiresResponsesApi(Service.OpenAI, "gpt-5.6"))
        assertTrue(requiresResponsesApi(Service.OpenAI, "GPT-5.6-Terra"))
        assertTrue(requiresResponsesApi(Service.OpenAI, "gpt-5.6-luna-xhigh"))

        // Older OpenAI families keep working on chat completions.
        assertFalse(requiresResponsesApi(Service.OpenAI, "gpt-5.5"))
        assertFalse(requiresResponsesApi(Service.OpenAI, "gpt-4o"))

        // Aggregators translate to the Responses API themselves and only accept chat completions.
        assertFalse(requiresResponsesApi(Service.OpenRouter, "openai/gpt-5.6-luna"))

        // The OpenAI-Compatible service only qualifies when pointed at OpenAI.
        assertTrue(requiresResponsesApi(Service.OpenAICompatible, "gpt-5.6-luna", "https://api.openai.com/v1"))
        assertFalse(requiresResponsesApi(Service.OpenAICompatible, "gpt-5.6-luna", "http://localhost:11434/v1"))
    }
}
