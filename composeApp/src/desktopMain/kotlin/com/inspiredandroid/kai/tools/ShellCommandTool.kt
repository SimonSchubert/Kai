package com.inspiredandroid.kai.tools

import com.inspiredandroid.kai.DesktopSandboxController
import com.inspiredandroid.kai.SandboxController
import com.inspiredandroid.kai.SandboxSessions
import com.inspiredandroid.kai.data.currentConversationIdOrNull
import com.inspiredandroid.kai.network.tools.ParameterSchema
import com.inspiredandroid.kai.network.tools.Tool
import com.inspiredandroid.kai.network.tools.ToolInfo
import com.inspiredandroid.kai.network.tools.ToolSchema
import java.util.UUID
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.tool_execute_shell_command_description
import kai.composeapp.generated.resources.tool_execute_shell_command_name
import org.koin.java.KoinJavaComponent.inject

private const val DEFAULT_TIMEOUT_SECONDS = 30L
private const val MAX_TIMEOUT_SECONDS = 120L

// Retained from before this tool routed through the sandbox: desktop's shell has no
// chroot/namespace boundary (unlike Android's), so this blocklist is the only guard
// against a handful of unambiguously destructive commands. Not a security boundary —
// a backstop against obvious mistakes.
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

private fun isBlocked(command: String): Boolean = blockedPatterns.any { it.containsMatchIn(command) }

private val validEnvKeyRegex = Regex("^[A-Za-z_][A-Za-z0-9_]*$")

private fun shellSingleQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

/**
 * Builds a `cd <dir> && FOO=bar ...` prefix for [ShellCommandTool.execute]. The env
 * variable *name* must stay unquoted -- bash only recognizes `NAME=value` as an
 * assignment word when NAME itself is unquoted; quoting it (as an earlier version of
 * this code did) makes bash treat the whole thing as a command name instead, e.g.
 * `'FOO'='hello'` fails with "FOO=hello: command not found" rather than assigning FOO.
 * Names are validated against shell-identifier syntax so an unquoted, LLM-supplied key
 * can't inject extra shell syntax.
 */
internal fun buildCommandPrefix(workingDir: String?, env: Map<String, String>): String = buildString {
    if (workingDir != null) {
        append("cd ").append(shellSingleQuote(workingDir)).append(" && ")
    }
    env.forEach { (k, v) ->
        require(validEnvKeyRegex.matches(k)) { "Invalid env variable name: $k" }
        append(k).append('=').append(shellSingleQuote(v)).append(' ')
    }
}

private const val TOOL_DESCRIPTION = """Execute a shell command in the Dev Tools sandbox and return stdout, stderr, and exit code.

Shell session is PERSISTENT across calls within THIS conversation: cwd, exported environment variables, and any in-shell state carry from one call to the next, just like a normal terminal. So "cd /tmp" in one call, then "pwd" in the next, returns "/tmp". You do NOT need to chain "cd dir && command" unless you want directory changes to be one-shot. Other conversations and the in-app Terminal tab each have their own isolated shells.

Pre-installed: git, curl, wget, jq, python, nodejs, plus remote-server tools — ssh, scp, sftp (openssh), lftp (FTP/FTPS), rsync. Use them directly. Install extra packages via the Dev Tools Packages UI or `micromamba install -y -p <prefix> -c conda-forge <package>`.

Note: unlike Android's sandbox, this shell has no chroot or namespace boundary — commands run directly against the real machine with real filesystem access. Be conservative with destructive operations.

Limits and behavior:
- Output is capped at 15000 characters per stream; for large output, pipe through head/tail.
- Default timeout: ${DEFAULT_TIMEOUT_SECONDS}s, max: ${MAX_TIMEOUT_SECONDS}s.
- Fullscreen TUIs (top, htop, vim, less, nano, anything ncurses) WILL NOT WORK — no PTY.
- Set background=true to run a long-lived process detached from the shell (writes to its own session_id). Use manage_process to check on it.
- Set fresh=true to run in a one-shot isolated shell that doesn't share state with the persistent session."""

