package com.inspiredandroid.kai.network.dtos.anthropic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class AnthropicDtoTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    @Test
    fun `request serialization produces valid Anthropic format`() {
        val request = AnthropicChatRequestDto(
            model = "claude-sonnet-4-20250514",
            messages = listOf(
                AnthropicChatRequestDto.Message(role = "user", content = JsonPrimitive("Hi")),
            ),
            max_tokens = 1,
        )
        val serialized = json.encodeToString(AnthropicChatRequestDto.serializer(), request)
        val parsed = json.parseToJsonElement(serialized)
        val obj = parsed as kotlinx.serialization.json.JsonObject
        assertEquals("\"claude-sonnet-4-20250514\"", obj["model"].toString())
        assertEquals("1", obj["max_tokens"].toString())
        // system and tools should not be present (explicitNulls = false)
        assertEquals(null, obj["system"])
        assertEquals(null, obj["tools"])
    }

    @Test
    fun `response deserialization handles text content`() {
        val responseJson = """
            {
                "id": "msg_123",
                "type": "message",
                "role": "assistant",
                "content": [
                    {"type": "text", "text": "Hello!"}
                ],
                "model": "claude-sonnet-4-20250514",
                "stop_reason": "end_turn",
                "stop_sequence": null,
                "usage": {"input_tokens": 10, "output_tokens": 5}
            }
        """.trimIndent()
        val response = json.decodeFromString(AnthropicChatResponseDto.serializer(), responseJson)
        assertEquals(1, response.content.size)
        assertEquals("text", response.content[0].type)
        assertEquals("Hello!", response.content[0].text)
        assertEquals("end_turn", response.stop_reason)
        assertEquals("Hello!", response.extractText())
    }

    @Test
    fun `response deserialization handles tool_use content`() {
        val responseJson = """
            {
                "id": "msg_456",
                "type": "message",
                "role": "assistant",
                "content": [
                    {"type": "text", "text": "Let me check that."},
                    {
                        "type": "tool_use",
                        "id": "toolu_abc",
                        "name": "get_weather",
                        "input": {"city": "London"}
                    }
                ],
                "model": "claude-sonnet-4-20250514",
                "stop_reason": "tool_use",
                "usage": {"input_tokens": 20, "output_tokens": 15}
            }
        """.trimIndent()
        val response = json.decodeFromString(AnthropicChatResponseDto.serializer(), responseJson)
        assertEquals(2, response.content.size)
        assertEquals("tool_use", response.content[1].type)
        assertEquals("toolu_abc", response.content[1].id)
        assertEquals("get_weather", response.content[1].name)
        assertEquals("tool_use", response.stop_reason)
        assertEquals("Let me check that.", response.extractText())
    }
}
