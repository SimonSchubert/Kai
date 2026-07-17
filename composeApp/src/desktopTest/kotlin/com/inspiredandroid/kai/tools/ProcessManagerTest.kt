package com.inspiredandroid.kai.tools

import com.inspiredandroid.kai.sandbox.DesktopPersistentShell
import com.inspiredandroid.kai.sandbox.DesktopSessionShell
import com.inspiredandroid.kai.sandbox.MicromambaLayout
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProcessManagerTest {

    private lateinit var baseDir: File
    private val shells = mutableMapOf<String, DesktopSessionShell>()
    private val closedSessions = mutableListOf<String>()

    private fun shellFor(sessionId: String): DesktopSessionShell = shells.getOrPut(sessionId) {
        DesktopSessionShell(sessionId, DesktopPersistentShell(MicromambaLayout(baseDir)))
    }

    private fun closeSession(sessionId: String) {
        closedSessions.add(sessionId)
        shells.remove(sessionId)?.reset()
    }

    // Background jobs spawn a real bash process (shell startup + a sentinel
    // round-trip); a fixed sleep here was flaky under load — poll for the
    // session actually finishing instead, same lesson already learned for
    // Tasks 5/6's tests (see progress ledger).
    private fun waitUntilFinished(pm: ProcessManager, sessionId: String, timeoutMs: Long = 5000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val log = pm.log(sessionId, 0, 1)
            if (log["status"] == "finished") return
            Thread.sleep(20)
        }
        error("Session $sessionId did not finish within ${timeoutMs}ms")
    }

    @BeforeTest
    fun setUp() {
        baseDir = Files.createTempDirectory("process-manager-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        shells.values.forEach { it.reset() }
        baseDir.deleteRecursively()
    }

    @Test
    fun startBackgroundReturnsSessionId() {
        val pm = ProcessManager()
        val result = pm.startBackground(::shellFor, ::closeSession, "echo hello", 10)
        assertEquals(true, result["success"])
        assertTrue((result["session_id"] as String).startsWith("bg-"))
        assertEquals("running", result["status"])
    }

    @Test
    fun listShowsRunningAndFinished() {
        val pm = ProcessManager()
        val result = pm.startBackground(::shellFor, ::closeSession, "echo fast", 10)
        waitUntilFinished(pm, result["session_id"] as String)
        val list = pm.list()
        assertEquals(1, list["total"])
    }

    @Test
    fun logReturnsOutput() {
        val pm = ProcessManager()
        val result = pm.startBackground(::shellFor, ::closeSession, "echo hello_world", 10)
        val sessionId = result["session_id"] as String
        waitUntilFinished(pm, sessionId)
        val log = pm.log(sessionId, 0, 200)
        assertEquals(true, log["success"])
        assertTrue((log["stdout"] as String).contains("hello_world"))
        assertEquals("finished", log["status"])
    }

    @Test
    fun logWithOffsetAndLimit() {
        val pm = ProcessManager()
        val result = pm.startBackground(::shellFor, ::closeSession, "seq 1 20", 10)
        val sessionId = result["session_id"] as String
        waitUntilFinished(pm, sessionId)
        val log = pm.log(sessionId, 5, 3)
        assertEquals(true, log["success"])
        assertEquals(5, log["offset"])
        val lines = (log["stdout"] as String).trim().lines()
        assertEquals(3, lines.size)
        assertEquals("6", lines[0]) // seq 1 20, offset 5 means line index 5 = "6"
    }

    @Test
    fun killTerminatesRunningProcess() {
        val pm = ProcessManager()
        val result = pm.startBackground(::shellFor, ::closeSession, "sleep 60", 120)
        val sessionId = result["session_id"] as String
        // Not waiting for finish here — this deliberately kills mid-flight.
        // Just give the bash process a moment to actually start executing.
        Thread.sleep(200)
        val killResult = pm.kill(::closeSession, sessionId)
        assertEquals(true, killResult["success"])
        assertTrue(sessionId in closedSessions)
    }

    @Test
    fun killAlreadyFinishedProcess() {
        val pm = ProcessManager()
        val result = pm.startBackground(::shellFor, ::closeSession, "echo done", 10)
        val sessionId = result["session_id"] as String
        waitUntilFinished(pm, sessionId)
        val killResult = pm.kill(::closeSession, sessionId)
        assertEquals(true, killResult["success"])
        assertTrue((killResult["message"] as String).contains("already finished"))
    }

    @Test
    fun removeSession() {
        val pm = ProcessManager()
        val result = pm.startBackground(::shellFor, ::closeSession, "echo bye", 10)
        val sessionId = result["session_id"] as String
        waitUntilFinished(pm, sessionId)
        val removeResult = pm.remove(::closeSession, sessionId)
        assertEquals(true, removeResult["success"])
        // After removal, list should be empty
        val list = pm.list()
        assertEquals(0, list["total"])
    }

    @Test
    fun unknownSessionReturnsError() {
        val pm = ProcessManager()
        val log = pm.log("nonexistent", 0, 200)
        assertEquals(false, log["success"])
        val kill = pm.kill(::closeSession, "nonexistent")
        assertEquals(false, kill["success"])
        val remove = pm.remove(::closeSession, "nonexistent")
        assertEquals(false, remove["success"])
    }

    @Test
    fun envVariablesArePassedThrough() {
        // ShellCommandTool bakes env into the command string before calling
        // startBackground (see shellSingleQuote/prefix); ProcessManager itself
        // just needs to run whatever command string it's given.
        val pm = ProcessManager()
        val result = pm.startBackground(::shellFor, ::closeSession, "MY_TEST_VAR=test_value_123 sh -c 'echo \$MY_TEST_VAR'", 10)
        val sessionId = result["session_id"] as String
        waitUntilFinished(pm, sessionId)
        val log = pm.log(sessionId, 0, 200)
        assertTrue((log["stdout"] as String).contains("test_value_123"))
    }

    @Test
    fun backgroundProcessTimesOut() {
        val pm = ProcessManager()
        val result = pm.startBackground(::shellFor, ::closeSession, "sleep 60", 1)
        val sessionId = result["session_id"] as String
        waitUntilFinished(pm, sessionId, timeoutMs = 8000)
        val log = pm.log(sessionId, 0, 200)
        assertEquals(true, log["timed_out"])
        assertEquals("finished", log["status"])
    }
}
