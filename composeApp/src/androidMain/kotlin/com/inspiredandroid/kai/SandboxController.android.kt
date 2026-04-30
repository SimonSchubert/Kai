package com.inspiredandroid.kai

import android.content.Context
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.inspiredandroid.kai.sandbox.LinuxSandboxManager
import com.inspiredandroid.kai.sandbox.SessionShell
import com.inspiredandroid.kai.sandbox.SandboxState
import com.inspiredandroid.kai.sandbox.openFileWithIntent
import com.inspiredandroid.kai.sandbox.resolveSandboxAbsolute
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import org.koin.java.KoinJavaComponent.inject
import java.io.File
import java.io.IOException

actual fun createSandboxController(): SandboxController = AndroidSandboxController()

class AndroidSandboxController : SandboxController {

    private val sandboxManager: LinuxSandboxManager by inject(LinuxSandboxManager::class.java)
    private val context: Context by inject(Context::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var cachedDiskUsageMB = 0L
    private var previousState: SandboxState? = null
    private val _status = MutableStateFlow(SandboxStatus())
    override val status: StateFlow<SandboxStatus> = _status
    override val sessions: StateFlow<List<String>> = sandboxManager.sessions

    init {
        // Synchronously seed the status from the manager's current state so the
        // first observer doesn't briefly see "not installed" before the launched
        // collector below catches up. Skip the disk-usage walk in this fast path —
        // it iterates the rootfs and could block the calling thread (often main,
        // since Koin singletons are created lazily on first injection from
        // Composables). The launched collect immediately re-emits the same state
        // and fills in disk usage on Dispatchers.IO.
        val initial = sandboxManager.state.value
        _status.value = if (initial is SandboxState.Ready) {
            SandboxStatus(
                installed = true,
                ready = true,
                statusText = "Ready",
                packagesInstalled = sandboxManager.arePackagesInstalled(),
            )
        } else {
            mapState(initial)
        }
        // Leave previousState null so the launched collect's first mapState(Ready)
        // computes disk usage on IO.

        scope.launch {
            sandboxManager.state.collect { state ->
                _status.value = mapState(state)
                previousState = state
            }
        }
    }

    private fun mapState(state: SandboxState): SandboxStatus = when (state) {
        is SandboxState.NotInstalled -> SandboxStatus(
            statusText = "Not installed",
        )

        is SandboxState.Downloading -> SandboxStatus(
            working = true,
            progress = state.progress,
            statusText = "Downloading rootfs...",
        )

        is SandboxState.Extracting -> SandboxStatus(
            working = true,
            statusText = "Extracting...",
        )

        is SandboxState.Installing -> {
            val rootfsExists = java.io.File(sandboxManager.rootfsPath).isDirectory
            SandboxStatus(
                installed = rootfsExists,
                working = true,
                statusText = state.detail.ifEmpty { "Installing..." },
                diskUsageMB = cachedDiskUsageMB,
            )
        }

        is SandboxState.Ready -> {
            if (previousState !is SandboxState.Ready) {
                cachedDiskUsageMB = sandboxManager.getDiskUsageMB()
            }
            SandboxStatus(
                installed = true,
                ready = true,
                statusText = "Ready",
                diskUsageMB = cachedDiskUsageMB,
                packagesInstalled = sandboxManager.arePackagesInstalled(),
            )
        }

        is SandboxState.Error -> SandboxStatus(
            error = true,
            statusText = "Error: ${state.message}",
        )
    }

    override fun setup() {
        sandboxManager.setup()
    }

    override fun cancel() {
        sandboxManager.cancel()
    }

    override fun reset() {
        sandboxManager.reset()
    }

    override fun installPackages() {
        sandboxManager.installPackages()
    }

    override fun closeSession(sessionId: String) {
        sandboxManager.closeShell(sessionId)
    }

    override fun transcriptFor(sessionId: String): SnapshotStateList<com.inspiredandroid.kai.TerminalLine> =
        sandboxManager.transcriptFor(sessionId)

    override fun clearTranscript(sessionId: String) {
        sandboxManager.clearTranscript(sessionId)
    }

    override suspend fun executeCommand(command: String, sessionId: String): String = withContext(Dispatchers.IO) {
        val state = sandboxManager.state.value
        if (state !is SandboxState.Ready) return@withContext SANDBOX_NOT_READY

        val result = sandboxManager.shellFor(sessionId).run(command, timeoutSeconds = 30)

        val stdout = result["stdout"] as? String ?: ""
        val stderr = result["stderr"] as? String ?: ""
        val exitCode = result["exit_code"] as? Int
        val error = result["error"] as? String

        buildString {
            if (stdout.isNotEmpty()) append(stdout)
            if (stderr.isNotEmpty()) {
                if (isNotEmpty()) append("\n")
                append(stderr)
            }
            if (error != null) {
                if (isNotEmpty()) append("\n")
                append(error)
            }
            if (exitCode != null && exitCode != 0 && isEmpty()) {
                append("Exit code: $exitCode")
            }
        }
    }

    override suspend fun executeCommandStreaming(
        command: String,
        onStdout: (String) -> Unit,
        onStderr: (String) -> Unit,
        sessionId: String,
    ): CommandHandle {
        val state = sandboxManager.state.value
        if (state !is SandboxState.Ready) {
            onStderr(SANDBOX_NOT_READY)
            return NoOpCommandHandle
        }
        val shell = sandboxManager.shellFor(sessionId)
        val deferred = CompletableDeferred<Map<String, Any>>()
        val cancelled = AtomicBoolean(false)
        // No implicit timeout in the streaming path — UI cancel + process exit
        // are the real "done" signals. The persistent shell still recovers
        // from a wedged shell via reset() on the next call.
        val streamingTimeoutSeconds = 24L * 60 * 60
        scope.launch {
            runCatching {
                shell.run(
                    command = command,
                    timeoutSeconds = streamingTimeoutSeconds,
                    onStdout = onStdout,
                    onStderr = onStderr,
                )
            }.onSuccess { deferred.complete(it) }
                .onFailure { deferred.complete(mapOf("exit_code" to -1)) }
        }
        return PersistentCommandHandle(shell, deferred, cancelled)
    }

    override suspend fun listDirectory(path: String): List<SandboxFileEntry> = withContext(Dispatchers.IO) {
        val dir = resolveSandboxAbsolute(sandboxManager.rootfsPath, sandboxManager.homePath, path)
            ?: return@withContext emptyList()
        if (!dir.isDirectory) return@withContext emptyList()

        val normalized = if (path.endsWith("/")) path.dropLast(1) else path
        val isRoot = normalized.isEmpty() || normalized == "/"

        val children = dir.listFiles().orEmpty()
            .filterNot { isRoot && it.name == "root" }
            .map { it.toEntry(parent = if (isRoot) "" else normalized) }
            .toMutableList()

        if (isRoot) {
            val home = File(sandboxManager.homePath)
            if (home.isDirectory) {
                children.add(
                    SandboxFileEntry(
                        name = "root",
                        path = "/root",
                        isDirectory = true,
                        sizeBytes = 0,
                        lastModifiedMs = home.lastModified(),
                    ),
                )
            }
        }
        children.sortedWith(
            compareByDescending<SandboxFileEntry> { it.isDirectory }
                .thenBy { it.name.lowercase() },
        )
    }

    override suspend fun readTextFile(path: String, maxBytes: Int): String? = withContext(Dispatchers.IO) {
        val file = resolveSandboxAbsolute(sandboxManager.rootfsPath, sandboxManager.homePath, path)
            ?: return@withContext null
        if (!file.isFile) return@withContext null
        if (file.length() > maxBytes) return@withContext null
        val bytes = try {
            file.readBytes()
        } catch (e: IOException) {
            return@withContext null
        }
        if (bytes.any { it == 0.toByte() }) return@withContext null
        bytes.toString(Charsets.UTF_8)
    }

    override suspend fun writeTextFile(path: String, content: String): Boolean = withContext(Dispatchers.IO) {
        val file = resolveSandboxAbsolute(sandboxManager.rootfsPath, sandboxManager.homePath, path)
            ?: return@withContext false
        if (file.exists() && !file.isFile) return@withContext false
        try {
            file.parentFile?.mkdirs()
            file.writeBytes(content.toByteArray(Charsets.UTF_8))
            true
        } catch (e: IOException) {
            false
        }
    }

    override suspend fun openFile(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        val file = resolveSandboxAbsolute(sandboxManager.rootfsPath, sandboxManager.homePath, path)
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid path: $path"))
        if (!file.isFile) return@withContext Result.failure(IllegalArgumentException("Not a file: $path"))
        val result = openFileWithIntent(context, file)
        if (result.success) Result.success(Unit) else Result.failure(IllegalStateException(result.error ?: "Open failed"))
    }

    override suspend fun deleteEntry(path: String, recursive: Boolean): Boolean = withContext(Dispatchers.IO) {
        val file = resolveSandboxAbsolute(sandboxManager.rootfsPath, sandboxManager.homePath, path)
            ?: return@withContext false
        if (!file.exists()) return@withContext false
        // Refuse to delete the sandbox roots themselves.
        val canonical = file.canonicalPath
        if (canonical == File(sandboxManager.homePath).canonicalPath) return@withContext false
        if (canonical == File(sandboxManager.rootfsPath).canonicalPath) return@withContext false
        when {
            file.isDirectory && !recursive -> {
                val empty = file.list()?.isEmpty() != false
                if (empty) file.delete() else false
            }

            file.isDirectory -> file.deleteRecursively()

            else -> file.delete()
        }
    }

    override suspend fun renameEntry(path: String, newName: String): Result<String> = withContext(Dispatchers.IO) {
        if (newName.isBlank() || newName.contains('/') || newName.contains('\\') ||
            newName == "." || newName == ".."
        ) {
            return@withContext Result.failure(IllegalArgumentException("Invalid name"))
        }
        val src = resolveSandboxAbsolute(sandboxManager.rootfsPath, sandboxManager.homePath, path)
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid path"))
        if (!src.exists()) return@withContext Result.failure(IllegalArgumentException("Not found"))
        val canonical = src.canonicalPath
        if (canonical == File(sandboxManager.homePath).canonicalPath) {
            return@withContext Result.failure(IllegalArgumentException("Cannot rename sandbox root"))
        }
        if (canonical == File(sandboxManager.rootfsPath).canonicalPath) {
            return@withContext Result.failure(IllegalArgumentException("Cannot rename sandbox root"))
        }
        val parentSandbox = path.substringBeforeLast('/', "")
        val newSandboxPath = if (parentSandbox.isEmpty()) "/$newName" else "$parentSandbox/$newName"
        val dest = resolveSandboxAbsolute(sandboxManager.rootfsPath, sandboxManager.homePath, newSandboxPath)
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid destination"))
        if (dest.exists()) return@withContext Result.failure(IllegalStateException("collision"))
        if (src.renameTo(dest)) {
            Result.success(newSandboxPath)
        } else {
            Result.failure(IllegalStateException("rename failed"))
        }
    }
}

private fun File.toEntry(parent: String): SandboxFileEntry = SandboxFileEntry(
    name = name,
    path = if (parent.isEmpty()) "/$name" else "$parent/$name",
    isDirectory = isDirectory,
    sizeBytes = if (isFile) length() else 0,
    lastModifiedMs = lastModified(),
)

private const val SANDBOX_NOT_READY = "Sandbox is not ready"

private class PersistentCommandHandle(
    private val shell: SessionShell,
    private val result: CompletableDeferred<Map<String, Any>>,
    private val cancelled: AtomicBoolean,
) : CommandHandle {
    override fun cancel() {
        cancelled.set(true)
        shell.cancelForeground()
    }
    override fun isCancelled(): Boolean = cancelled.get()
    override suspend fun writeInput(line: String) {
        withContext(Dispatchers.IO) { shell.writeInput(line) }
    }
    override suspend fun awaitExit(): Int = (result.await()["exit_code"] as? Int) ?: -1
}
