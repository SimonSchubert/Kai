package com.inspiredandroid.kai.tools

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

actual class LocalNetworkPermissionController actual constructor() {
    private val _permissionRequested = MutableStateFlow(false)
    actual val permissionRequested: StateFlow<Boolean> = _permissionRequested

    actual fun hasPermission(): Boolean {
        // The browser gates cross-origin requests itself; no app-level permission
        return true
    }

    actual suspend fun requestPermission(): Boolean {
        // No permission to request on web
        return true
    }

    actual fun onPermissionResult(granted: Boolean) {
        // No-op for web
    }

    actual fun openAppSettings() {
        // No app permission settings on web
    }
}

@Composable
actual fun SetupLocalNetworkPermissionHandler(controller: LocalNetworkPermissionController) {
    // No-op for web - no permission launcher needed
}
