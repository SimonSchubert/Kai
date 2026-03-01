package com.inspiredandroid.kai.tools

import com.inspiredandroid.kai.network.tools.ParameterSchema
import com.inspiredandroid.kai.network.tools.Tool
import com.inspiredandroid.kai.network.tools.ToolInfo
import com.inspiredandroid.kai.network.tools.ToolSchema
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.tool_execute_shell_command_description
import kai.composeapp.generated.resources.tool_execute_shell_command_name
import java.io.File
import java.util.concurrent.TimeUnit

private const val MAX_OUTPUT_LENGTH = 10_000
private const val DEFAULT_TIMEOUT_SECONDS = 30L
private const val MAX_TIMEOUT_SECONDS = 120L

private val blockedPatterns = listOf(
    Regex("""rm\s+-[^\s]*r[^\s]*\s+/\s*$"""), // rm -rf /
    Regex("""rm\s+-[^\s]*r[^\s]*\s+/\*"""), // rm -rf /*
    Regex("""rm\s+-[^\s]*r[^\s]*\s+~\s*$"""), // rm -rf ~
    Regex("""rm\s+-[^\s]*r[^\s]*\s+~/\*"""), // rm -rf ~/*
    Regex("""mkfs\."""), // mkfs.ext4, mkfs.ntfs, etc.
    Regex("""dd\s+.*if=/dev/(zero|urandom|random)"""), // dd overwrite disk
    Regex(""">\s*/dev/[sh]d[a-z]"""), // > /dev/sda
    Regex(""":\(\)\s*\{.*\|.*&\s*\}\s*;?\s*:"""), // fork bomb
    Regex("""chmod\s+-[^\s]*R[^\s]*\s+[0-7]+\s+/\s*$"""), // chmod -R 777 /
    Regex("""shutdown"""), // shutdown
    Regex("""reboot"""), // reboot
    Regex("""halt\b"""), // halt
    Regex("""poweroff"""), // poweroff
    Regex("""init\s+[06]"""), // init 0 / init 6
    Regex("""format\s+[A-Za-z]:"""), // Windows format C:
)

private fun isBlocked(command: String): Boolean = blockedPatterns.any { it.containsMatchIn(command) }

object ShellCommandTool : Tool {
    override val schema = ToolSchema(
        name = "execute_shell_command",
        description = "Execute a shell command on the host device and return stdout, stderr, and exit code. Use for file operations, system info, running scripts, etc.",
        parameters = mapOf(
            "command" to ParameterSchema("string", "The shell command to execute", true),
            "timeout" to ParameterSchema("integer", "Timeout in seconds (default 30, max 120)", false),
            "working_dir" to ParameterSchema("string", "Working directory for the command", false),
        ),
    )

    override suspend fun execute(args: Map<String, Any>): Any {
        val command = args["command"] as? String
            ?: return mapOf("success" to false, "error" to "Command is required")

        if (isBlocked(command)) {
            return mapOf("success" to false, "error" to "Command is blocked for safety reasons")
        }

        val timeoutSeconds = ((args["timeout"] as? Number)?.toLong() ?: DEFAULT_TIMEOUT_SECONDS)
            .coerceIn(1, MAX_TIMEOUT_SECONDS)

        val workingDir = (args["working_dir"] as? String)?.let { File(it) }

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

            val process = processBuilder.start()
            val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)

            if (!completed) {
                process.destroyForcibly()
                return mapOf(
                    "success" to false,
                    "stdout" to process.inputStream.bufferedReader().readText().take(MAX_OUTPUT_LENGTH),
                    "stderr" to process.errorStream.bufferedReader().readText().take(MAX_OUTPUT_LENGTH),
                    "exit_code" to -1,
                    "timed_out" to true,
                )
            }

            val stdout = process.inputStream.bufferedReader().readText().take(MAX_OUTPUT_LENGTH)
            val stderr = process.errorStream.bufferedReader().readText().take(MAX_OUTPUT_LENGTH)
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
