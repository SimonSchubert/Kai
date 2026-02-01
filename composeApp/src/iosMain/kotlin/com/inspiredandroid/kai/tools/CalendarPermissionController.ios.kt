package com.inspiredandroid.kai.tools

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

actual class CalendarPermissionController actual constructor() {
    private val _permissionRequested = MutableStateFlow(false)
    actual val permissionRequested: StateFlow<Boolean> = _permissionRequested

    actual fun hasPermission(): Boolean {
        // TODO: Implement using EventKit EKEventStore.authorizationStatus
        return false
    }

    actual suspend fun requestPermission(): Boolean {
        // TODO: Implement using EventKit EKEventStore.requestAccess
        return false
    }

    actual fun onPermissionResult(granted: Boolean) {
        // No-op for iOS - permission result is handled inline
    }
}

@Composable
actual fun SetupCalendarPermissionHandler(controller: CalendarPermissionController) {
    // No-op for iOS - permission is requested inline using EventKit
}
