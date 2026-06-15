package com.inspiredandroid.kai.sandbox

sealed interface SandboxState {
    data object NotInstalled : SandboxState
    data class Downloading(val progress: Float) : SandboxState
    data object Extracting : SandboxState
    data class Installing(val detail: String = "") : SandboxState
    data class Ready(val warning: String? = null) : SandboxState
    data class Error(val message: String) : SandboxState
}
