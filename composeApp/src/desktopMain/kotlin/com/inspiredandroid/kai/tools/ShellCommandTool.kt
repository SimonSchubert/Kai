package com.inspiredandroid.kai.tools

import com.inspiredandroid.kai.network.tools.ParameterSchema
import com.inspiredandroid.kai.network.tools.Tool
import com.inspiredandroid.kai.network.tools.ToolInfo
import com.inspiredandroid.kai.network.tools.ToolSchema
import com.inspiredandroid.kai.smartTruncate
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.tool_execute_shell_command_description
import kai.composeapp.generated.resources.tool_execute_shell_command_name
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.io.File
import java.io.InputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val MAX_OUTPUT_LENGTH = 30_000
private const val DEFAULT_TIMEOUT_SECONDS = 30L
private const val MAX_TIMEOUT_SECONDS = 120L

private val blockedPatterns = listOf(
    Regex("""rm\s+-[^\s]*r[^\s]*\s+/(?:\s|$)"""), // rm -rf /
    Regex("""rm\s+-[^\s]*r[^\s]*\s+/\*"""), // rm -rf /*
    Regex("""rm\s+-[^\s]*r[^\s]*\s+~(?:\s|$)"""), // rm -rf ~
    Regex("""rm\s+-[^\s]*r[^\s]*\s+~/\*"""), // rm -rf ~/*
    Regex("""mkfs\."""), // mkfs.ext4, mkfs.ntfs, etc.
    Regex("""dd\s+.*if=/dev/(zero|urandom|random)"""), // dd overwrite disk
    Regex(""">\s*/dev/[sh]d[a-z]"""), // > /dev/sda
    Regex(""":\(\)\s*\{.*\|.*&\s*\}\s*;?\s*:"""), // fork bomb
    Regex("""chmod\s+-[^\s]*R[^\s]*\s+[0-7]+\s+/(?:\s|$)"""), // chmod -R 777 /
    Regex("""\bshutdown\b"""), // shutdown
    Regex("""\breboot\b"""), // reboot
    Regex("""\bhalt\b"""), // halt
    Regex("""\bpoweroff\b"""), // poweroff
    Regex("""\binit\s+[06]\b"""), // init 0 / init 6
    Regex("""format\s+[A-Za-z]:"""), // Windows format C:
)

private val BLOCKED_ENV_VARS = setOf(
    "PATH",
    "LD_PRELOAD",
    "LD_LIBRARY_PATH",
    "DYLD_INSERT_LIBRARIES",
    "DYLD_LIBRARY_PATH",
    "DYLD_FRAMEWORK_PATH",
)

private fun isBlocked(command: String): Boolean = blockedPatterns.any { it.containsMatchIn(command) }

// Grace for the readers to hit EOF after the shell exits. A child left running in the background
// (`server &`) inherits the pipes and holds them open, so EOF may never come.
private const val STREAM_DRAIN_GRACE_SECONDS = 2L

// Dedicated daemon threads: a reader can stay blocked on a pipe held open by an orphaned
// background child, which must not starve the shared ForkJoin pool or keep the JVM alive.
private val streamReaders = Executors.newCachedThreadPool { runnable ->
    Thread(runnable, "kai-shell-reader").apply { isDaemon = true }
}

/**
 * Drains [stream] on a reader thread, keeping the first [MAX_OUTPUT_LENGTH] chars and discarding
 * the rest so the process never blocks on a full pipe. [text] returns what arrived so far, so a
 * stream that never reaches EOF still yields its output.
 */
private class StreamCollector(stream: InputStream) {
    private val sb = StringBuilder()
    private val done: CompletableFuture<Unit> = CompletableFuture.runAsync({
        val reader = stream.bufferedReader()
        val buf = CharArray(8192)
        var read: Int
        while (reader.read(buf).also { read = it } != -1) {
            synchronized(sb) {
                if (sb.length < MAX_OUTPUT_LENGTH) sb.append(buf, 0, read)
            }
        }
    }, streamReaders).thenApply { }

    /** Waits up to [seconds] for EOF; returns false if the stream is still held open. */
    fun awaitEof(seconds: Long): Boolean = try {
        done.get(seconds, TimeUnit.SECONDS)
        true
    } catch (_: TimeoutException) {
        false
    } catch (_: Exception) {
        true // read failed — whatever was collected is all there is
    }

    fun text(): String = synchronized(sb) { sb.toString() }.smartTruncate(MAX_OUTPUT_LENGTH)
}

/** Kills [process] and everything it spawned that is still attached to it. */
private fun destroyTree(process: Process) {
    process.descendants().forEach { it.destroyForcibly() }
    process.destroyForcibly()
}

