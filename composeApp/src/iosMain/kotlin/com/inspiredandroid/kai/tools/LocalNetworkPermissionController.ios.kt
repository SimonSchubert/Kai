package com.inspiredandroid.kai.tools

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

actual class LocalNetworkPermissionController actual constructor() {
    private val _permissionRequested = MutableStateFlow(false)
    actual val permissionRequested: StateFlow<Boolean> = _permissionRequested

    actual fun hasPermission(): Boolean {
        // iOS shows its own local network prompt automatically on first access
        return true
    }

    actual suspend fun requestPermission(): Boolean {
        // iOS handles the prompt at OS level; nothing to request explicitly
        return true
    }

    actual fun onPermissionResult(granted: Boolean) {
        // No-op for iOS
    }

    actual fun openAppSettings() {
        // Never reached: hasPermission() is always true on iOS, so the denied
        // status (and its settings button) can't appear.
    }
}

@Composable
actual fun SetupLocalNetworkPermissionHandler(controller: LocalNetworkPermissionController) {
    // No-op for iOS - the system prompts on first local network access
}
