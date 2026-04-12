package com.inspiredandroid.kai.network.dtos.openaicompatible

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenAICompatibleModelResponseDtoTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    @Test
    fun `full response with all fields`() {
        val responseJson = """
            {
                "data": [
                    {
                        "id": "llama-3.1-70b",
                        "owned_by": "meta",
                        "isActive": true,
                        "created": 1700000000,
                        "context_window": 131072,
                        "type": "chat"
                    }
                ]
            }
        """.trimIndent()
        val response = json.decodeFromString(OpenAICompatibleModelResponseDto.serializer(), responseJson)
        assertEquals(1, response.data.size)
        val model = response.data[0]
        assertEquals("llama-3.1-70b", model.id)
        assertEquals("meta", model.owned_by)
        assertEquals(true, model.isActive)
        assertEquals(1700000000L, model.created)
        assertEquals(131072L, model.context_window)
        assertEquals("chat", model.type)
    }

    @Test
    fun `minimal response with id only`() {
        val responseJson = """
            {
                "data": [{"id": "some-model"}]
            }
        """.trimIndent()
        val response = json.decodeFromString(OpenAICompatibleModelResponseDto.serializer(), responseJson)
        assertEquals(1, response.data.size)
        val model = response.data[0]
        assertEquals("some-model", model.id)
        assertNull(model.owned_by)
        assertEquals(true, model.isActive) // default
        assertNull(model.created)
        assertNull(model.context_window)
        assertNull(model.type)
    }

    @Test
    fun `unknown extra fields are ignored`() {
        val responseJson = """
            {
                "data": [
                    {
                        "id": "model-1",
                        "extra_field": "should be ignored",
                        "another_unknown": 42
                    }
                ],
                "object": "list"
            }
        """.trimIndent()
        val response = json.decodeFromString(OpenAICompatibleModelResponseDto.serializer(), responseJson)
        assertEquals(1, response.data.size)
        assertEquals("model-1", response.data[0].id)
    }

    @Test
    fun `isActive defaults to true when absent`() {
        val responseJson = """{"data": [{"id": "m1"}]}"""
        val response = json.decodeFromString(OpenAICompatibleModelResponseDto.serializer(), responseJson)
        assertEquals(true, response.data[0].isActive)
    }

    @Test
    fun `isActive null`() {
        val responseJson = """{"data": [{"id": "m1", "isActive": null}]}"""
        val response = json.decodeFromString(OpenAICompatibleModelResponseDto.serializer(), responseJson)
        assertNull(response.data[0].isActive)
    }

    @Test
    fun `isActive false`() {
        val responseJson = """{"data": [{"id": "m1", "isActive": false}]}"""
        val response = json.decodeFromString(OpenAICompatibleModelResponseDto.serializer(), responseJson)
        assertEquals(false, response.data[0].isActive)
    }

    @Test
    fun `context_window as nullable long`() {
        val responseJson = """
            {
                "data": [
                    {"id": "m1", "context_window": 8192},
                    {"id": "m2", "context_window": null},
                    {"id": "m3"}
                ]
            }
        """.trimIndent()
        val response = json.decodeFromString(OpenAICompatibleModelResponseDto.serializer(), responseJson)
        assertEquals(8192L, response.data[0].context_window)
        assertNull(response.data[1].context_window)
        assertNull(response.data[2].context_window)
    }

    @Test
    fun `openrouter style context_length and name are parsed`() {
        val responseJson = """
            {
                "data": [
                    {
                        "id": "anthropic/claude-3.5-sonnet",
                        "name": "Anthropic: Claude 3.5 Sonnet",
                        "context_length": 200000,
                        "description": "Claude 3.5 Sonnet by Anthropic"
                    }
                ]
            }
        """.trimIndent()
        val response = json.decodeFromString(OpenAICompatibleModelResponseDto.serializer(), responseJson)
        val model = response.data[0]
        assertEquals("Anthropic: Claude 3.5 Sonnet", model.name)
        assertEquals(200_000L, model.context_length)
        assertEquals("Claude 3.5 Sonnet by Anthropic", model.description)
    }

    @Test
    fun `type present and absent`() {
        val responseJson = """
            {
                "data": [
                    {"id": "m1", "type": "chat"},
                    {"id": "m2"}
                ]
            }
        """.trimIndent()
        val response = json.decodeFromString(OpenAICompatibleModelResponseDto.serializer(), responseJson)
        assertEquals("chat", response.data[0].type)
        assertNull(response.data[1].type)
    }

    @Test
    fun `empty data list`() {
        val responseJson = """{"data": []}"""
        val response = json.decodeFromString(OpenAICompatibleModelResponseDto.serializer(), responseJson)
        assertTrue(response.data.isEmpty())
    }

    @Test
    fun `multiple models`() {
        val responseJson = """
            {
                "data": [
                    {"id": "gpt-4", "owned_by": "openai", "context_window": 8192},
                    {"id": "gpt-3.5-turbo", "owned_by": "openai", "context_window": 4096},
                    {"id": "llama-70b", "owned_by": "meta", "isActive": true, "type": "chat"}
                ]
            }
        """.trimIndent()
        val response = json.decodeFromString(OpenAICompatibleModelResponseDto.serializer(), responseJson)
        assertEquals(3, response.data.size)
        assertEquals("gpt-4", response.data[0].id)
        assertEquals("gpt-3.5-turbo", response.data[1].id)
        assertEquals("llama-70b", response.data[2].id)
    }
}
