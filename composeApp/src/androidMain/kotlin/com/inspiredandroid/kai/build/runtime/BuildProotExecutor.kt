package com.inspiredandroid.kai.build.runtime

import java.io.File
import java.io.IOException
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class ProotResult(
    val success: Boolean,
    val stdout: String = "",
    val stderr: String = "",
    val error: String? = null,
) {
    /**
     * Best available explanation for a failure, trimmed for display.
     * Prefers the tail of the stream — apt/dpkg put the real error last
     * (early lines are often only the apt-utils debconf warning).
     */
    fun failureDetail(maxChars: Int = 500): String {
        val raw = stderr.ifBlank { stdout }.ifBlank { error.orEmpty() }.trim()
        if (raw.length <= maxChars) return raw
        return "…" + raw.takeLast(maxChars)
    }
}

class BuildProotHandle internal constructor(
    private val process: Process,
    private val cancelled: AtomicBoolean,
    private val readers: List<CompletableFuture<Void>>,
    /** Host-side file the guest bridge wrote its own pid into, if it runs under a PTY. */
    private val guestPidFile: File? = null,
) {
    /**
     * Writes go through one background thread: the pipe write blocks, and
     * interactive input arrives on the UI thread a keystroke at a time. A single
     * thread also keeps the bytes in the order they were typed.
     */
    private val writer = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "kai-build-pty-write").apply { isDaemon = true }
    }

    fun cancel() {
        cancelled.set(true)
        // Destroying the proot process alone does not end a session: proot survives
        // Process.destroyForcibly() here, and its guest children (the PTY bridge and
        // the shell under it) would outlive it as orphans anyway. Killing the bridge
        // closes the PTY master, which hangs up the shell and lets proot exit.
        killGuest()
        runCatching { writer.shutdownNow() }
        runCatching { process.outputStream.close() }
        runCatching { process.inputStream.close() }
        runCatching { process.errorStream.close() }
        process.destroyForcibly()
    }

    /** proot does not namespace pids, so the pid the guest wrote is a host pid. */
    private fun killGuest() {
        val file = guestPidFile ?: return
        val pid = runCatching { file.readText().trim().toIntOrNull() }.getOrNull()
        if (pid != null && pid > 0) runCatching { android.os.Process.killProcess(pid) }
        runCatching { file.delete() }
    }

    /** Raw bytes to the process (PTY master side via python bridge). */
    fun writeBytes(data: ByteArray) {
        if (cancelled.get() || data.isEmpty()) return
        runCatching {
            writer.execute {
                if (cancelled.get()) return@execute
                runCatching {
                    process.outputStream.write(data)
                    process.outputStream.flush()
                }
            }
        }
    }

    fun writeText(text: String) {
        writeBytes(text.toByteArray(Charsets.UTF_8))
    }

    fun awaitExit(): Int {
        while (!cancelled.get() && process.isAlive) {
            runCatching { process.waitFor(200, TimeUnit.MILLISECONDS) }
        }
        if (cancelled.get()) return -1
        readers.forEach { runCatching { it.get(500, TimeUnit.MILLISECONDS) } }
        return runCatching { process.exitValue() }.getOrDefault(-1)
    }
}

/**
 * Low-level proot launcher for the Debian Kai Build rootfs.
 *
 * Only [projectsPath] is bind-mounted (to `/root/projects`). `/root` itself
 * stays on the rootfs so agent binaries under the vendor install dirs
 * (`~/.local/bin`, `~/.grok/bin`, `~/.opencode/bin`) are executable — Android
 * external storage is often mounted noexec.
 */
