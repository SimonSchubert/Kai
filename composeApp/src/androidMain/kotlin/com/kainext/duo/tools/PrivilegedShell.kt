package com.kainext.duo.tools

import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

private const val TAG = "PrivilegedShell"

/** Which backend a privileged command should use. */
enum class PrivilegedMode {
    AUTO,
    ROOT,
    SHIZUKU,

    /**
     * Accessibility-service mode. It cannot run shell commands — it reads the node tree and
     * dispatches gestures instead — so [PrivilegedShell] falls back to root/Shizuku/app-shell
     * for anything that needs a command line. The mode exists so the agent knows which input
     * backend to prefer for taps.
     */
    ACCESSIBILITY,
    ;

    companion object {
        fun fromId(id: String): PrivilegedMode = when (id.lowercase()) {
            "root" -> ROOT
            "shizuku" -> SHIZUKU
            "accessibility" -> ACCESSIBILITY
            else -> AUTO
        }
    }
}

/**
 * Result of one privileged command. [backend] names the path that actually ran it
 * (`root`, `shizuku`, or `app` for the unprivileged fallback), so the agent and the
 * Settings status row can tell the user what really happened.
 */
data class PrivilegedResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val backend: String,
) {
    val success: Boolean get() = exitCode == 0

    /** Output for the agent: stdout on success, stderr + exit code on failure. */
    fun toToolOutput(): String = if (success) {
        stdout.ifBlank { "(no output)" }
    } else {
        buildString {
            append("exit code ").append(exitCode).append(" via ").append(backend)
            if (stderr.isNotBlank()) append("\n").append(stderr.trim())
            if (stdout.isNotBlank()) append("\n").append(stdout.trim())
        }
    }
}

/**
 * Runs shell commands as root (`su`) or through Shizuku (adb/shell uid 2000).
 *
 * AUTO prefers root, then Shizuku, then an unprivileged `sh` child of the app process.
 * The unprivileged fallback is still useful for app-private paths but cannot read
 * `/sdcard` under scoped storage, which is why the Settings screen surfaces the backend.
 */
