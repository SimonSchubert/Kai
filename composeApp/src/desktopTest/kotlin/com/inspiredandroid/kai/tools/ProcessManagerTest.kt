package com.inspiredandroid.kai.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProcessManagerTest {

    @Test
    fun startBackgroundReturnsSessionId() {
        val pm = ProcessManager()
        val result = pm.startBackground("echo hello", 10, null, emptyMap())
        assertEquals(true, result["success"])
        assertTrue((result["session_id"] as String).startsWith("bg-"))
        assertEquals("running", result["status"])
    }

    @Test
    fun listShowsRunningAndFinished() {
        val pm = ProcessManager()
        pm.startBackground("echo fast", 10, null, emptyMap())
        Thread.sleep(500) // let it finish
        val list = pm.list()
        assertEquals(1, list["total"])
    }

    @Test
    fun logReturnsOutput() {
        val pm = ProcessManager()
        val result = pm.startBackground("echo hello_world", 10, null, emptyMap())
        val sessionId = result["session_id"] as String
        Thread.sleep(500) // let it finish
        val log = pm.log(sessionId, 0, 200)
        assertEquals(true, log["success"])
        assertTrue((log["stdout"] as String).contains("hello_world"))
        assertEquals("finished", log["status"])
    }

    @Test
    fun logWithOffsetAndLimit() {
        val pm = ProcessManager()
        val result = pm.startBackground("seq 1 20", 10, null, emptyMap())
        val sessionId = result["session_id"] as String
        Thread.sleep(500)
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
        val result = pm.startBackground("sleep 60", 120, null, emptyMap())
        val sessionId = result["session_id"] as String
        Thread.sleep(200)
        val killResult = pm.kill(sessionId)
        assertEquals(true, killResult["success"])
    }

    @Test
    fun killAlreadyFinishedProcess() {
        val pm = ProcessManager()
        val result = pm.startBackground("echo done", 10, null, emptyMap())
        val sessionId = result["session_id"] as String
        Thread.sleep(500)
        val killResult = pm.kill(sessionId)
        assertEquals(true, killResult["success"])
        assertTrue((killResult["message"] as String).contains("already finished"))
    }

    @Test
    fun removeSession() {
        val pm = ProcessManager()
        val result = pm.startBackground("echo bye", 10, null, emptyMap())
        val sessionId = result["session_id"] as String
        Thread.sleep(500)
        val removeResult = pm.remove(sessionId)
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
        val kill = pm.kill("nonexistent")
        assertEquals(false, kill["success"])
        val remove = pm.remove("nonexistent")
        assertEquals(false, remove["success"])
    }

    @Test
    fun envVariablesArePassedThrough() {
        val pm = ProcessManager()
        val result = pm.startBackground("echo \$MY_TEST_VAR", 10, null, mapOf("MY_TEST_VAR" to "test_value_123"))
        val sessionId = result["session_id"] as String
        Thread.sleep(500)
        val log = pm.log(sessionId, 0, 200)
        assertTrue((log["stdout"] as String).contains("test_value_123"))
    }

    @Test
    fun backgroundProcessTimesOut() {
        val pm = ProcessManager()
        val result = pm.startBackground("sleep 60", 1, null, emptyMap())
        val sessionId = result["session_id"] as String
        Thread.sleep(2000) // wait for 1s timeout + buffer
        val log = pm.log(sessionId, 0, 200)
        assertEquals(true, log["timed_out"])
        assertEquals("finished", log["status"])
    }
}
