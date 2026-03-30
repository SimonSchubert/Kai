package com.inspiredandroid.kai.tools

import com.inspiredandroid.kai.network.tools.ParameterSchema
import com.inspiredandroid.kai.network.tools.Tool
import com.inspiredandroid.kai.network.tools.ToolInfo
import com.inspiredandroid.kai.network.tools.ToolSchema
import com.inspiredandroid.kai.smartTruncate
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.tool_execute_shell_command_description
import kai.composeapp.generated.resources.tool_execute_shell_command_name
import java.io.BufferedReader
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

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

private fun readBounded(reader: BufferedReader): String {
    val sb = StringBuilder()
    val buf = CharArray(8192)
    var read: Int
    while (reader.read(buf).also { read = it } != -1) {
        sb.append(buf, 0, read)
        if (sb.length >= MAX_OUTPUT_LENGTH) break
    }
    // Drain remaining to unblock the process even if we stop collecting
    if (sb.length >= MAX_OUTPUT_LENGTH) {
        while (reader.read(buf) != -1) { /* discard */ }
    }
    return sb.toString()
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

            // Drain stdout/stderr concurrently to avoid pipe buffer deadlock
            val stdoutFuture = CompletableFuture.supplyAsync {
                readBounded(process.inputStream.bufferedReader())
            }
            val stderrFuture = CompletableFuture.supplyAsync {
                readBounded(process.errorStream.bufferedReader())
            }

            val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)

            if (!completed) {
                process.destroyForcibly()
                return mapOf(
                    "success" to false,
                    "stdout" to stdoutFuture.get(1, TimeUnit.SECONDS).smartTruncate(MAX_OUTPUT_LENGTH),
                    "stderr" to stderrFuture.get(1, TimeUnit.SECONDS).smartTruncate(MAX_OUTPUT_LENGTH),
                    "exit_code" to -1,
                    "timed_out" to true,
                )
            }

            val stdout = stdoutFuture.get().smartTruncate(MAX_OUTPUT_LENGTH)
            val stderr = stderrFuture.get().smartTruncate(MAX_OUTPUT_LENGTH)
            val exitCode = process.exitValue()

            mapOf(
                "success" to (exitCode == 0),
                "stdout" to stdout,
                "stderr" to stderr,
                "exit_code" to exitCode,
                "timed_out" to false,
            )
        } catch (e: Exception) {
            mapOf(
                "success" to false,
                "error" to (e.message ?: "Failed to execute command"),
            )
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
