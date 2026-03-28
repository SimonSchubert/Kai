package com.inspiredandroid.kai.sandbox

sealed interface SandboxState {
    data object NotInstalled : SandboxState
    data class Downloading(val progress: Float) : SandboxState
    data object Extracting : SandboxState
    data class Installing(val detail: String = "") : SandboxState
    data object Ready : SandboxState
    data class Error(val message: String) : SandboxState
}
