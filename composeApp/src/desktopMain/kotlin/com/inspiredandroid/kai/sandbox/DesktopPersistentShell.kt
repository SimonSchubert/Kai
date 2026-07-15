package com.inspiredandroid.kai.sandbox

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val MAX_OUTPUT_LENGTH = 15_000
private const val RS = ""
private const val US = ""

/**
 * A persistent, non-chroot bash shell with micromamba's installed environment
 * on PATH. Structurally the same sentinel-based command-boundary protocol as
 * Android's PersistentSandboxShell, without any chroot/proot layer — desktop
 * has no separate fake root, so this is just an ordinary shell with env vars set.
 *
 * Known limitation: [cancelForeground] kills the shell process itself but,
 * unlike Android's PersistentSandboxShell, does not signal the foreground
 * command's children — a long-running child (rsync, python, curl) reparents
 * to init and keeps running after cancel. Android solves this with a pid
 * probe + pgrep/kill escalation; desktop doesn't implement that yet. This
 * matters more here, not less, since this shell runs fully unsandboxed
 * (no chroot boundary) on non-Flatpak desktop builds.
 */
class DesktopPersistentShell(private val layout: MicromambaLayout) {
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var process: Process? = null
    private var watchdog: Job? = null
    private val currentSink = AtomicReference<CommandSink?>(null)

    /**
     * The sentinel arrives on stderr, but stdout is drained by a separate
     * thread/pipe with no ordering guarantee relative to stderr — completing
     * as soon as the stderr sentinel is seen let a command's trailing stdout
     * lines lose the race and never make it into [stdoutBuf] (confirmed by a
     * flaky test: a fast 20-line `seq` command intermittently returned only
     * 1-2 lines). [run] now emits a second, stdout-side end marker after the
     * command; ordering *within* the stdout pipe is guaranteed, so seeing
     * that marker means every real stdout line before it has already been
     * dispatched. Completion waits for both markers, whichever arrives last.
     */
    private class CommandSink(
        val nonce: String,
        val stdoutBuf: StringBuilder = StringBuilder(),
        val stderrBuf: StringBuilder = StringBuilder(),
        val onStdout: ((String) -> Unit)? = null,
        val onStderr: ((String) -> Unit)? = null,
        val done: CompletableDeferred<Result> = CompletableDeferred(),
    ) {
        private val completionLock = Any()

        @Volatile private var stdoutMarkerSeen = false

        @Volatile private var pendingResult: Result? = null

        fun stdoutMarkerArrived() {
            synchronized(completionLock) {
                stdoutMarkerSeen = true
                pendingResult?.let { done.complete(it) }
            }
        }

        fun stderrResultArrived(result: Result) {
            synchronized(completionLock) {
                pendingResult = result
                if (stdoutMarkerSeen) done.complete(result)
            }
        }
    }

    private data class Result(val exitCode: Int, val cwd: String, val shellDied: Boolean = false)

    suspend fun run(
        command: String,
        timeoutSeconds: Long,
        onStdout: ((String) -> Unit)? = null,
        onStderr: ((String) -> Unit)? = null,
    ): Map<String, Any> = mutex.withLock {
        ensureShell()
        val nonce = (0 until 16).map { "0123456789abcdef".random() }.joinToString("")
        val sink = CommandSink(nonce, onStdout = onStdout, onStderr = onStderr)
        currentSink.set(sink)

        val line = "$command; __kai_st=\$?; " +
            "printf '\\n\\036%s\\036\\n' '$nonce'; " +
            "printf '\\n\\036%s\\037%d\\037%s\\036\\n' '$nonce' \"\$__kai_st\" \"\$PWD\" >&2"
        writeLine(line)

        val result = withTimeoutOrNull(timeoutSeconds.seconds) { sink.done.await() }
        currentSink.set(null)
        if (result == null) {
            reset()
            return@withLock mapOf(
                "success" to false,
                "stdout" to sink.stdoutBuf.toString(),
                "stderr" to sink.stderrBuf.toString(),
                "exit_code" to -1,
                "timed_out" to true,
                "cwd" to "",
                "shell_died" to true,
            )
        }
        mapOf(
            "success" to (!result.shellDied && result.exitCode == 0),
            "stdout" to sink.stdoutBuf.toString(),
            "stderr" to sink.stderrBuf.toString(),
            "exit_code" to result.exitCode,
            "timed_out" to false,
            "cwd" to result.cwd,
            "shell_died" to result.shellDied,
        )
    }

