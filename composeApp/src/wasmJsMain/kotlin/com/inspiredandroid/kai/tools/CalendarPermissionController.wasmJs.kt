package com.inspiredandroid.kai.tools

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

actual class CalendarPermissionController actual constructor() {
    private val _permissionRequested = MutableStateFlow(false)
    actual val permissionRequested: StateFlow<Boolean> = _permissionRequested

    actual fun hasPermission(): Boolean {
        // Web doesn't have calendar permissions
        return false
    }

    actual suspend fun requestPermission(): Boolean {
        // Web doesn't support native calendar access
        return false
    }

    actual fun onPermissionResult(granted: Boolean) {
        // No-op for web
    }
}

@Composable
actual fun SetupCalendarPermissionHandler(controller: CalendarPermissionController) {
    // No-op for web - no permission launcher needed
}
