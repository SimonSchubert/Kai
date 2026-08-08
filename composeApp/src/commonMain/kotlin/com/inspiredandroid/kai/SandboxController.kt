package com.inspiredandroid.kai

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.inspiredandroid.kai.linux.LinuxDistro
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class SandboxStatus(
    val installed: Boolean = false,
    val ready: Boolean = false,
    val working: Boolean = false,
    val progress: Float? = null,
    val statusText: String = "",
    val diskUsageMB: Long = 0,
    val packagesInstalled: Boolean = false,
    val error: Boolean = false,
    /**
     * What is on disk once [installed], and what a fresh install would become
     * before that. Package commands, tool descriptions and the Settings card all
     * follow it.
     */
    val distro: LinuxDistro = LinuxDistro.DEFAULT,
)

interface CommandHandle {
    fun cancel()
    fun isCancelled(): Boolean
    suspend fun writeInput(line: String)
    suspend fun awaitExit(): Int
}

internal object NoOpCommandHandle : CommandHandle {
    override fun cancel() {}
    override fun isCancelled(): Boolean = false
    override suspend fun writeInput(line: String) {}
    override suspend fun awaitExit(): Int = -1
}

data class SandboxFileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModifiedMs: Long,
)

/** Sentinel ids for shell sessions that aren't tied to a specific chat. */
object SandboxSessions {
    /** Default scratch session — used when no caller-specific id is available. */
    const val DEFAULT = "__default__"

    /** Background system maintenance (package manager UI, settings refreshes). */
    const val SYSTEM = "__system__"

    /** User-facing Terminal tab in Settings. */
    const val TERMINAL = "__terminal__"

    /** True for chat-bound session ids (anything that isn't a sentinel). Such sessions get their transcript persisted. */
    fun isPersistable(sessionId: String): Boolean = sessionId != TERMINAL && sessionId != SYSTEM && sessionId != DEFAULT
}

interface SandboxController : FileBrowserSource {
    val status: StateFlow<SandboxStatus>

    /** Active shell-session ids (in-memory only, not persisted). */
    val sessions: StateFlow<List<String>>
    fun setup()
    fun cancel()
    fun reset()
    fun installPackages()
    suspend fun executeCommand(
        command: String,
        sessionId: String = SandboxSessions.DEFAULT,
    ): String
    suspend fun executeCommandStreaming(
        command: String,
        onStdout: (String) -> Unit,
        onStderr: (String) -> Unit,
        sessionId: String = SandboxSessions.DEFAULT,
    ): CommandHandle

    /** Drop the shell for [sessionId] if any. Idempotent. */
    fun closeSession(sessionId: String) {}

    /**
     * Live transcript of commands and output for [sessionId]. The list is
     * populated regardless of which path drove the command (chat tool or
     * Terminal UI), so the user can see the agent's activity. Returns an
     * empty, non-mutated list on platforms without a sandbox.
     */
    fun transcriptFor(sessionId: String): SnapshotStateList<TerminalLine> = mutableStateListOf()

    /** Wipe the transcript for [sessionId]. Idempotent. */
    fun clearTranscript(sessionId: String) {}

    /**
     * Pause/resume bounded-trim on [sessionId]'s transcript. Set to true while
     * the user is dragging to select text in the LazyColumn rendering this
     * transcript — pruning lines mid-drag unregisters their selectables and
     * crashes SelectionManager on its next lookup. Resuming runs a catch-up
     * trim so the transcript settles back to its cap.
     */
    fun setTranscriptInteractive(sessionId: String, interacting: Boolean) {}
}

/** The sandbox is Android-only; every other target gets this. */
class NoOpSandboxController : SandboxController {
    override val status: StateFlow<SandboxStatus> = MutableStateFlow(SandboxStatus())
    override val sessions: StateFlow<List<String>> = MutableStateFlow(emptyList())
    override fun setup() {}
    override fun cancel() {}
    override fun reset() {}
    override fun installPackages() {}
    override suspend fun executeCommand(command: String, sessionId: String): String = ""
    override suspend fun executeCommandStreaming(
        command: String,
        onStdout: (String) -> Unit,
        onStderr: (String) -> Unit,
        sessionId: String,
    ): CommandHandle = NoOpCommandHandle

    override suspend fun listDirectory(path: String): List<SandboxFileEntry> = emptyList()
    override suspend fun readTextFile(path: String, maxBytes: Int, force: Boolean): TextFileResult = TextFileResult.Unreadable
    override suspend fun writeTextFile(path: String, content: String): Boolean = false
    override suspend fun openFile(path: String): Result<Unit> = Result.failure(UnsupportedOperationException("Sandbox file browser is Android-only"))
    override suspend fun deleteEntry(path: String, recursive: Boolean): Boolean = false
    override suspend fun renameEntry(path: String, newName: String): Result<String> = Result.failure(UnsupportedOperationException("Sandbox file browser is Android-only"))
    override suspend fun importFile(directoryPath: String, source: PlatformFile): Result<String> = Result.failure(UnsupportedOperationException("Sandbox file browser is Android-only"))
}

expect fun createSandboxController(): SandboxController
