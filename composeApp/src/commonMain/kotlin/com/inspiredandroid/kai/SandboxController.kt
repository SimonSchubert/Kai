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

interface SandboxController {
    val status: StateFlow<SandboxStatus>
    fun setup()
    fun cancel()
    fun reset()
    fun installPackages()
    suspend fun executeCommand(command: String): String
}

expect fun createSandboxController(): SandboxController
