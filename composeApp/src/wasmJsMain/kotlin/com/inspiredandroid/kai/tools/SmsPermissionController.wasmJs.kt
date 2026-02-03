package com.inspiredandroid.kai.tools

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

actual class SmsPermissionController actual constructor() {
    private val _permissionRequested = MutableStateFlow(false)
    actual val permissionRequested: StateFlow<Boolean> = _permissionRequested

    actual fun hasReadPermission(): Boolean {
        // Web doesn't have SMS permissions
        return false
    }

    actual fun hasSendPermission(): Boolean {
        // Web doesn't have SMS permissions
        return false
    }

    actual fun hasContactsPermission(): Boolean {
        // Web doesn't have SMS permissions
        return false
    }

    actual suspend fun requestReadPermission(): Boolean {
        // Web doesn't support SMS access
        return false
    }

    actual suspend fun requestSendPermission(): Boolean {
        // Web doesn't support SMS access
        return false
    }

    actual suspend fun requestContactsPermission(): Boolean {
        // Web doesn't support SMS access
        return false
    }

    actual fun onPermissionResult(granted: Boolean) {
        // No-op for web
    }
}

@Composable
actual fun SetupSmsPermissionHandler(controller: SmsPermissionController) {
    // No-op for web - no permission launcher needed
}