object ShellCommandTool : Tool {
    private val sandboxController: SandboxController by inject(SandboxController::class.java)

    override val schema = ToolSchema(
        name = "execute_shell_command",
        description = TOOL_DESCRIPTION,
        parameters = mapOf(
            "command" to ParameterSchema("string", "The shell command to execute", true),
            "timeout" to ParameterSchema("integer", "Timeout in seconds (default $DEFAULT_TIMEOUT_SECONDS, max $MAX_TIMEOUT_SECONDS)", false),
            "working_dir" to ParameterSchema(
                "string",
                "If set, run the command starting in this directory (cd <dir> && <command>). The cd persists for subsequent calls — same as if the user had run cd themselves.",
                false,
            ),
            "env" to ParameterSchema(
                "object",
                "Per-command environment variable overrides. Scoped to this call only; does not persist (use 'export' inside the command if you want persistence).",
                false,
            ),
            "background" to ParameterSchema(
                "boolean",
                "Run detached as a background job. Returns a session_id; use manage_process to check status. Does not share the persistent shell.",
                false,
            ),
            "fresh" to ParameterSchema(
                "boolean",
                "If true, run in a one-shot isolated shell that does not share state with the persistent session. Default false.",
                false,
            ),
        ),
    )

    @Suppress("UNCHECKED_CAST")
    override suspend fun execute(args: Map<String, Any>): Any {
        val command = args["command"] as? String
            ?: return mapOf("success" to false, "error" to "Command is required")

        if (isBlocked(command)) {
            return mapOf("success" to false, "error" to "Command is blocked for safety reasons")
        }

        if (!sandboxController.status.value.ready) {
            return mapOf("success" to false, "error" to "Dev Tools sandbox is not installed. Set it up in Settings > Dev Tools.")
        }
        val controller = sandboxController as? DesktopSandboxController
            ?: return mapOf("success" to false, "error" to "Sandbox controller unavailable")

        val timeoutSeconds = ((args["timeout"] as? Number)?.toLong() ?: DEFAULT_TIMEOUT_SECONDS)
            .coerceIn(1, MAX_TIMEOUT_SECONDS)
        val workingDir = args["working_dir"] as? String

        val envMap = (args["env"] as? Map<String, Any>)
            ?.mapValues { it.value.toString() }
            ?: emptyMap()

        val background = args["background"] as? Boolean ?: false

        // Apply working_dir/env as a per-command prefix (cd ... && FOO=bar cmd) so
        // they don't bleed into the session's own state. cd is intentionally
        // persistent: the LLM is told that's the case in the tool description.
        val prefix = try {
            buildCommandPrefix(workingDir, envMap)
        } catch (e: IllegalArgumentException) {
            return mapOf("success" to false, "error" to e.message)
        }
        val wrapped = if (prefix.isEmpty()) command else "$prefix$command"

        if (background) {
            return ProcessManagerTool.processManager.startBackground(
                controller::shellFor,
                controller::closeSession,
                wrapped,
                timeoutSeconds,
            )
        }

        val fresh = args["fresh"] as? Boolean ?: false
        val sessionId = if (fresh) "fresh-${UUID.randomUUID()}" else currentConversationIdOrNull() ?: SandboxSessions.DEFAULT

        return try {
            controller.shellFor(sessionId).run(
                command = wrapped,
                timeoutSeconds = timeoutSeconds,
                displayCommand = command,
            )
        } finally {
            // Fresh sessions are one-shot by contract; drop the shell (and its
            // bash process) immediately rather than leaking it for the app's
            // lifetime under a UUID nobody will ever address again.
            if (fresh) controller.closeSession(sessionId)
        }
    }

    val toolInfo = ToolInfo(
        id = "execute_shell_command",
        name = "Execute Shell Command",
        description = "Execute a shell command in the Dev Tools sandbox",
        nameRes = Res.string.tool_execute_shell_command_name,
        descriptionRes = Res.string.tool_execute_shell_command_description,
        isEnabled = false,
    )
}
