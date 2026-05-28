package com.inspiredandroid.kai

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
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

interface SandboxController {
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

    suspend fun listDirectory(path: String): List<SandboxFileEntry>
    suspend fun readTextFile(path: String, maxBytes: Int = 512_000): String?
    suspend fun writeTextFile(path: String, content: String): Boolean
    suspend fun openFile(path: String): Result<Unit>
    suspend fun deleteEntry(path: String, recursive: Boolean): Boolean
    suspend fun renameEntry(path: String, newName: String): Result<String>
}

expect fun createSandboxController(): SandboxController
