package com.inspiredandroid.kai.tools

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

/**
 * Foreground runs must return (or cancel) promptly even when the command leaves something holding
 * its output pipes open, and a cancelled run must not leave the command behind.
 */
class ShellCommandToolTest {

    private val isWindows = System.getProperty("os.name").lowercase().contains("win")

    @Test
    fun `background child holding stdout does not hang the call`() = runBlocking {
        if (isWindows) return@runBlocking
        lateinit var result: Map<*, *>
        val elapsed = measureTime {
            result = ShellCommandTool.execute(mapOf("command" to "sleep 20 & echo hi")) as Map<*, *>
        }

        assertTrue(elapsed < 10.seconds, "call took $elapsed")
        assertEquals(0, result["exit_code"])
        assertEquals("hi", result["stdout"].toString().trim())
        assertTrue(result["stderr"].toString().contains("background=true"))
    }

    @Test
    fun `cancelling a running command kills it`() = runBlocking {
        if (isWindows) return@runBlocking
        val marker = File.createTempFile("kai-shell-cancel", ".txt").apply { delete() }
        try {
            val elapsed = measureTime {
                assertFailsWith<CancellationException> {
                    withTimeout(1.seconds) {
                        ShellCommandTool.execute(mapOf("command" to "sleep 3 && touch '${marker.absolutePath}'"))
                    }
                }
            }
            assertTrue(elapsed < 3.seconds, "cancellation took $elapsed")

            Thread.sleep(4_000)
            assertFalse(marker.exists(), "command kept running after cancellation")
        } finally {
            marker.delete()
        }
    }

    @Test
    fun `plain command returns output and exit code`() = runBlocking {
        if (isWindows) return@runBlocking
        val result = ShellCommandTool.execute(mapOf("command" to "echo out; echo err >&2; exit 3")) as Map<*, *>

        assertEquals(3, result["exit_code"])
        assertEquals("out", result["stdout"].toString().trim())
        assertEquals("err", result["stderr"].toString().trim())
        assertEquals(false, result["timed_out"])
    }
}
