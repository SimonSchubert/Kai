package com.inspiredandroid.kai.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inspiredandroid.kai.Platform
import com.inspiredandroid.kai.SandboxController
import com.inspiredandroid.kai.SandboxMigration
import com.inspiredandroid.kai.SandboxStatus
import com.inspiredandroid.kai.SandboxStatusLabel
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
    val sandboxStatusLabel: SandboxStatusLabel? = null,
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
    /** Distributions already on disk, so the picker can say which is a download. */
    val installedDistros: Set<LinuxDistro> = emptySet(),
    /** Files the other install has and this one does not; null when there are none. */
    val migration: SandboxMigration? = null,
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
            // No distro seed: the controller is the only thing that knows which
            // install the sandbox is actually pointed at, and a sandbox older
            // than the picker has one the stored setting does not name.
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
        sandboxStatusLabel = status.label,
        sandboxDiskUsageMB = status.diskUsageMB,
        sandboxPackagesInstalled = status.packagesInstalled,
        isWorking = status.working,
        hasError = status.error,
        distro = status.distro,
        installedDistros = status.installedDistros,
        migration = status.migration,
    )

    fun onToggleSandbox(enabled: Boolean) {
        dataRepository.setSandboxEnabled(enabled)
        _state.update { it.copy(isSandboxEnabled = enabled) }
    }

    /**
     * Points the shell integration at another distribution. Nothing is removed:
     * each keeps its own install, so this is a switch when the target is already
     * there and a plain "Install" offer when it is not.
     *
     * The state is updated ahead of the controller so the radio button answers
     * the tap; the controller's status then confirms it along with what the new
     * install's card should say.
     */
    fun onSelectDistro(distro: LinuxDistro) {
        if (_state.value.distro == distro || _state.value.isWorking) return
        dataRepository.setSandboxDistro(distro)
        _state.update { it.copy(distro = distro) }
        sandboxController.selectDistro(distro)
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

    /**
     * Copies the other distribution's home in. Nothing is removed by it — the
     * distribution it came from is still installed afterwards, and removing that
     * stays the separate, deliberate Uninstall action.
     */
    fun onMigrateHome() {
        sandboxController.migrateHome()
    }
}
