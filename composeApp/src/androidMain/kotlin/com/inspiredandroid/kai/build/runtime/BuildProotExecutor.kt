package com.inspiredandroid.kai.build.runtime

import com.inspiredandroid.kai.linux.ProotHandle
import com.inspiredandroid.kai.linux.ProotLauncher
import com.inspiredandroid.kai.linux.ProotResult
import java.io.File
import java.io.IOException
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

/** Install logs are worth keeping in full; the shell tool's 15k cap is not enough. */
private const val MAX_OUTPUT_CHARS = 200_000

/**
 * Kai Build's view of a rootfs: interactive sessions under a real PTY, raw
 * bytes rather than lines.
 *
 * The proot invocation itself — argv, binds, environment — comes from the
 * shared [ProotLauncher], the same one the chat sandbox uses.
 */
class BuildProotExecutor(
    private val launcher: ProotLauncher,
    private val tmpPath: String,
    private val columns: Int = 80,
    private val rows: Int = 24,
    /** Guest path the host writes "rows cols" to — one file per live session. */
    private val winsizePath: String = "/tmp/kai-winsize",
    /** File name (inside the bind-mounted tmp dir) the PTY bridge records its pid in. */
    private val pidFileName: String = "kai-pid",
) {

    fun execute(
        command: String,
        timeoutSeconds: Long = 120,
        workingDir: String = "/root",
    ): ProotResult = launcher.execute(
        command = command,
        timeoutSeconds = timeoutSeconds,
        workingDir = workingDir,
        maxOutputChars = MAX_OUTPUT_CHARS,
    )

    /**
     * Interactive session: PTY inside the guest, raw byte stream to [onOutput].
     * stderr is merged onto the PTY by python's pty.spawn; any leftover host
     * stderr is still forwarded.
     */
    fun executeStreaming(
        command: String,
        workingDir: String,
        onOutput: (ByteArray, Int) -> Unit,
    ): ProotHandle {
        val pidFile = File(tmpPath, pidFileName)
        runCatching { pidFile.delete() }
        return launcher.startStreaming(
            command = wrapWithPty(command),
            workingDir = workingDir,
            guestPidFile = pidFile,
        ) { process, cancelled ->
            listOf(
                CompletableFuture.runAsync { streamBytes(process.inputStream, cancelled, onOutput) },
                CompletableFuture.runAsync { streamBytes(process.errorStream, cancelled, onOutput) },
            )
        }
    }

    /**
     * Runs [command] under a real PTY via python3 (always installed with base packages).
     *
     * Important: we do **not** use bare `pty.spawn` alone — TUI apps (Grok, etc.) query
     * size via `TIOCGWINSZ`. A fresh PTY is often 0×0, so they paint nothing. We
     * `pty.fork()`, set winsize, SIGWINCH, then bridge master ↔ stdio.
     */
    private fun wrapWithPty(command: String): String {
        val cmdB64 = Base64.getEncoder().encodeToString(command.toByteArray(Charsets.UTF_8))
        val script = """
            |import base64, errno, fcntl, os, pty, select, signal, struct, sys, termios
            |ROWS, COLS = $rows, $columns
            |cmd = base64.b64decode('$cmdB64').decode()
            |
            |def set_winsize(fd, r, c):
            |    # struct winsize { row, col, xpixel, ypixel }
            |    fcntl.ioctl(fd, termios.TIOCSWINSZ, struct.pack('HHHH', r, c, 0, 0))
            |
            |pid, master = pty.fork()
            |if pid == 0:
            |    os.environ['TERM'] = 'xterm-256color'
            |    os.environ['COLORTERM'] = 'truecolor'
            |    os.environ['COLUMNS'] = str(COLS)
            |    os.environ['LINES'] = str(ROWS)
            |    # Prefer a clean dynamic-linker search inside the rootfs.
            |    os.environ.pop('LD_LIBRARY_PATH', None)
            |    os.execvp('/bin/bash', ['bash', '-lc', cmd])
            |    os._exit(127)
            |
            |try:
            |    set_winsize(master, ROWS, COLS)
            |except OSError:
            |    pass
            |try:
            |    os.kill(pid, signal.SIGWINCH)
            |except OSError:
            |    pass
            |
            |# Host writes "rows cols" here (bind-mounted /tmp) when the UI resizes.
            |WS_PATH = '$winsizePath'
            |ws_cur = [ROWS, COLS]  # mutable so nested poll can update
            |
            |def poll_winsize():
            |    try:
            |        with open(WS_PATH, 'r') as f:
            |            parts = f.read().split()
            |        if len(parts) < 2:
            |            return
            |        nr, nc = int(parts[0]), int(parts[1])
            |        if nr < 1 or nc < 1 or (nr, nc) == (ws_cur[0], ws_cur[1]):
            |            return
            |        set_winsize(master, nr, nc)
            |        try:
            |            os.kill(pid, signal.SIGWINCH)
            |        except OSError:
            |            pass
            |        ws_cur[0], ws_cur[1] = nr, nc
            |    except (OSError, ValueError):
            |        pass
            |
            |stdin_open = True
            |try:
            |    while True:
            |        rfds = [master]
            |        if stdin_open:
            |            rfds.append(0)
            |        try:
            |            # Short timeout so we notice UI resize without extra IPC.
            |            r, _, _ = select.select(rfds, [], [], 0.05)
            |        except (InterruptedError, select.error):
            |            poll_winsize()
            |            continue
            |        poll_winsize()
            |        if master in r:
            |            try:
            |                data = os.read(master, 8192)
            |            except OSError as e:
            |                if e.errno == errno.EIO:
            |                    break
            |                raise
            |            if not data:
            |                break
            |            os.write(1, data)
            |        if stdin_open and 0 in r:
            |            data = os.read(0, 8192)
            |            if not data:
            |                stdin_open = False
            |            else:
            |                os.write(master, data)
            |except OSError:
            |    pass
            |finally:
            |    try:
            |        os.close(master)
            |    except OSError:
            |        pass
            |
            |_, status = os.waitpid(pid, 0)
            |if hasattr(os, 'waitstatus_to_exitcode'):
            |    raise SystemExit(os.waitstatus_to_exitcode(status))
            |if os.WIFEXITED(status):
            |    raise SystemExit(os.WEXITSTATUS(status))
            |raise SystemExit(1)
            """.trimMargin()
        val scriptB64 = Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_8))
        // `$$` is this shell's pid and `exec` keeps it for python, so the file holds
        // the bridge's real pid — that is what cancelling a session has to kill.
        val dollar = "${'$'}${'$'}"
        return """echo $dollar > /tmp/$pidFileName; exec python3 -c "import base64;exec(base64.b64decode('$scriptB64'))""""
    }

    private fun streamBytes(
        stream: java.io.InputStream,
        cancelled: AtomicBoolean,
        onOutput: (ByteArray, Int) -> Unit,
    ) {
        val buf = ByteArray(4096)
        try {
            while (!cancelled.get()) {
                val n = try {
                    stream.read(buf)
                } catch (e: IOException) {
                    if (cancelled.get()) break
                    throw e
                }
                if (n < 0) break
                if (n > 0) onOutput(buf, n)
            }
        } finally {
            runCatching { stream.close() }
        }
    }
}
