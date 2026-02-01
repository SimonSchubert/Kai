package com.inspiredandroid.kai.tools

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

actual class NotificationPermissionController actual constructor() {
    private val _permissionRequested = MutableStateFlow(false)
    actual val permissionRequested: StateFlow<Boolean> = _permissionRequested

    actual fun hasPermission(): Boolean {
        // Web notifications not yet implemented
        return false
    }

    actual suspend fun requestPermission(): Boolean {
        // Web notifications not yet implemented
        return false
    }

    actual fun onPermissionResult(granted: Boolean) {
        // No-op for web
    }
}

@Composable
actual fun SetupNotificationPermissionHandler(controller: NotificationPermissionController) {
    // No-op for web - not yet implemented
}
