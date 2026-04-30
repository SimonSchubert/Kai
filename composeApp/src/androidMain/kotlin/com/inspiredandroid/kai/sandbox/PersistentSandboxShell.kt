package com.inspiredandroid.kai.sandbox

import com.inspiredandroid.kai.smartTruncate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.atomic.AtomicReference

private const val MAX_OUTPUT_LENGTH = 15_000

// Sentinel uses ASCII Record Separator (0x1e) and Unit Separator (0x1f),
// emitted to stderr so user redirects of stdout don't swallow it. Octal
// escapes for portability across bash/busybox printf.
private const val RS = ""
private const val US = ""
// Marker emitted once at shell startup so we know bash's pid before any user
// command has finished. Without this, cancel on the first command had nothing
// to signal (bashPid was null, set only from the sentinel of a completed run).
private const val PID_PROBE_PREFIX = "${RS}KAIBASHPID${US}"

class PersistentSandboxShell(
    private val executor: ProotExecutor,
    private val tmpPath: String,
) {
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var handle: ProotHandle? = null
    @Volatile private var bashPid: Int? = null
    private var watchdog: Job? = null
    private val currentSink = AtomicReference<CommandSink?>(null)

    private class CommandSink(
        val nonce: String,
        val stdoutBuf: StringBuilder = StringBuilder(),
        val stderrBuf: StringBuilder = StringBuilder(),
        val onStdout: ((String) -> Unit)? = null,
        val onStderr: ((String) -> Unit)? = null,
        val done: CompletableDeferred<Result> = CompletableDeferred(),
    )

    data class Result(
        val exitCode: Int,
        val cwd: String,
        val bashPid: Int,
        val shellDied: Boolean = false,
    )

    /**
     * Run a single command in the persistent shell. Suspends until the
     * sentinel is observed, the per-command timeout fires, or the shell dies.
     * Concurrent calls are serialized by an internal mutex.
     */
    suspend fun run(
        command: String,
        timeoutSeconds: Long,
        onStdout: ((String) -> Unit)? = null,
        onStderr: ((String) -> Unit)? = null,
    ): Map<String, Any> = mutex.withLock {
        ensureShell()
        val nonce = randomNonce()
        val sink = CommandSink(nonce = nonce, onStdout = onStdout, onStderr = onStderr)
        currentSink.set(sink)

        val cmdFile = File(tmpPath, ".kai_cmd_$nonce")
        try {
            cmdFile.writeText(command)
        } catch (e: Exception) {
            currentSink.set(null)
            return@withLock errorMap(stderr = "Failed to stage command: ${e.message}")
        }

        // Source the user command (preserves cwd/env), capture exit, emit sentinel.
        // Leading \n flushes any partial stderr line (e.g. Python's >>> prompt
        // with no trailing newline) so the sentinel arrives on a clean line.
        val line = ". /tmp/.kai_cmd_$nonce; __kai_st=\$?; rm -f /tmp/.kai_cmd_$nonce; " +
            "printf '\\n\\036%s\\037%d\\037%d\\037%s\\036\\n' '$nonce' \"\$__kai_st\" \"\$\$\" \"\$PWD\" >&2"
        handle?.writeInput(line)

        val result = withTimeoutOrNull(timeoutSeconds * 1000) { sink.done.await() }
        if (result == null) {
            // Hung command. Try a graduated cancel; if that doesn't shake it
            // loose within a short grace, reset the shell.
            cancelForeground()
            val recovered = withTimeoutOrNull(2_000) { sink.done.await() }
            currentSink.set(null)
            if (recovered == null) {
                reset()
                return@withLock timeoutMap(sink, stderr = "Command timed out and shell was reset")
            }
            return@withLock buildResult(sink, recovered, timedOut = true)
        }
        currentSink.set(null)
        if (result.shellDied) {
            return@withLock buildResult(sink, result, shellDied = true)
        }
        bashPid = result.bashPid
        return@withLock buildResult(sink, result)
    }

    /**
     * Forward a stdin line to the running command. The shell's stdin is also
     * the foreground command's stdin (no redirection), so this delivers
     * interactive input (e.g. ssh password prompts) to the running process.
     */
    fun writeInput(line: String) {
        handle?.writeInput(line)
    }

    /**
     * Best-effort interrupt of the foreground command without killing the
     * shell itself. Without a PTY we can't deliver SIGINT through line
     * discipline, so we send signals to bash's children from a sibling proot.
     * Falls back to a full shell reset if the pid isn't known yet (probe race)
     * or if even SIGKILL doesn't free the foreground.
     */
    fun cancelForeground() {
        val pid = bashPid
        if (pid == null) {
            // No pid captured yet — the user expects cancel to actually do
            // something, so nuke the shell. The next run lazily restarts it.
            reset()
            return
        }
        scope.launch {
            for (signal in listOf("INT", "TERM", "KILL")) {
                sendSignalToChildren(pid, signal)
                kotlinx.coroutines.delay(500)
                // Stop escalating as soon as the in-flight command finishes
                // (sentinel arrived) or there's no in-flight command anymore.
                val done = currentSink.get()?.done?.isCompleted
                if (done == null || done == true) return@launch
            }
            // Even SIGKILL didn't free us — the shell itself must be wedged.
            reset()
        }
    }

    /** Tear down the shell. Next [run] will lazily restart it. */
    fun reset() {
        watchdog?.cancel()
        watchdog = null
        handle?.cancel()
        handle = null
        bashPid = null
        // Fail any in-flight command.
        currentSink.getAndSet(null)?.done?.complete(
            Result(exitCode = -1, cwd = "/root", bashPid = 0, shellDied = true),
        )
    }

    private fun ensureShell() {
        if (handle != null) return
        // Non-interactive bash. We have no tty, so -i would only emit prompts
        // to stderr that we'd have to filter. --noprofile/--norc keep the env
        // clean; bash still reads commands from stdin line by line, executes
        // them in-process (so cd/export/. preserve state), and inherits its
        // stdin to any foreground child (so ssh can read passwords typed via
        // writeInput).
        val h = executor.executeStreaming(
            command = "exec bash --noprofile --norc",
            onStdout = { line -> dispatchStdout(line) },
            onStderr = { line -> dispatchStderr(line) },
        )
        handle = h
        // Capture bash's pid before any user command runs. The dispatcher
        // recognizes this marker on stderr and sets bashPid, so cancel on
        // the very first command has something to signal. Leading \n matches
        // the sentinel pattern below — flushes any partial line first.
        h.writeInput("printf '\\n\\036KAIBASHPID\\037%d\\036\\n' \"\$\$\" >&2")
        watchdog = scope.launch {
            h.awaitExit()
            // Shell died. Wake up any in-flight command with a shellDied result
            // so callers don't sit on a sentinel that will never come.
            currentSink.getAndSet(null)?.done?.complete(
                Result(exitCode = -1, cwd = "/root", bashPid = bashPid ?: 0, shellDied = true),
            )
            handle = null
            bashPid = null
        }
    }

    private fun dispatchStdout(line: String) {
        val sink = currentSink.get() ?: return
        appendBounded(sink.stdoutBuf, line)
        sink.onStdout?.invoke(line)
    }

    private fun dispatchStderr(line: String) {
        // Suppress blank stderr lines. Sentinel emission prepends \n to flush
        // any partial line ahead of it, which produces a stray empty line when
        // there's nothing to flush. Legitimate blank stderr is rare; dropping
        // it is a worthwhile tradeoff for clean output.
        if (line.isEmpty()) return
        // Startup pid probe — handled regardless of whether a sink is active.
        if (line.startsWith(PID_PROBE_PREFIX) && line.endsWith(RS)) {
            val pidText = line.substring(PID_PROBE_PREFIX.length, line.length - 1)
            pidText.toIntOrNull()?.let { bashPid = it }
            return
        }
        val sink = currentSink.get() ?: return
        // Sentinel format: \x1e<nonce>\x1f<exit>\x1f<pid>\x1f<pwd>\x1e
        if (line.length >= 2 && line.startsWith(RS) && line.endsWith(RS)) {
            val payload = line.substring(1, line.length - 1)
            val parts = payload.split(US)
            if (parts.size == 4 && parts[0] == sink.nonce) {
                val exit = parts[1].toIntOrNull() ?: -1
                val pid = parts[2].toIntOrNull() ?: 0
                val cwd = parts[3]
                sink.done.complete(Result(exitCode = exit, cwd = cwd, bashPid = pid))
                return
            }
        }
        appendBounded(sink.stderrBuf, line)
        sink.onStderr?.invoke(line)
    }

    private fun sendSignalToChildren(parentPid: Int, signal: String) {
        // pgrep/pkill come from busybox and are present in the base rootfs.
        // We use pgrep + xargs kill because some busybox builds don't have
        // pkill -P. Failure is swallowed; this is best-effort.
        runCatching {
            executor.execute(
                command = "kids=\$(pgrep -P $parentPid); [ -n \"\$kids\" ] && kill -$signal \$kids",
                timeoutSeconds = 5,
            )
        }
    }

    private fun buildResult(
        sink: CommandSink,
        result: Result,
        timedOut: Boolean = false,
        shellDied: Boolean = false,
    ): Map<String, Any> {
        val stderr = if (shellDied || result.shellDied) {
            val tail = sink.stderrBuf.toString()
            if (tail.isEmpty()) "Shell session ended" else "$tail\nShell session ended"
        } else {
            sink.stderrBuf.toString()
        }
        return mapOf(
            "success" to (!timedOut && !shellDied && !result.shellDied && result.exitCode == 0),
            "stdout" to sink.stdoutBuf.toString().smartTruncate(MAX_OUTPUT_LENGTH),
            "stderr" to stderr.smartTruncate(MAX_OUTPUT_LENGTH),
            "exit_code" to if (timedOut) -1 else result.exitCode,
            "timed_out" to timedOut,
            "cwd" to result.cwd,
            "shell_died" to (shellDied || result.shellDied),
        )
    }

    private fun timeoutMap(sink: CommandSink, stderr: String): Map<String, Any> = mapOf(
        "success" to false,
        "stdout" to sink.stdoutBuf.toString().smartTruncate(MAX_OUTPUT_LENGTH),
        "stderr" to (sink.stderrBuf.toString() + "\n" + stderr).smartTruncate(MAX_OUTPUT_LENGTH),
        "exit_code" to -1,
        "timed_out" to true,
        "cwd" to "/root",
        "shell_died" to true,
    )

    private fun errorMap(stderr: String): Map<String, Any> = mapOf(
        "success" to false,
        "stdout" to "",
        "stderr" to stderr,
        "exit_code" to -1,
        "timed_out" to false,
        "cwd" to "/root",
        "shell_died" to false,
    )
}

private fun appendBounded(buf: StringBuilder, line: String) {
    if (buf.length >= MAX_OUTPUT_LENGTH) return
    if (buf.isNotEmpty()) buf.append('\n')
    buf.append(line)
}

private fun randomNonce(): String =
    (0 until 16).map { "0123456789abcdef".random() }.joinToString("")
