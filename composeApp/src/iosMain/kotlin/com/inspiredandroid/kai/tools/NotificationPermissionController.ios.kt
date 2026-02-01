package com.inspiredandroid.kai.tools

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

actual class NotificationPermissionController actual constructor() {
    private val _permissionRequested = MutableStateFlow(false)
    actual val permissionRequested: StateFlow<Boolean> = _permissionRequested

    actual fun hasPermission(): Boolean {
        // iOS notification permission not yet implemented
        return false
    }

    actual suspend fun requestPermission(): Boolean {
        // iOS notification permission not yet implemented
        return false
    }

    actual fun onPermissionResult(granted: Boolean) {
        // No-op for iOS
    }
}

@Composable
actual fun SetupNotificationPermissionHandler(controller: NotificationPermissionController) {
    // No-op for iOS - not yet implemented
}
