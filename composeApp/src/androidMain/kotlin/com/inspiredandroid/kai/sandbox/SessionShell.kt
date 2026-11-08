package com.inspiredandroid.kai.sandbox

import androidx.compose.runtime.snapshots.SnapshotStateList
import com.inspiredandroid.kai.TerminalLine
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

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
    initialLines: List<TerminalLine> = emptyList(),
    private val onChange: ((List<TerminalLine>) -> Unit)? = null,
) {
    private val buffer = TranscriptBuffer(initialLines, MAX_TRANSCRIPT_LINES)

    val transcript: SnapshotStateList<TerminalLine> get() = buffer.transcript

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
        buffer.append(TerminalLine.Command(displayCommand))
        try {
            return inner.run(
                command = command,
                timeoutSeconds = timeoutSeconds,
                onStdout = { line ->
                    buffer.append(TerminalLine.Output(line))
                    onStdout?.invoke(line)
                },
                onStderr = { line ->
                    buffer.append(TerminalLine.Error(line))
                    onStderr?.invoke(line)
                },
            )
        } finally {
            val lines = withContext(NonCancellable) { buffer.flush() }
            onChange?.invoke(lines)
        }
    }

    fun writeInput(line: String) = inner.writeInput(line)

    fun cancelForeground() = inner.cancelForeground()

    fun reset() = inner.reset()

    /**
     * While paused, appends still add new lines but skip the bounded-trim.
     * Pausing during a drag-select prevents SelectionManager from crashing on
     * a selectable id whose Text was pruned mid-drag. Unpause runs a catch-up
     * trim so memory stays bounded once the gesture ends.
     */
    fun setPrunePaused(value: Boolean) = buffer.setPrunePaused(value)
}