class PrivilegedShell(
    private val modeProvider: () -> PrivilegedMode = { PrivilegedMode.AUTO },
) {

    /** True when `su` can be spawned and reports uid 0. Cached after the first positive probe. */
    @Volatile
    private var cachedRootAvailable: Boolean? = null

    fun isRootAvailable(forceRefresh: Boolean = false): Boolean {
        if (!forceRefresh) cachedRootAvailable?.let { return it }
        val available = try {
            val process = ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start()
            val finished = process.waitFor(3, TimeUnit.SECONDS)
            val output = if (finished) process.inputStream.bufferedReader().readText() else ""
            if (!finished) process.destroyForcibly()
            finished && output.contains("uid=0")
        } catch (_: Throwable) {
            false
        }
        cachedRootAvailable = available
        return available
    }

    fun isShizukuBinderAlive(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    fun isShizukuPermissionGranted(): Boolean = try {
        isShizukuBinderAlive() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    fun requestShizukuPermission() {
        try {
            if (isShizukuBinderAlive() && !isShizukuPermissionGranted()) {
                @Suppress("DEPRECATION")
                Shizuku.requestPermission(REQUEST_CODE)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Shizuku permission request failed", e)
        }
    }

    private fun startRoot(command: String): Process? = try {
        ProcessBuilder("su", "-c", command).redirectErrorStream(false).start()
    } catch (e: Throwable) {
        Log.w(TAG, "root spawn failed", e)
        null
    }

    /**
     * Spawns a privileged process through the Shizuku service (uid 2000 in the shell
     * domain). Returns null when Shizuku is unavailable, permission is not granted, or
     * the spawn itself fails — the caller ([execute]) then falls back to root or the
     * unprivileged app shell.
     *
     * Shizuku 13 removed `Shizuku.newProcess`, so the documented replacement is
     * `IShizukuService.newProcess(cmd, env, dir)`, which returns an `IRemoteProcess`.
     */
    private fun startShizuku(command: String): Process? = try {
        if (!isShizukuPermissionGranted()) {
            null
        } else {
            val binder = Shizuku.getBinder()
            if (binder == null || !binder.pingBinder()) {
                null
            } else {
                val service = IShizukuService.Stub.asInterface(binder)
                val remote = service?.newProcess(arrayOf("sh", "-c", command), null, null)
                remote?.let(::wrapShizukuProcess)
            }
        }
    } catch (e: Throwable) {
        Log.w(TAG, "shizuku spawn failed", e)
        null
    }

    /**
     * Wraps the service's `IRemoteProcess` in `ShizukuRemoteProcess`, which is the
     * artifact's own `java.lang.Process` implementation (it also links a death recipient
     * so a dead Shizuku server cannot leave a stuck reader behind).
     *
     * Its constructor is package-private, so reflection is the only way to reach it
     * without depending on Shizuku internals at compile time.
     */
    private fun wrapShizukuProcess(remote: IRemoteProcess): Process? = try {
        val constructor = ShizukuRemoteProcess::class.java
            .getDeclaredConstructor(IRemoteProcess::class.java)
        constructor.isAccessible = true
        constructor.newInstance(remote) as Process
    } catch (e: Throwable) {
        Log.w(TAG, "shizuku process wrap failed", e)
        null
    }

    private fun startAppShell(command: String): Process? = try {
        ProcessBuilder("sh", "-c", command).redirectErrorStream(false).start()
    } catch (e: Throwable) {
        Log.w(TAG, "app shell spawn failed", e)
        null
    }

    /** Executes [command] and collects stdout/stderr, killing the process on timeout. */
    suspend fun execute(command: String, timeoutMs: Long = 30_000): PrivilegedResult {
        val mode = modeProvider()
        val (process, backend) = when (mode) {
            PrivilegedMode.ROOT -> startRoot(command)?.let { it to "root" }
                ?: return PrivilegedResult("", "Root shell unavailable. Enable root or switch to Shizuku/Auto.", -1, "root")

            PrivilegedMode.SHIZUKU -> startShizuku(command)?.let { it to "shizuku" }
                ?: return PrivilegedResult("", "Shizuku unavailable or permission not granted.", -1, "shizuku")

            // Accessibility has no shell of its own, so it resolves like AUTO for commands.
            PrivilegedMode.AUTO, PrivilegedMode.ACCESSIBILITY -> startRoot(command)?.let { it to "root" }
                ?: startShizuku(command)?.let { it to "shizuku" }
                ?: startAppShell(command)?.let { it to "app" }
                ?: return PrivilegedResult("", "No shell backend available.", -1, "none")
        }
        return collect(process, timeoutMs, backend)
    }

    private suspend fun collect(process: Process, timeoutMs: Long, backend: String): PrivilegedResult =
        withContext(Dispatchers.IO) {
            val stdout = StringBuilder()
            val stderr = StringBuilder()
            val outThread = thread(name = "priv-shell-out", isDaemon = true) {
                runCatching {
                    process.inputStream.bufferedReader().forEachLine { stdout.appendLine(it) }
                }
            }
            val errThread = thread(name = "priv-shell-err", isDaemon = true) {
                runCatching {
                    process.errorStream.bufferedReader().forEachLine { stderr.appendLine(it) }
                }
            }
            val finished = try {
                process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                false
            }
            if (!finished) {
                process.destroyForcibly()
            }
            outThread.join(1_000)
            errThread.join(1_000)
            val exit = try {
                if (finished) process.exitValue() else -1
            } catch (_: Throwable) {
                -1
            }
            PrivilegedResult(
                stdout = stdout.toString().trimEnd(),
                stderr = if (finished) stderr.toString().trimEnd() else "command timed out after ${timeoutMs}ms",
                exitCode = exit,
                backend = backend,
            )
        }

    /** Single-quotes [value] for safe interpolation into a `sh` command line. */
    fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    companion object {
        const val REQUEST_CODE = 4213
    }
}
