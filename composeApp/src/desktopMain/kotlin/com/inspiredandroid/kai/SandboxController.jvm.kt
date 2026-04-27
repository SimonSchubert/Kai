package com.inspiredandroid.kai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

actual fun createSandboxController(): SandboxController = NoOpSandboxController()

class NoOpSandboxController : SandboxController {
    override val status: StateFlow<SandboxStatus> = MutableStateFlow(SandboxStatus())
    override fun setup() {}
    override fun cancel() {}
    override fun reset() {}
    override fun installPackages() {}
    override suspend fun executeCommand(command: String): String = ""
    override suspend fun executeCommandStreaming(
        command: String,
        onStdout: (String) -> Unit,
        onStderr: (String) -> Unit,
    ): CommandHandle = NoOpCommandHandle

    override suspend fun listDirectory(path: String): List<SandboxFileEntry> = emptyList()
    override suspend fun readTextFile(path: String, maxBytes: Int): String? = null
    override suspend fun writeTextFile(path: String, content: String): Boolean = false
    override suspend fun openFile(path: String): Result<Unit> = Result.failure(UnsupportedOperationException("Sandbox file browser is Android-only"))
    override suspend fun deleteEntry(path: String, recursive: Boolean): Boolean = false
    override suspend fun renameEntry(path: String, newName: String): Result<String> = Result.failure(UnsupportedOperationException("Sandbox file browser is Android-only"))
}
