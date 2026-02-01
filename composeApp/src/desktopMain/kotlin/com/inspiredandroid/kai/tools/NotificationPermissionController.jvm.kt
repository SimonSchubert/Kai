package com.inspiredandroid.kai.tools

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

actual class NotificationPermissionController actual constructor() {
    private val _permissionRequested = MutableStateFlow(false)
    actual val permissionRequested: StateFlow<Boolean> = _permissionRequested

    actual fun hasPermission(): Boolean {
        // Desktop doesn't have runtime permissions for notifications
        return true
    }

    actual suspend fun requestPermission(): Boolean {
        // Desktop doesn't need permission requests
        return true
    }

    actual fun onPermissionResult(granted: Boolean) {
        // No-op for desktop
    }
}

@Composable
actual fun SetupNotificationPermissionHandler(controller: NotificationPermissionController) {
    // No-op for desktop - no permission launcher needed
}
