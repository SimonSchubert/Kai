package com.inspiredandroid.kai.tools

import com.inspiredandroid.kai.sandbox.DesktopSessionShell
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class ProcessManager {

    class Session(
        val id: String,
        val command: String,
        val startTime: Long,
        @Volatile var stdout: String = "",
        @Volatile var stderr: String = "",
        @Volatile var finished: Boolean = false,
        @Volatile var exitCode: Int? = null,
        @Volatile var timedOut: Boolean = false,
    )

    private val sessions = ConcurrentHashMap<String, Session>()
    private val nextId = AtomicInteger(1)

    /**
     * [command] is expected to already have working_dir/env baked in as a shell prefix
     * (see ShellCommandTool) — this just needs a plain command string to run.
     *
     * Runs in its own dedicated sandbox session (distinct from any conversation's
     * persistent shell), so a long background job never blocks that conversation's
     * foreground commands via the per-session mutex in DesktopPersistentShell.
     *
     * Takes [shellFor]/[closeSession] as functions rather than a DesktopSandboxController
     * directly, so this class stays testable without constructing the full controller
     * (whose constructor does Koin lookups).
     */
    fun startBackground(
        shellFor: (String) -> DesktopSessionShell,
        closeSession: (String) -> Unit,
        command: String,
        timeoutSeconds: Long,
    ): Map<String, Any> {
        val sessionId = "bg-${nextId.getAndIncrement()}"
        val session = Session(id = sessionId, command = command, startTime = System.currentTimeMillis())
        sessions[sessionId] = session

        CompletableFuture.runAsync {
            runBlocking {
                val result = shellFor(sessionId).run(command = command, timeoutSeconds = timeoutSeconds)
                session.stdout = result["stdout"] as? String ?: ""
                session.stderr = result["stderr"] as? String ?: ""
                session.exitCode = result["exit_code"] as? Int ?: -1
                session.timedOut = result["timed_out"] as? Boolean ?: false
                session.finished = true
                closeSession(sessionId)
            }
        }

        return mapOf(
            "success" to true,
            "session_id" to sessionId,
            "status" to "running",
            "message" to "Process started in background. Use manage_process tool to check status.",
        )
    }

    fun list(): Map<String, Any> {
        val running = sessions.values.filter { !it.finished }.map { it.toInfo() }
        val finished = sessions.values.filter { it.finished }.map { it.toInfo() }
        return mapOf(
            "running" to running,
            "finished" to finished,
            "total" to sessions.size,
        )
    }

    fun log(sessionId: String, offset: Int, limit: Int): Map<String, Any> {
        val session = sessions[sessionId]
            ?: return mapOf("success" to false, "error" to "Unknown session: $sessionId")

        val stdoutLines = session.stdout.lines()
        val sliced = stdoutLines.drop(offset).take(limit).joinToString("\n")

        return mapOf(
            "success" to true,
            "session_id" to sessionId,
            "status" to if (session.finished) "finished" else "running",
            "exit_code" to (session.exitCode ?: -1),
            "stdout" to sliced,
            "stderr" to session.stderr.takeLast(2000),
            "total_stdout_lines" to stdoutLines.size,
            "offset" to offset,
            "timed_out" to session.timedOut,
        )
    }

    fun kill(closeSession: (String) -> Unit, sessionId: String): Map<String, Any> {
        val session = sessions[sessionId]
            ?: return mapOf("success" to false, "error" to "Unknown session: $sessionId")

        if (session.finished) {
            return mapOf("success" to true, "message" to "Process already finished", "exit_code" to (session.exitCode ?: -1))
        }

        closeSession(sessionId)
        session.finished = true
        session.exitCode = -1
        session.timedOut = true
        return mapOf("success" to true, "message" to "Process killed")
    }

    fun remove(closeSession: (String) -> Unit, sessionId: String): Map<String, Any> {
        val session = sessions.remove(sessionId)
            ?: return mapOf("success" to false, "error" to "Unknown session: $sessionId")

        if (!session.finished) closeSession(sessionId)
        return mapOf("success" to true, "message" to "Session removed")
    }

    private fun Session.toInfo(): Map<String, Any> = mapOf(
        "session_id" to id,
        "command" to command,
        "status" to if (finished) "finished" else "running",
        "exit_code" to (exitCode ?: -1),
        "duration_seconds" to ((System.currentTimeMillis() - startTime) / 1000),
        "timed_out" to timedOut,
        "stdout_length" to stdout.length,
    )
}