    fun cancelForeground() {
        reset()
    }

    fun reset() {
        watchdog?.cancel()
        watchdog = null
        process?.destroyForcibly()
        process = null
        currentSink.getAndSet(null)?.done?.complete(Result(exitCode = -1, cwd = "", shellDied = true))
    }

    private fun writeLine(line: String) {
        val p = process ?: return
        p.outputStream.write((line + "\n").toByteArray())
        p.outputStream.flush()
    }

    private fun ensureShell() {
        if (process != null) return
        val env = HashMap(System.getenv())
        val existingPath = env["PATH"].orEmpty()
        env["PATH"] = "${File(layout.rootPrefix, "bin").absolutePath}:$existingPath"
        env["MAMBA_ROOT_PREFIX"] = layout.rootPrefix.absolutePath

        val pb = ProcessBuilder("bash", "--noprofile", "--norc")
        pb.environment().putAll(env)
        pb.redirectErrorStream(false)
        val p = pb.start()
        process = p

        val stdoutThread = Thread { streamLines(p.inputStream.bufferedReader(), isStderr = false) }
        val stderrThread = Thread { streamLines(p.errorStream.bufferedReader(), isStderr = true) }
        stdoutThread.isDaemon = true
        stderrThread.isDaemon = true
        stdoutThread.start()
        stderrThread.start()

        watchdog = scope.launch {
            while (p.isAlive) delay(200.milliseconds)
            currentSink.getAndSet(null)?.done?.complete(Result(exitCode = -1, cwd = "", shellDied = true))
            process = null
        }
    }

    private fun streamLines(reader: BufferedReader, isStderr: Boolean) {
        try {
            while (true) {
                val line = reader.readLine() ?: break
                if (isStderr) dispatchStderr(line) else dispatchStdout(line)
            }
        } catch (_: Exception) {
            // stream closed under us (process destroyed)
        }
    }

    private fun dispatchStdout(line: String) {
        // Suppress blank stdout lines for the same reason as stderr below —
        // the stdout-side end marker's leading \n flushes any partial line
        // ahead of it, producing a stray empty line when there's nothing to
        // flush.
        if (line.isEmpty()) return
        val sink = currentSink.get() ?: return
        if (line.length >= 2 && line.startsWith(RS) && line.endsWith(RS)) {
            val payload = line.substring(1, line.length - 1)
            if (payload == sink.nonce) {
                sink.stdoutMarkerArrived()
                return
            }
        }
        appendBounded(sink.stdoutBuf, line)
        sink.onStdout?.invoke(line)
    }

    private fun dispatchStderr(line: String) {
        // Suppress blank stderr lines. Sentinel emission prepends \n to flush
        // any partial line ahead of it, which produces a stray empty line when
        // there's nothing to flush.
        if (line.isEmpty()) return
        val sink = currentSink.get() ?: return
        if (line.length >= 2 && line.startsWith(RS) && line.endsWith(RS)) {
            val payload = line.substring(1, line.length - 1)
            val parts = payload.split(US)
            if (parts.size == 3 && parts[0] == sink.nonce) {
                val exit = parts[1].toIntOrNull() ?: -1
                val cwd = parts[2]
                sink.stderrResultArrived(Result(exitCode = exit, cwd = cwd))
                return
            }
        }
        appendBounded(sink.stderrBuf, line)
        sink.onStderr?.invoke(line)
    }

    private fun appendBounded(buf: StringBuilder, line: String) {
        if (buf.length >= MAX_OUTPUT_LENGTH) return
        if (buf.isNotEmpty()) buf.append('\n')
        buf.append(line)
    }
}
