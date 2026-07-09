package com.inspiredandroid.kai

import androidx.compose.runtime.snapshots.SnapshotStateList
import com.inspiredandroid.kai.data.ConversationStorage
import com.inspiredandroid.kai.sandbox.DesktopLinuxSandboxManager
import com.inspiredandroid.kai.sandbox.DesktopPersistentShell
import com.inspiredandroid.kai.sandbox.DesktopSessionShell
import com.inspiredandroid.kai.sandbox.MicromambaDownloader
import com.inspiredandroid.kai.sandbox.SandboxState
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.inject

actual fun createSandboxController(): SandboxController = DesktopSandboxController()

private const val SANDBOX_NOT_READY = "Sandbox is not ready"
private val TRANSCRIPT_SAVE_DEBOUNCE = 500.milliseconds

class DesktopSandboxController : SandboxController {

    private val conversationStorage: ConversationStorage by inject(ConversationStorage::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val sandboxManager: DesktopLinuxSandboxManager by lazy {
        val baseDir = File(getAppFilesDirectory(), "linux-sandbox/micromamba")
        DesktopLinuxSandboxManager(baseDir, MicromambaDownloader(HttpClient(CIO)))
    }

    private val shells = ConcurrentHashMap<String, DesktopSessionShell>()
    private val pendingSaves = ConcurrentHashMap<String, Job>()
    private val _sessions = MutableStateFlow<List<String>>(emptyList())
    override val sessions: StateFlow<List<String>> = _sessions

    private var previousState: SandboxState? = null
    private var cachedDiskUsageMB = 0L
    private val _status = MutableStateFlow(SandboxStatus())
    override val status: StateFlow<SandboxStatus> = _status

    init {
        // Synchronously seed the status from the manager's current state so the
        // first observer doesn't briefly see "not installed" before the launched
        // collector below catches up. Skip the disk-usage walk in this fast path —
        // it iterates the installed environment and could block the calling
        // thread (often main, since Koin singletons are created lazily on first
        // injection from Composables). The launched collect immediately re-emits
        // the same state and fills in disk usage on Dispatchers.IO.
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
        is SandboxState.NotInstalled -> SandboxStatus(statusText = "Not installed")
        is SandboxState.Downloading -> SandboxStatus(
            working = true,
            progress = state.progress,
            statusText = "Downloading micromamba...",
        )
        is SandboxState.Extracting -> SandboxStatus(working = true, statusText = "Extracting...")
        is SandboxState.Installing -> SandboxStatus(
            installed = sandboxManager.layout.binaryFile.exists(),
            working = true,
            statusText = state.detail.ifEmpty { "Installing..." },
            diskUsageMB = cachedDiskUsageMB,
        )
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
        is SandboxState.Error -> SandboxStatus(error = true, statusText = "Error: ${state.message}")
    }

    override fun setup() = sandboxManager.setup()
    override fun cancel() = sandboxManager.cancel()
    override fun reset() = sandboxManager.reset()
    override fun installPackages() = sandboxManager.installPackages()

    override fun closeSession(sessionId: String) {
        val removed = synchronized(shells) {
            val s = shells.remove(sessionId)
            _sessions.value = shells.keys.toList()
            s
        }
        removed?.reset()
    }

    override fun transcriptFor(sessionId: String): SnapshotStateList<TerminalLine> = shellFor(sessionId).transcript

    override fun clearTranscript(sessionId: String) {
        shells[sessionId]?.transcript?.clear()
    }

    override fun setTranscriptInteractive(sessionId: String, interacting: Boolean) {
        shells[sessionId]?.setPrunePaused(interacting)
    }

    /** Widened from private so ShellCommandTool/ProcessManager (desktop's LLM-facing tools) can
     *  reuse the same session-shell machinery as the Terminal UI, instead of running raw host
     *  processes. See docs/features/sandbox.md for why this exists. */
    internal fun shellFor(sessionId: String): DesktopSessionShell = synchronized(shells) {
        shells[sessionId]?.let { return@synchronized it }
        val persistable = SandboxSessions.isPersistable(sessionId)
        val initialLines = if (persistable) {
            conversationStorage.conversations.value
                .firstOrNull { it.id == sessionId }?.shellTranscript.orEmpty()
        } else {
            emptyList()
        }
        val onChange: ((List<TerminalLine>) -> Unit)? = if (persistable) {
            { lines -> scheduleTranscriptSave(sessionId, lines) }
        } else {
            null
        }
        val wrapper = DesktopSessionShell(sessionId, DesktopPersistentShell(sandboxManager.layout), initialLines, onChange)
        shells[sessionId] = wrapper
        _sessions.value = shells.keys.toList()
        wrapper
    }

    private fun scheduleTranscriptSave(sessionId: String, lines: List<TerminalLine>) {
        pendingSaves[sessionId]?.cancel()
        pendingSaves[sessionId] = scope.launch {
            try {
                delay(TRANSCRIPT_SAVE_DEBOUNCE)
                conversationStorage.updateShellTranscript(sessionId, lines)
            } finally {
                pendingSaves.remove(sessionId)
            }
        }
    }

    override suspend fun executeCommand(command: String, sessionId: String): String = withContext(Dispatchers.IO) {
        if (sandboxManager.state.value !is SandboxState.Ready) return@withContext SANDBOX_NOT_READY
        val result = shellFor(sessionId).run(command, timeoutSeconds = 30)
        val stdout = result["stdout"] as? String ?: ""
        val stderr = result["stderr"] as? String ?: ""
        val exitCode = result["exit_code"] as? Int
        buildString {
            if (stdout.isNotEmpty()) append(stdout)
            if (stderr.isNotEmpty()) {
                if (isNotEmpty()) append("\n")
                append(stderr)
            }
            if (exitCode != null && exitCode != 0 && isEmpty()) append("Exit code: $exitCode")
        }
    }

    override suspend fun executeCommandStreaming(
        command: String,
        onStdout: (String) -> Unit,
        onStderr: (String) -> Unit,
        sessionId: String,
    ): CommandHandle {
        if (sandboxManager.state.value !is SandboxState.Ready) {
            onStderr(SANDBOX_NOT_READY)
            return NoOpCommandHandle
        }
        val shell = shellFor(sessionId)
        val deferred = CompletableDeferred<Map<String, Any>>()
        val cancelled = AtomicBoolean(false)
        scope.launch {
            runCatching {
                shell.run(command, timeoutSeconds = 24L * 60 * 60, onStdout = onStdout, onStderr = onStderr)
            }.onSuccess { deferred.complete(it) }
                .onFailure { deferred.complete(mapOf("exit_code" to -1)) }
        }
        return object : CommandHandle {
            override fun cancel() {
                cancelled.set(true)
                shell.cancelForeground()
            }
            override fun isCancelled(): Boolean = cancelled.get()
            override suspend fun writeInput(line: String) { /* no interactive stdin forwarding in this pass */ }
            override suspend fun awaitExit(): Int = (deferred.await()["exit_code"] as? Int) ?: -1
        }
    }

    override suspend fun listDirectory(path: String): List<SandboxFileEntry> = emptyList()
    override suspend fun readTextFile(path: String, maxBytes: Int): String? = null
    override suspend fun writeTextFile(path: String, content: String): Boolean = false
    override suspend fun openFile(path: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Sandbox file browser not yet implemented for desktop"))
    override suspend fun deleteEntry(path: String, recursive: Boolean): Boolean = false
    override suspend fun renameEntry(path: String, newName: String): Result<String> =
        Result.failure(UnsupportedOperationException("Sandbox file browser not yet implemented for desktop"))
}
