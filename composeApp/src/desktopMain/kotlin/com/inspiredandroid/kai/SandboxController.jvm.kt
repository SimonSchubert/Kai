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
}
