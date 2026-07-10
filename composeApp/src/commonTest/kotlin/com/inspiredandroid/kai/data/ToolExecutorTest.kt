package com.inspiredandroid.kai.data

import com.inspiredandroid.kai.network.tools.Tool
import com.inspiredandroid.kai.network.tools.ToolSchema
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * Tests for [ToolExecutor.formatJsonElement] and the [ToolExecutor.executeTool] error and
 * cancellation contract. Tool lookup is injected via the `toolsProvider` constructor
 * parameter, so no Koin container is needed.
 */
class ToolExecutorTest {

    private val executor = ToolExecutor()

    private class FakeTool(
        name: String = "fake_tool",
        override val timeout: Duration = 30.minutes,
        private val block: suspend () -> Any,
    ) : Tool {
        override val schema = ToolSchema(name = name, description = "test tool", parameters = emptyMap())
        override suspend fun execute(args: Map<String, Any>): Any = block()
    }

    private fun executorWith(tool: Tool) = ToolExecutor(toolsProvider = { listOf(tool) })

    @Test
    fun `executeTool propagates CancellationException instead of returning an error result`() = runTest {
        val executor = executorWith(FakeTool { throw CancellationException("stop") })
        assertFailsWith<CancellationException> {
            executor.executeTool("fake_tool", "{}")
        }
    }

    @Test
    fun `executeTool is cooperatively cancellable while a tool is running`() = runTest {
        var completed = false
        val executor = executorWith(FakeTool { awaitCancellation() })
        val job = launch {
            executor.executeTool("fake_tool", "{}")
            completed = true
        }
        runCurrent()
        job.cancel()
        job.join()
        assertTrue(job.isCancelled)
        assertTrue(!completed)
    }

    @Test
    fun `executeTool reports a timeout as an error result`() = runTest {
        val executor = executorWith(
            FakeTool(timeout = 100.milliseconds) { delay(10.minutes) },
        )
        val result = executor.executeTool("fake_tool", "{}")
        assertTrue(result.contains("timed out"))
    }

    @Test
    fun `executeTool reports a generic exception as an error result`() = runTest {
        val executor = executorWith(FakeTool { throw IllegalStateException("boom") })
        val result = executor.executeTool("fake_tool", "{}")
        assertTrue(result.contains("Tool execution failed"))
    }

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
