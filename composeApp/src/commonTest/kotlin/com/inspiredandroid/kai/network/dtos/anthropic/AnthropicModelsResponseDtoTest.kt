package com.inspiredandroid.kai.network.dtos.anthropic

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnthropicModelsResponseDtoTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun `full response with all fields`() {
        val responseJson = """
            {
                "data": [
                    {
                        "id": "claude-sonnet-4-20250514",
                        "display_name": "Claude Sonnet 4",
                        "created_at": "2025-05-14T00:00:00Z",
                        "type": "model"
                    }
                ]
            }
        """.trimIndent()
        val response = json.decodeFromString(AnthropicModelsResponseDto.serializer(), responseJson)
        assertEquals(1, response.data.size)
        val model = response.data[0]
        assertEquals("claude-sonnet-4-20250514", model.id)
        assertEquals("Claude Sonnet 4", model.display_name)
        assertEquals("2025-05-14T00:00:00Z", model.created_at)
        assertEquals("model", model.type)
    }

    @Test
    fun `minimal response with id only`() {
        val responseJson = """
            {
                "data": [{"id": "claude-3-haiku-20240307"}]
            }
        """.trimIndent()
        val response = json.decodeFromString(AnthropicModelsResponseDto.serializer(), responseJson)
        assertEquals(1, response.data.size)
        val model = response.data[0]
        assertEquals("claude-3-haiku-20240307", model.id)
        assertNull(model.display_name)
        assertNull(model.created_at)
        assertNull(model.type)
    }

    @Test
    fun `unknown fields are ignored`() {
        val responseJson = """
            {
                "data": [
                    {
                        "id": "claude-3-opus-20240229",
                        "display_name": "Claude 3 Opus",
                        "max_tokens": 4096,
                        "pricing": {"input": 15, "output": 75}
                    }
                ],
                "has_more": false,
                "first_id": "claude-3-opus-20240229"
            }
        """.trimIndent()
        val response = json.decodeFromString(AnthropicModelsResponseDto.serializer(), responseJson)
        assertEquals(1, response.data.size)
        assertEquals("claude-3-opus-20240229", response.data[0].id)
        assertEquals("Claude 3 Opus", response.data[0].display_name)
    }

    @Test
    fun `null display_name`() {
        val responseJson = """
            {
                "data": [{"id": "claude-3-haiku", "display_name": null}]
            }
        """.trimIndent()
        val response = json.decodeFromString(AnthropicModelsResponseDto.serializer(), responseJson)
        assertNull(response.data[0].display_name)
    }

    @Test
    fun `empty data list`() {
        val responseJson = """{"data": []}"""
        val response = json.decodeFromString(AnthropicModelsResponseDto.serializer(), responseJson)
        assertTrue(response.data.isEmpty())
    }

    @Test
    fun `multiple models`() {
        val responseJson = """
            {
                "data": [
                    {"id": "claude-sonnet-4-20250514", "display_name": "Claude Sonnet 4"},
                    {"id": "claude-3-opus-20240229", "display_name": "Claude 3 Opus"},
                    {"id": "claude-3-haiku-20240307", "display_name": "Claude 3 Haiku"}
                ]
            }
        """.trimIndent()
        val response = json.decodeFromString(AnthropicModelsResponseDto.serializer(), responseJson)
        assertEquals(3, response.data.size)
        assertEquals("claude-sonnet-4-20250514", response.data[0].id)
        assertEquals("claude-3-opus-20240229", response.data[1].id)
        assertEquals("claude-3-haiku-20240307", response.data[2].id)
    }
}
