package com.kainext.duo.sandbox

import com.kainext.duo.SandboxStatusLabel

sealed interface SandboxState {
    data object NotInstalled : SandboxState
    data class Downloading(val progress: Float) : SandboxState
    data object Extracting : SandboxState
    data class Installing(val label: SandboxStatusLabel = SandboxStatusLabel.Installing) : SandboxState
    data object Ready : SandboxState
    data class Error(val label: SandboxStatusLabel.Failure) : SandboxState
}
