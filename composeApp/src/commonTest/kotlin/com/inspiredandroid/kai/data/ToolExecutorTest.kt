package com.inspiredandroid.kai.data

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for [ToolExecutor.formatJsonElement]. The async [ToolExecutor.executeTool] path
 * is not unit-tested here because it depends on the platform's `getAvailableTools()`,
 * which requires the Koin DI container to be initialized — that machinery is exercised
 * by higher-level integration tests instead.
 */
class ToolExecutorTest {

    private val executor = ToolExecutor()

    @Test
    fun `formatJsonElement renders JsonNull as the literal null`() {
        assertEquals("null", executor.formatJsonElement(JsonNull))
    }

    @Test
    fun `formatJsonElement quotes string primitives`() {
        assertEquals("\"hello\"", executor.formatJsonElement(JsonPrimitive("hello")))
    }

    @Test
    fun `formatJsonElement does not quote numeric primitives`() {
        assertEquals("42", executor.formatJsonElement(JsonPrimitive(42)))
        assertEquals("3.14", executor.formatJsonElement(JsonPrimitive(3.14)))
    }

    @Test
    fun `formatJsonElement does not quote boolean primitives`() {
        assertEquals("true", executor.formatJsonElement(JsonPrimitive(true)))
        assertEquals("false", executor.formatJsonElement(JsonPrimitive(false)))
    }

    @Test
    fun `formatJsonElement renders objects via toString`() {
        val obj = buildJsonObject {
            put("key", JsonPrimitive("value"))
        }
        val result = executor.formatJsonElement(obj)
        assertTrue(result.contains("key"))
        assertTrue(result.contains("value"))
    }

    @Test
    fun `formatJsonElement renders arrays via toString`() {
        val arr = buildJsonArray {
            add(JsonPrimitive("a"))
            add(JsonPrimitive("b"))
        }
        val result = executor.formatJsonElement(arr)
        assertTrue(result.contains("a"))
        assertTrue(result.contains("b"))
        assertTrue(result.startsWith("["))
    }

    @Test
    fun `formatJsonElement preserves string content with special characters`() {
        // Note: the implementation does not escape — it concatenates with quotes around content
        val input = JsonPrimitive("with spaces and ?punctuation!")
        assertEquals("\"with spaces and ?punctuation!\"", executor.formatJsonElement(input))
    }
}
