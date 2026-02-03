package com.inspiredandroid.kai.tools

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

actual class SmsPermissionController actual constructor() {
    private val _permissionRequested = MutableStateFlow(false)
    actual val permissionRequested: StateFlow<Boolean> = _permissionRequested

    actual fun hasReadPermission(): Boolean {
        // iOS doesn't allow third-party SMS access
        return false
    }

    actual fun hasSendPermission(): Boolean {
        // iOS doesn't allow third-party SMS access
        return false
    }

    actual fun hasContactsPermission(): Boolean {
        // iOS doesn't allow third-party SMS access
        return false
    }

    actual suspend fun requestReadPermission(): Boolean {
        // iOS doesn't support third-party SMS access
        return false
    }

    actual suspend fun requestSendPermission(): Boolean {
        // iOS doesn't support third-party SMS access
        return false
    }

    actual suspend fun requestContactsPermission(): Boolean {
        // iOS doesn't support third-party SMS access
        return false
    }

    actual fun onPermissionResult(granted: Boolean) {
        // No-op for iOS
    }
}

@Composable
actual fun SetupSmsPermissionHandler(controller: SmsPermissionController) {
    // No-op for iOS - no permission launcher needed
}
