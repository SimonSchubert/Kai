package com.inspiredandroid.kai.tools

import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private const val MAX_OUTPUT_LENGTH = 30_000

class ProcessManager {

    class Session(
        val id: String,
        val command: String,
        val startTime: Long,
        val process: Process,
        val stdoutBuffer: StringBuilder = StringBuilder(),
        val stderrBuffer: StringBuilder = StringBuilder(),
        @Volatile var finished: Boolean = false,
        @Volatile var exitCode: Int? = null,
        @Volatile var timedOut: Boolean = false,
    )

    private val sessions = ConcurrentHashMap<String, Session>()
    private val nextId = AtomicInteger(1)

    fun startBackground(
        command: String,
        timeoutSeconds: Long,
        workingDir: File?,
        envMap: Map<String, String>,
    ): Map<String, Any> {
        val sessionId = "bg-${nextId.getAndIncrement()}"

        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        val processBuilder = if (isWindows) {
            ProcessBuilder("cmd", "/c", command)
        } else {
            ProcessBuilder("sh", "-c", command)
        }

        processBuilder.redirectErrorStream(false)
        if (workingDir != null && workingDir.isDirectory) {
            processBuilder.directory(workingDir)
        }
        if (envMap.isNotEmpty()) {
            processBuilder.environment().putAll(envMap)
        }

        val process = processBuilder.start()
        val session = Session(
            id = sessionId,
            command = command,
            startTime = System.currentTimeMillis(),
            process = process,
        )
        sessions[sessionId] = session

        // Drain stdout/stderr in background threads with bounded buffers
        CompletableFuture.runAsync {
            process.inputStream.bufferedReader().forEachLine { line ->
                synchronized(session.stdoutBuffer) {
                    if (session.stdoutBuffer.length < MAX_OUTPUT_LENGTH) {
                        session.stdoutBuffer.appendLine(line)
                    }
                }
            }
        }
        CompletableFuture.runAsync {
            process.errorStream.bufferedReader().forEachLine { line ->
                synchronized(session.stderrBuffer) {
                    if (session.stderrBuffer.length < MAX_OUTPUT_LENGTH) {
                        session.stderrBuffer.appendLine(line)
                    }
                }
            }
        }

        // Monitor for completion/timeout
        CompletableFuture.runAsync {
            val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                session.timedOut = true
                session.exitCode = -1
            } else {
                session.exitCode = process.exitValue()
            }
            session.finished = true
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

        val stdout = synchronized(session.stdoutBuffer) { session.stdoutBuffer.toString() }
        val stderr = synchronized(session.stderrBuffer) { session.stderrBuffer.toString() }

        val stdoutLines = stdout.lines()
        val sliced = stdoutLines.drop(offset).take(limit).joinToString("\n")

        return mapOf(
            "success" to true,
            "session_id" to sessionId,
            "status" to if (session.finished) "finished" else "running",
            "exit_code" to (session.exitCode ?: -1),
            "stdout" to sliced,
            "stderr" to stderr.takeLast(2000),
            "total_stdout_lines" to stdoutLines.size,
            "offset" to offset,
            "timed_out" to session.timedOut,
        )
    }

    fun kill(sessionId: String): Map<String, Any> {
        val session = sessions[sessionId]
            ?: return mapOf("success" to false, "error" to "Unknown session: $sessionId")

        if (session.finished) {
            return mapOf("success" to true, "message" to "Process already finished", "exit_code" to (session.exitCode ?: -1))
        }

        session.process.destroyForcibly()
        session.finished = true
        session.exitCode = -1
        return mapOf("success" to true, "message" to "Process killed")
    }

    fun remove(sessionId: String): Map<String, Any> {
        val session = sessions.remove(sessionId)
            ?: return mapOf("success" to false, "error" to "Unknown session: $sessionId")

        if (!session.finished) {
            session.process.destroyForcibly()
        }
        return mapOf("success" to true, "message" to "Session removed")
    }

    private fun Session.toInfo(): Map<String, Any> = mapOf(
        "session_id" to id,
        "command" to command,
        "status" to if (finished) "finished" else "running",
        "exit_code" to (exitCode ?: -1),
        "duration_seconds" to ((System.currentTimeMillis() - startTime) / 1000),
        "timed_out" to timedOut,
        "stdout_length" to stdoutBuffer.length,
    )
}
