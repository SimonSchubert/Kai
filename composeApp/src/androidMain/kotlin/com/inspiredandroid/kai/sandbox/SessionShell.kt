package com.inspiredandroid.kai.sandbox

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.Snapshot
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
    initialLines: List<TerminalLine> = emptyList(),
    private val onChange: ((List<TerminalLine>) -> Unit)? = null,
) {
    val transcript: SnapshotStateList<TerminalLine> = mutableStateListOf<TerminalLine>().apply {
        addAll(initialLines)
    }

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
        try {
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
        } finally {
            onChange?.invoke(transcript.toList())
        }
    }

    fun writeInput(line: String) = inner.writeInput(line)

    fun cancelForeground() = inner.cancelForeground()

    fun reset() = inner.reset()

    @Volatile
    private var prunePaused: Boolean = false

    /**
     * While paused, [appendBounded] still adds new lines but skips the
     * bounded-trim. Pausing during a drag-select prevents
     * SelectionManager from crashing on a selectable id whose Text was
     * pruned mid-drag. Unpause runs a catch-up trim so memory stays
     * bounded once the gesture ends.
     */
    fun setPrunePaused(value: Boolean) {
        val wasPaused = prunePaused
        prunePaused = value
        if (wasPaused && !value) {
            Snapshot.withMutableSnapshot {
                val excess = transcript.size - MAX_TRANSCRIPT_LINES
                if (excess > 0) transcript.subList(0, excess).clear()
            }
        }
    }

    private fun appendBounded(line: TerminalLine) {
        // Add+trim must commit as a single snapshot. Otherwise a LazyColumn
        // measure pass can capture size=N+1 then read index N after the trim
        // shrunk the list — IndexOutOfBoundsException under heavy bursts.
        Snapshot.withMutableSnapshot {
            transcript.add(line)
            if (!prunePaused) {
                val excess = transcript.size - MAX_TRANSCRIPT_LINES
                if (excess > 0) transcript.subList(0, excess).clear()
            }
        }
    }
}