class BuildProotExecutor(
    private val prootPath: String,
    private val libDir: String,
    private val rootfsPath: String,
    private val projectsPath: String,
    private val tmpPath: String,
    private val columns: Int = 80,
    private val rows: Int = 24,
    /** Guest path the host writes "rows cols" to — one file per live session. */
    private val winsizePath: String = "/tmp/kai-winsize",
    /** Guest path browser-open URLs are captured in — one file per live session. */
    private val openUrlPath: String = "/tmp/kai-open-url",
    /** File name (inside the bind-mounted tmp dir) the PTY bridge records its pid in. */
    private val pidFileName: String = "kai-pid",
) {

    fun execute(
        command: String,
        timeoutSeconds: Long = 120,
        workingDir: String = "/root",
    ): ProotResult = try {
        val process = start(command, workingDir, allocatePty = false)
        val stdout = CompletableFuture.supplyAsync { process.inputStream.bufferedReader().use { it.readText() } }
        val stderr = CompletableFuture.supplyAsync { process.errorStream.bufferedReader().use { it.readText() } }
        if (process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            ProotResult(
                success = process.exitValue() == 0,
                stdout = stdout.get(),
                stderr = stderr.get(),
            )
        } else {
            process.destroyForcibly()
            ProotResult(success = false, error = "Timed out after ${timeoutSeconds}s")
        }
    } catch (e: Exception) {
        ProotResult(success = false, error = e.message ?: "proot failed")
    }

    /**
     * Interactive session: PTY inside the guest, raw byte stream to [onOutput].
     * stderr is merged onto the PTY by python's pty.spawn; any leftover host
     * stderr is still forwarded.
     */
    fun executeStreaming(
        command: String,
        workingDir: String,
        onOutput: (ByteArray, Int) -> Unit,
    ): BuildProotHandle {
        val pidFile = File(tmpPath, pidFileName)
        runCatching { pidFile.delete() }
        val process = start(command, workingDir, allocatePty = true)
        val cancelled = AtomicBoolean(false)
        val readers = listOf(
            CompletableFuture.runAsync { streamBytes(process.inputStream, cancelled, onOutput) },
            CompletableFuture.runAsync { streamBytes(process.errorStream, cancelled, onOutput) },
        )
        return BuildProotHandle(process, cancelled, readers, guestPidFile = pidFile)
    }

    private fun start(command: String, workingDir: String, allocatePty: Boolean): Process {
        val shellCommand = if (allocatePty) wrapWithPty(command) else command
        return Runtime.getRuntime().exec(
            arrayOf(
                prootPath,
                "--link2symlink",
                "-L",
                "--rootfs=$rootfsPath",
                "--bind=/dev",
                "--bind=/proc",
                "--bind=/sys",
                "--bind=$projectsPath:/root/projects",
                "--bind=$tmpPath:/tmp",
                "-0",
                "-w", workingDir,
                "/bin/sh", "-c", shellCommand,
            ),
            environment(),
            File(rootfsPath).parentFile,
        )
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

    private fun environment(): Array<String> = arrayOf(
        "HOME=/root",
        "USER=root",
        // Vendor installers: Claude → ~/.local/bin, Grok → ~/.grok/bin, OpenCode → ~/.opencode/bin.
        // Keep every agent dir on PATH; shell rc files are not sourced for non-login probes.
        "PATH=/root/.local/bin:/root/.grok/bin:/root/.opencode/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
        "TERM=xterm-256color",
        "COLORTERM=truecolor",
        "LANG=C.UTF-8",
        "COLUMNS=$columns",
        "LINES=$rows",
        "DEBIAN_FRONTEND=noninteractive",
        // Grok opens the login URL via webbrowser; under proot there is no real browser.
        // GROK_TEST_OPEN_URL_FILE is an official Grok hook that writes the URL to a file.
        "GROK_TEST_OPEN_URL_FILE=$openUrlPath",
        // Same target for the kai-browser wrapper, so both hooks feed one session's link bar.
        "KAI_OPEN_URL_FILE=$openUrlPath",
        // webbrowser crate falls back to $BROWSER when xdg-open is missing.
        "BROWSER=/usr/local/bin/kai-browser",
        "LD_LIBRARY_PATH=$libDir",
        "PROOT_TMP_DIR=$tmpPath",
        "PROOT_LOADER=${File(prootPath).parent.orEmpty()}/libproot-loader.so",
    )

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
