package com.inspiredandroid.kai.sandbox

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.inspiredandroid.kai.TerminalLine

private const val MAX_TRANSCRIPT_LINES = 500

/**
 * Per-session facade over [PersistentSandboxShell]. Same surface as the inner
 * shell, plus a [transcript] that captures the command and its output as the
 * command runs — populated regardless of *which* path called the shell (chat
 * tool, terminal UI, package manager UI). The Terminal tab observes this list
 * directly so the agent's commands are visible alongside the user's.
 */
class SessionShell(
    val sessionId: String,
    private val inner: PersistentSandboxShell,
) {
    val transcript: SnapshotStateList<TerminalLine> = mutableStateListOf()

    /**
     * Run a single command in the persistent shell.
     *
     * @param displayCommand what to show in the transcript. Defaults to [command];
     *   callers that wrap a user command (e.g. with `cd /workdir && env=foo`)
     *   should pass the original unwrapped form so the user sees what they
     *   actually asked for, not the plumbing.
     */
    suspend fun run(
        command: String,
        timeoutSeconds: Long,
        displayCommand: String = command,
        onStdout: ((String) -> Unit)? = null,
        onStderr: ((String) -> Unit)? = null,
    ): Map<String, Any> {
        appendBounded(TerminalLine.Command(displayCommand))
        return inner.run(
            command = command,
            timeoutSeconds = timeoutSeconds,
            onStdout = { line ->
                appendBounded(TerminalLine.Output(line))
                onStdout?.invoke(line)
            },
            onStderr = { line ->
                appendBounded(TerminalLine.Error(line))
                onStderr?.invoke(line)
            },
        )
    }

    fun writeInput(line: String) = inner.writeInput(line)

    fun cancelForeground() = inner.cancelForeground()

    fun reset() = inner.reset()

    private fun appendBounded(line: TerminalLine) {
        transcript.add(line)
        val excess = transcript.size - MAX_TRANSCRIPT_LINES
        if (excess > 0) transcript.subList(0, excess).clear()
    }
}
