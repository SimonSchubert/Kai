package com.inspiredandroid.kai.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inspiredandroid.kai.Platform
import com.inspiredandroid.kai.SandboxController
import com.inspiredandroid.kai.SandboxStatus
import com.inspiredandroid.kai.currentPlatform
import com.inspiredandroid.kai.data.DataRepository
import com.inspiredandroid.kai.linux.LinuxDistro
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
    /**
     * The installed distro once [sandboxInstalled], the pending choice before
     * that. The controller reports both through the same field because nothing
     * downstream should ever prefer the setting over what is on disk.
     */
    val distro: LinuxDistro = LinuxDistro.DEFAULT,
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
                distro = dataRepository.getSandboxDistro(),
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
        distro = status.distro,
    )

    fun onToggleSandbox(enabled: Boolean) {
        dataRepository.setSandboxEnabled(enabled)
        _state.update { it.copy(isSandboxEnabled = enabled) }
    }

    /**
     * Only meaningful before an install: the card hides the picker afterwards,
     * because an existing rootfs keeps whatever it was built as.
     */
    fun onSelectDistro(distro: LinuxDistro) {
        if (_state.value.sandboxInstalled) return
        dataRepository.setSandboxDistro(distro)
        _state.update { it.copy(distro = distro) }
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
