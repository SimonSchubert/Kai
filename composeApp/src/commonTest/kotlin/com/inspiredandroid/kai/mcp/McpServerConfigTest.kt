package com.inspiredandroid.kai.mcp

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class McpServerConfigTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `round trip preserves all fields`() {
        val original = McpServerConfig(
            id = "test_server",
            name = "Test Server",
            url = "https://example.com/mcp",
            headers = mapOf("Authorization" to "Bearer xyz", "X-Custom" to "value"),
            isEnabled = false,
        )

        val encoded = json.encodeToString(McpServerConfig.serializer(), original)
        val decoded = json.decodeFromString(McpServerConfig.serializer(), encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun `isEnabled defaults to true when absent in JSON`() {
        val jsonString = """
            {
                "id": "abc",
                "name": "Server",
                "url": "https://example.com"
            }
        """.trimIndent()

        val decoded = json.decodeFromString(McpServerConfig.serializer(), jsonString)
        assertEquals(true, decoded.isEnabled)
    }

    @Test
    fun `headers default to empty map when absent`() {
        val jsonString = """
            {
                "id": "abc",
                "name": "Server",
                "url": "https://example.com"
            }
        """.trimIndent()

        val decoded = json.decodeFromString(McpServerConfig.serializer(), jsonString)
        assertTrue(decoded.headers.isEmpty())
    }

    @Test
    fun `decodes headers map correctly`() {
        val jsonString = """
            {
                "id": "abc",
                "name": "Server",
                "url": "https://example.com",
                "headers": {
                    "Authorization": "Bearer token123",
                    "X-API-Version": "2"
                },
                "isEnabled": true
            }
        """.trimIndent()

        val decoded = json.decodeFromString(McpServerConfig.serializer(), jsonString)
        assertEquals(2, decoded.headers.size)
        assertEquals("Bearer token123", decoded.headers["Authorization"])
        assertEquals("2", decoded.headers["X-API-Version"])
    }

    @Test
    fun `unknown fields are ignored when ignoreUnknownKeys is true`() {
        val jsonString = """
            {
                "id": "abc",
                "name": "Server",
                "url": "https://example.com",
                "futureField": "ignored",
                "another": 42
            }
        """.trimIndent()

        val decoded = json.decodeFromString(McpServerConfig.serializer(), jsonString)
        assertEquals("abc", decoded.id)
        assertEquals("Server", decoded.name)
    }

    @Test
    fun `decoding fails when required field is missing`() {
        // Missing 'url' (required)
        val jsonString = """
            {
                "id": "abc",
                "name": "Server"
            }
        """.trimIndent()

        assertFailsWith<SerializationException> {
            json.decodeFromString(McpServerConfig.serializer(), jsonString)
        }
    }

    @Test
    fun `encoded JSON contains all fields`() {
        val config = McpServerConfig(
            id = "id1",
            name = "Name 1",
            url = "https://x.example",
            headers = mapOf("h" to "v"),
            isEnabled = true,
        )

        val encoded = json.encodeToString(McpServerConfig.serializer(), config)
        assertTrue(encoded.contains("\"id\""))
        assertTrue(encoded.contains("\"name\""))
        assertTrue(encoded.contains("\"url\""))
        assertTrue(encoded.contains("\"headers\""))
        assertTrue(encoded.contains("\"isEnabled\""))
    }

    @Test
    fun `equality is structural`() {
        val a = McpServerConfig(id = "x", name = "X", url = "u")
        val b = McpServerConfig(id = "x", name = "X", url = "u")
        val c = a.copy(isEnabled = false)

        assertEquals(a, b)
        assertTrue(a != c)
    }
}
