package com.inspiredandroid.kai.data

import com.inspiredandroid.kai.network.tools.Tool
import com.inspiredandroid.kai.network.tools.ToolSchema
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * Tests for the [ToolExecutor.executeTool] error and cancellation contract. Tool lookup is
 * injected via the `toolsProvider` constructor parameter, so no Koin container is needed.
 */
class ToolExecutorTest {

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
}
