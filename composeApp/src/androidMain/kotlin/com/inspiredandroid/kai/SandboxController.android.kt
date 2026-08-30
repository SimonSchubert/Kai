package com.inspiredandroid.kai

import android.content.Context
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.inspiredandroid.kai.linux.LinuxDistro
import com.inspiredandroid.kai.sandbox.LinuxSandboxManager
import com.inspiredandroid.kai.sandbox.SandboxState
import com.inspiredandroid.kai.sandbox.SessionShell
import com.inspiredandroid.kai.sandbox.importFileInto
import com.inspiredandroid.kai.sandbox.openFileWithIntent
import com.inspiredandroid.kai.sandbox.readFileAsText
import com.inspiredandroid.kai.sandbox.toFileEntry
import io.github.vinceglb.filekit.PlatformFile
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.terminal_sandbox_not_ready
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import org.koin.java.KoinJavaComponent.inject
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

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
        _status.value = quickStatus(sandboxManager.state.value)
        // Leave previousState null so the launched collect's first mapState(Ready)
        // computes disk usage on IO.

        scope.launch {
            sandboxManager.state.collect { state ->
                // Guard the mapping: if anything inside (e.g. disk-usage walk)
                // throws, we'd lose the collector and the UI would freeze on
                // its current status until process restart.
                try {
                    _status.value = mapState(state)
                } catch (e: Throwable) {
                    android.util.Log.e("SandboxController", "mapState failed for $state", e)
                    _status.value = SandboxStatus(
                        distro = sandboxManager.distro,
                        error = true,
                        label = SandboxStatusLabel.Failure.Status(e.message ?: e::class.simpleName.orEmpty()),
                    )
                }
                previousState = state
                // Only a settled sandbox can have gained or lost migratable
                // files, and this walks another whole home — so it never runs
                // during an install's progress updates.
                if (!_status.value.working) refreshMigration()
            }
        }
        refreshMigration()
    }

    /**
     * Re-answers "is there anything to copy over?" on IO and patches it into the
     * status. Kept out of the status mapping itself: that mapping runs on the
     * calling thread for the first paint and for a distribution switch, and this
     * walk is unbounded.
     */
    private fun refreshMigration() {
        scope.launch {
            val distro = sandboxManager.distro
            val migration = runCatching { sandboxManager.surveyMigration() }.getOrNull()
            // A switch that landed while the walk ran has already published its
            // own status, and this answer describes the install we have left —
            // so it must not be published *or* cached for the next status build.
            if (sandboxManager.distro != distro) return@launch
            knownMigration = migration
            _status.update { it.copy(migration = migration) }
        }
    }

    private fun mapState(state: SandboxState): SandboxStatus = when (state) {
        is SandboxState.NotInstalled -> status(label = SandboxStatusLabel.NotInstalled)

        is SandboxState.Downloading -> status(
            working = true,
            progress = state.progress,
            label = SandboxStatusLabel.Downloading,
        )

        is SandboxState.Extracting -> status(working = true, label = SandboxStatusLabel.Extracting)

        is SandboxState.Installing -> status(
            installed = java.io.File(sandboxManager.rootfsPath).isDirectory,
            working = true,
            label = state.label,
            diskUsageMB = cachedDiskUsageMB,
        )

        is SandboxState.Ready -> {
            if (previousState !is SandboxState.Ready) {
                cachedDiskUsageMB = sandboxManager.getDiskUsageMB()
            }
            readyStatus(diskUsageMB = cachedDiskUsageMB)
        }

        is SandboxState.Error -> status(error = true, label = state.label)
    }

    /**
     * The same mapping without the disk-usage walk, for the paths that have to
     * answer on the calling thread: process start, and a distribution switch —
     * where the buttons must stop describing the outgoing rootfs immediately.
     */
    private fun quickStatus(state: SandboxState): SandboxStatus = if (state is SandboxState.Ready) readyStatus() else mapState(state)

    private fun readyStatus(diskUsageMB: Long = 0) = status(
        installed = true,
        ready = true,
        label = SandboxStatusLabel.Ready,
        diskUsageMB = diskUsageMB,
        packagesInstalled = sandboxManager.arePackagesInstalled(),
    )

    /**
     * Last read of which distributions exist on disk. Only a settled state can
     * have changed that, and the picker consuming it is hidden while an install
     * runs — so a download's per-chunk progress updates do not each re-read both
     * install markers.
     */
    private var knownInstalledDistros: Set<LinuxDistro> = emptySet()

    private fun installedDistros(working: Boolean): Set<LinuxDistro> {
        if (!working) knownInstalledDistros = sandboxManager.installedDistros()
        return knownInstalledDistros
    }

    /** Last answer from [refreshMigration], so rebuilding a status never walks. */
    private var knownMigration: SandboxMigration? = null

    /** Every status carries the same view of which distributions exist on disk. */
    private fun status(
        installed: Boolean = false,
        ready: Boolean = false,
        working: Boolean = false,
        progress: Float? = null,
        label: SandboxStatusLabel? = null,
        diskUsageMB: Long = 0,
        packagesInstalled: Boolean = false,
        error: Boolean = false,
    ) = SandboxStatus(
        installed = installed,
        ready = ready,
        working = working,
        progress = progress,
        label = label,
        diskUsageMB = diskUsageMB,
        packagesInstalled = packagesInstalled,
        error = error,
        distro = sandboxManager.distro,
        installedDistros = installedDistros(working),
        migration = knownMigration,
    )

    override fun setup() {
        sandboxManager.setup()
    }

    override fun cancel() {
        sandboxManager.cancel()
    }

    override fun reset() {
        sandboxManager.reset()
    }

    override fun selectDistro(distro: LinuxDistro) {
        sandboxManager.selectDistro(distro)
        // The manager's state can land on the same value it already held (both
        // distributions uninstalled, say), and a StateFlow would not re-emit —
        // so the switch is published here rather than waited for.
        cachedDiskUsageMB = 0
        previousState = null
        // The outgoing install's answer describes the wrong direction now.
        knownMigration = null
        _status.value = quickStatus(sandboxManager.state.value)
        scope.launch {
            val state = sandboxManager.state.value
            _status.value = mapState(state)
            previousState = state
        }
        refreshMigration()
    }

    override fun migrateHome() {
        sandboxManager.migrateHome()
    }

    override fun installPackages() {
        sandboxManager.installPackages()
    }

    override fun closeSession(sessionId: String) {
        sandboxManager.closeShell(sessionId)
    }

    override fun transcriptFor(sessionId: String): SnapshotStateList<com.inspiredandroid.kai.TerminalLine> = sandboxManager.transcriptFor(sessionId)

    override fun clearTranscript(sessionId: String) {
        sandboxManager.clearTranscript(sessionId)
    }

    override fun setTranscriptInteractive(sessionId: String, interacting: Boolean) {
        sandboxManager.setSessionInteractive(sessionId, interacting)
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
            // Streaming output is read by a person in the terminal, unlike
            // [executeCommand]'s return value, which is an agent tool result.
            onStderr(getString(Res.string.terminal_sandbox_not_ready))
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
        val dir = sandboxManager.fileMap().resolve(path)
            ?: return@withContext emptyList()
        if (!dir.isDirectory) return@withContext emptyList()

        val normalized = if (path.endsWith("/")) path.dropLast(1) else path
        val isRoot = normalized.isEmpty() || normalized == "/"
        // On a legacy install /root is bound in from external storage, so the
        // rootfs listing shows an empty mount point — swap it for the real thing.
        val homeIsSeparate = isRoot && !sandboxManager.homeOnRootfs

        val children = dir.listFiles().orEmpty()
            .filterNot { homeIsSeparate && it.name == "root" }
            .map { it.toFileEntry(parent = if (isRoot) "" else normalized) }
            .toMutableList()

        if (homeIsSeparate) {
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

    override suspend fun readTextFile(path: String, maxBytes: Int, force: Boolean): TextFileResult = withContext(Dispatchers.IO) {
        val file = sandboxManager.fileMap().resolve(path)
            ?: return@withContext TextFileResult.Unreadable
        readFileAsText(file, maxBytes, force)
    }

    override suspend fun writeTextFile(path: String, content: String): Boolean = withContext(Dispatchers.IO) {
        val file = sandboxManager.fileMap().resolve(path)
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
        val file = sandboxManager.fileMap().resolve(path)
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid path: $path"))
        if (!file.isFile) return@withContext Result.failure(IllegalArgumentException("Not a file: $path"))
        val result = openFileWithIntent(context, file)
        if (result.success) Result.success(Unit) else Result.failure(IllegalStateException(result.error ?: "Open failed"))
    }

    override suspend fun deleteEntry(path: String, recursive: Boolean): Boolean = withContext(Dispatchers.IO) {
        val file = sandboxManager.fileMap().resolve(path)
            ?: return@withContext false
        if (!file.exists()) return@withContext false
        // Refuse to delete the bind roots themselves.
        if (sandboxManager.fileMap().isRoot(file)) return@withContext false
        when {
            file.isDirectory && !recursive -> {
                val empty = file.list()?.isEmpty() != false
                if (empty) file.delete() else false
            }

            file.isDirectory -> file.deleteRecursively()

            else -> file.delete()
        }
    }

    override suspend fun importFile(directoryPath: String, source: PlatformFile): Result<String> = withContext(Dispatchers.IO) {
        val dir = sandboxManager.fileMap().resolve(directoryPath)
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid path: $directoryPath"))
        try {
            val created = importFileInto(dir, source)
            val parent = directoryPath.trimEnd('/')
            Result.success(if (parent.isEmpty()) "/${created.name}" else "$parent/${created.name}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun renameEntry(path: String, newName: String): Result<String> = withContext(Dispatchers.IO) {
        if (newName.isBlank() || newName.contains('/') || newName.contains('\\') ||
            newName == "." || newName == ".."
        ) {
            return@withContext Result.failure(IllegalArgumentException("Invalid name"))
        }
        val src = sandboxManager.fileMap().resolve(path)
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid path"))
        if (!src.exists()) return@withContext Result.failure(IllegalArgumentException("Not found"))
        if (sandboxManager.fileMap().isRoot(src)) {
            return@withContext Result.failure(IllegalArgumentException("Cannot rename sandbox root"))
        }
        val parentSandbox = path.substringBeforeLast('/', "")
        val newSandboxPath = if (parentSandbox.isEmpty()) "/$newName" else "$parentSandbox/$newName"
        val dest = sandboxManager.fileMap().resolve(newSandboxPath)
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid destination"))
        if (dest.exists()) return@withContext Result.failure(IllegalStateException("collision"))
        if (src.renameTo(dest)) {
            Result.success(newSandboxPath)
        } else {
            Result.failure(IllegalStateException("rename failed"))
        }
    }
}

/** Returned to the agent as a tool result, so it stays English like the rest of the tool surface. */
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
