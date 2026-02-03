package com.inspiredandroid.kai.tools

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

actual class SmsPermissionController actual constructor() {
    private val _permissionRequested = MutableStateFlow(false)
    actual val permissionRequested: StateFlow<Boolean> = _permissionRequested

    actual fun hasReadPermission(): Boolean {
        // Desktop doesn't have SMS permissions
        return false
    }

    actual fun hasSendPermission(): Boolean {
        // Desktop doesn't have SMS permissions
        return false
    }

    actual fun hasContactsPermission(): Boolean {
        // Desktop doesn't have SMS permissions
        return false
    }

    actual suspend fun requestReadPermission(): Boolean {
        // Desktop doesn't support SMS access
        return false
    }

    actual suspend fun requestSendPermission(): Boolean {
        // Desktop doesn't support SMS access
        return false
    }

    actual suspend fun requestContactsPermission(): Boolean {
        // Desktop doesn't support SMS access
        return false
    }

    actual fun onPermissionResult(granted: Boolean) {
        // No-op for desktop
    }
}

@Composable
actual fun SetupSmsPermissionHandler(controller: SmsPermissionController) {
    // No-op for desktop - no permission launcher needed
}
