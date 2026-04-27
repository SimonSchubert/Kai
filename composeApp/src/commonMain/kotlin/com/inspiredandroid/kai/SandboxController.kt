package com.inspiredandroid.kai

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

interface SandboxController {
    val status: StateFlow<SandboxStatus>
    fun setup()
    fun cancel()
    fun reset()
    fun installPackages()
    suspend fun executeCommand(command: String): String
    suspend fun executeCommandStreaming(
        command: String,
        onStdout: (String) -> Unit,
        onStderr: (String) -> Unit,
    ): CommandHandle

    suspend fun listDirectory(path: String): List<SandboxFileEntry>
    suspend fun readTextFile(path: String, maxBytes: Int = 512_000): String?
    suspend fun writeTextFile(path: String, content: String): Boolean
    suspend fun openFile(path: String): Result<Unit>
}

expect fun createSandboxController(): SandboxController
