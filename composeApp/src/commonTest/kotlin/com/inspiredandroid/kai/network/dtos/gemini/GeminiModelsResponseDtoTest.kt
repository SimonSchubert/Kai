package com.inspiredandroid.kai.network.dtos.gemini

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GeminiModelsResponseDtoTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    @Test
    fun `full response with all fields`() {
        val responseJson = """
            {
                "models": [
                    {
                        "name": "models/gemini-2.0-flash",
                        "displayName": "Gemini 2.0 Flash",
                        "description": "Fast and versatile",
                        "supportedGenerationMethods": ["generateContent", "countTokens"]
                    }
                ]
            }
        """.trimIndent()
        val response = json.decodeFromString(GeminiModelsResponseDto.serializer(), responseJson)
        assertEquals(1, response.models.size)
        val model = response.models[0]
        assertEquals("models/gemini-2.0-flash", model.name)
        assertEquals("Gemini 2.0 Flash", model.displayName)
        assertEquals("Fast and versatile", model.description)
        assertEquals(listOf("generateContent", "countTokens"), model.supportedGenerationMethods)
    }

    @Test
    fun `minimal response with name only`() {
        val responseJson = """
            {
                "models": [{"name": "models/gemini-1.5-pro"}]
            }
        """.trimIndent()
        val response = json.decodeFromString(GeminiModelsResponseDto.serializer(), responseJson)
        assertEquals(1, response.models.size)
        val model = response.models[0]
        assertEquals("models/gemini-1.5-pro", model.name)
        assertNull(model.displayName)
        assertNull(model.description)
        assertNull(model.supportedGenerationMethods)
    }

    @Test
    fun `unknown fields are ignored`() {
        val responseJson = """
            {
                "models": [
                    {
                        "name": "models/gemini-2.0-flash",
                        "displayName": "Gemini 2.0 Flash",
                        "inputTokenLimit": 1048576,
                        "outputTokenLimit": 8192,
                        "temperature": 1.0,
                        "topP": 0.95
                    }
                ]
            }
        """.trimIndent()
        val response = json.decodeFromString(GeminiModelsResponseDto.serializer(), responseJson)
        assertEquals(1, response.models.size)
        assertEquals("models/gemini-2.0-flash", response.models[0].name)
        assertEquals("Gemini 2.0 Flash", response.models[0].displayName)
    }

    @Test
    fun `null supportedGenerationMethods`() {
        val responseJson = """
            {
                "models": [{"name": "models/gemini-test", "supportedGenerationMethods": null}]
            }
        """.trimIndent()
        val response = json.decodeFromString(GeminiModelsResponseDto.serializer(), responseJson)
        assertNull(response.models[0].supportedGenerationMethods)
    }

    @Test
    fun `empty supportedGenerationMethods`() {
        val responseJson = """
            {
                "models": [{"name": "models/gemini-test", "supportedGenerationMethods": []}]
            }
        """.trimIndent()
        val response = json.decodeFromString(GeminiModelsResponseDto.serializer(), responseJson)
        assertTrue(response.models[0].supportedGenerationMethods!!.isEmpty())
    }

    @Test
    fun `models prefix preserved in name`() {
        val responseJson = """
            {
                "models": [{"name": "models/gemini-2.5-pro-preview-05-06"}]
            }
        """.trimIndent()
        val response = json.decodeFromString(GeminiModelsResponseDto.serializer(), responseJson)
        assertEquals("models/gemini-2.5-pro-preview-05-06", response.models[0].name)
    }

    @Test
    fun `multiple generation methods`() {
        val responseJson = """
            {
                "models": [
                    {
                        "name": "models/gemini-2.0-flash",
                        "supportedGenerationMethods": ["generateContent", "countTokens", "embedContent"]
                    }
                ]
            }
        """.trimIndent()
        val response = json.decodeFromString(GeminiModelsResponseDto.serializer(), responseJson)
        assertEquals(3, response.models[0].supportedGenerationMethods!!.size)
    }

    @Test
    fun `empty models list`() {
        val responseJson = """{"models": []}"""
        val response = json.decodeFromString(GeminiModelsResponseDto.serializer(), responseJson)
        assertTrue(response.models.isEmpty())
    }

    @Test
    fun `multiple models`() {
        val responseJson = """
            {
                "models": [
                    {"name": "models/gemini-2.5-pro", "displayName": "Gemini 2.5 Pro"},
                    {"name": "models/gemini-2.0-flash", "displayName": "Gemini 2.0 Flash"},
                    {"name": "models/gemini-1.5-pro", "displayName": "Gemini 1.5 Pro"}
                ]
            }
        """.trimIndent()
        val response = json.decodeFromString(GeminiModelsResponseDto.serializer(), responseJson)
        assertEquals(3, response.models.size)
        assertEquals("models/gemini-2.5-pro", response.models[0].name)
        assertEquals("models/gemini-2.0-flash", response.models[1].name)
        assertEquals("models/gemini-1.5-pro", response.models[2].name)
    }
}