private fun buildDescription(): String {
    val osName = System.getProperty("os.name", "").lowercase()
    val platform = when {
        "mac" in osName || "darwin" in osName -> "macOS"
        "win" in osName -> "Windows"
        else -> "Linux"
    }
    val shell = if ("win" in osName) "cmd.exe" else "sh"
    return """Execute a shell command on the host machine ($platform, shell: $shell) and return stdout, stderr, and exit code.
Each command runs in a fresh shell — use "cd dir && command" for directory changes.
Output is limited to ${MAX_OUTPUT_LENGTH} characters per stream; for large output, pipe through head/tail.
Default timeout: ${DEFAULT_TIMEOUT_SECONDS}s, max: ${MAX_TIMEOUT_SECONDS}s.
Use for file operations, system info, running scripts, installing packages, etc.
Set background=true to run long-lived processes (servers, builds). Use the manage_process tool to check on them."""
}

object ShellCommandTool : Tool {
    // The executor's default 30s would cut off the advertised 120s max; the per-call timeout is
    // enforced below, so this only needs to sit above it with room to collect output.
    override val timeout: Duration = (MAX_TIMEOUT_SECONDS + 10).seconds

    override val schema = ToolSchema(
        name = "execute_shell_command",
        description = buildDescription(),
        parameters = mapOf(
            "command" to ParameterSchema("string", "The shell command to execute", true),
            "timeout" to ParameterSchema("integer", "Timeout in seconds (default $DEFAULT_TIMEOUT_SECONDS, max $MAX_TIMEOUT_SECONDS)", false),
            "working_dir" to ParameterSchema("string", "Working directory for the command", false),
            "env" to ParameterSchema("object", "Environment variables to set (key-value pairs). Cannot override PATH or LD_PRELOAD.", false),
            "background" to ParameterSchema("boolean", "Run in background and return immediately with a session_id. Use manage_process tool to check status.", false),
        ),
    )

    @Suppress("UNCHECKED_CAST")
    override suspend fun execute(args: Map<String, Any>): Any {
        val command = args["command"] as? String
            ?: return mapOf("success" to false, "error" to "Command is required")

        if (isBlocked(command)) {
            return mapOf("success" to false, "error" to "Command is blocked for safety reasons")
        }

        val timeoutSeconds = ((args["timeout"] as? Number)?.toLong() ?: DEFAULT_TIMEOUT_SECONDS)
            .coerceIn(1, MAX_TIMEOUT_SECONDS)

        val workingDir = (args["working_dir"] as? String)?.let { File(it) }

        val envMap = (args["env"] as? Map<String, Any>)
            ?.mapValues { it.value.toString() }
            ?.filterKeys { it.uppercase() !in BLOCKED_ENV_VARS }
            ?: emptyMap()

        val background = args["background"] as? Boolean ?: false
        if (background) {
            return ProcessManagerTool.processManager.startBackground(command, timeoutSeconds, workingDir, envMap)
        }

        return try {
            runInterruptible(Dispatchers.IO) { runForeground(command, timeoutSeconds, workingDir, envMap) }
        } catch (e: Exception) {
            // Cancellation (stop, executor timeout) must propagate, not become a command result.
            if (e is kotlinx.coroutines.CancellationException) throw e
            mapOf(
                "success" to false,
                "error" to (e.message ?: "Failed to execute command"),
            )
        }
    }

    /**
     * Blocking foreground run. Called through [runInterruptible], so cancelling the tool (stop
     * button, executor timeout) interrupts [Process.waitFor] and the finally block kills the tree.
     */
    private fun runForeground(command: String, timeoutSeconds: Long, workingDir: File?, envMap: Map<String, String>): Map<String, Any> {
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
        try {
            // Drain stdout/stderr concurrently to avoid pipe buffer deadlock
            val stdout = StreamCollector(process.inputStream)
            val stderr = StreamCollector(process.errorStream)

            val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!completed) destroyTree(process)

            val drained = stdout.awaitEof(STREAM_DRAIN_GRACE_SECONDS) and
                stderr.awaitEof(STREAM_DRAIN_GRACE_SECONDS)
            val stderrText = if (completed && !drained) {
                stderr.text() + "\n[Output may be incomplete: a background process is still holding the shell's output open. " +
                    "Use background=true for long-running processes.]"
            } else {
                stderr.text()
            }

            if (!completed) {
                return mapOf(
                    "success" to false,
                    "stdout" to stdout.text(),
                    "stderr" to stderrText,
                    "exit_code" to -1,
                    "timed_out" to true,
                )
            }

            val exitCode = process.exitValue()
            return mapOf(
                "success" to (exitCode == 0),
                "stdout" to stdout.text(),
                "stderr" to stderrText,
                "exit_code" to exitCode,
                "timed_out" to false,
            )
        } finally {
            // Interrupted (tool cancelled) or failed mid-run: don't leave the command running.
            if (process.isAlive) destroyTree(process)
        }
    }

    val toolInfo = ToolInfo(
        id = "execute_shell_command",
        name = "Execute Shell Command",
        description = "Execute a shell command on the device",
        nameRes = Res.string.tool_execute_shell_command_name,
        descriptionRes = Res.string.tool_execute_shell_command_description,
        isEnabled = false,
    )
}
