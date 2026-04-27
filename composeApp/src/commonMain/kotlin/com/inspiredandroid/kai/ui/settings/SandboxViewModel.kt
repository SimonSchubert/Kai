package com.inspiredandroid.kai.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inspiredandroid.kai.Platform
import com.inspiredandroid.kai.SandboxController
import com.inspiredandroid.kai.SandboxStatus
import com.inspiredandroid.kai.currentPlatform
import com.inspiredandroid.kai.data.DataRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class SandboxUiState(
    val showSandbox: Boolean = false,
    val sandboxInstalled: Boolean = false,
    val sandboxReady: Boolean = false,
    val sandboxProgress: Float? = null,
    val sandboxStatusText: String = "",
    val sandboxDiskUsageMB: Long = 0,
    val sandboxPackagesInstalled: Boolean = false,
    val isSandboxEnabled: Boolean = true,
    val isWorking: Boolean = false,
    val hasError: Boolean = false,
)

class SandboxViewModel(
    private val dataRepository: DataRepository,
    private val sandboxController: SandboxController,
) : ViewModel() {

    // Seed synchronously from the controller's current status so the first
    // composition doesn't briefly render the install UI when the sandbox is
    // already ready. The controller mirrors LinuxSandboxManager's synchronous
    // installation check, so reading status.value here returns the real state.
    private val _state = MutableStateFlow(
        applyStatus(
            sandboxController.status.value,
            SandboxUiState(
                showSandbox = currentPlatform is Platform.Mobile.Android,
                isSandboxEnabled = dataRepository.isSandboxEnabled(),
            ),
        ),
    )

    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            sandboxController.status.collect { sandboxStatus ->
                _state.update { applyStatus(sandboxStatus, it) }
            }
        }
    }

    private fun applyStatus(status: SandboxStatus, base: SandboxUiState): SandboxUiState = base.copy(
        sandboxInstalled = status.installed,
        sandboxReady = status.ready,
        sandboxProgress = status.progress,
        sandboxStatusText = status.statusText,
        sandboxDiskUsageMB = status.diskUsageMB,
        sandboxPackagesInstalled = status.packagesInstalled,
        isWorking = status.working,
        hasError = status.error,
    )

    fun onToggleSandbox(enabled: Boolean) {
        dataRepository.setSandboxEnabled(enabled)
        _state.update { it.copy(isSandboxEnabled = enabled) }
    }

    fun onSetupSandbox() {
        sandboxController.setup()
    }

    fun onCancelSandbox() {
        sandboxController.cancel()
    }

    fun onResetSandbox() {
        sandboxController.reset()
    }

    fun onInstallPackages() {
        sandboxController.installPackages()
    }
}
