package com.inspiredandroid.kai.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inspiredandroid.kai.Platform
import com.inspiredandroid.kai.SandboxController
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

    private val _state = MutableStateFlow(
        SandboxUiState(
            showSandbox = currentPlatform is Platform.Mobile.Android,
            isSandboxEnabled = dataRepository.isSandboxEnabled(),
        ),
    )

    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            sandboxController.status.collect { sandboxStatus ->
                _state.update {
                    it.copy(
                        sandboxInstalled = sandboxStatus.installed,
                        sandboxReady = sandboxStatus.ready,
                        sandboxProgress = sandboxStatus.progress,
                        sandboxStatusText = sandboxStatus.statusText,
                        sandboxDiskUsageMB = sandboxStatus.diskUsageMB,
                        sandboxPackagesInstalled = sandboxStatus.packagesInstalled,
                        isWorking = sandboxStatus.working,
                        hasError = sandboxStatus.error,
                    )
                }
            }
        }
    }

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
