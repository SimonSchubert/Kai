package com.inspiredandroid.kai.tools

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.StateFlow

/**
 * Multiplatform controller for calendar permission requests.
 * This bridges the gap between tool execution (suspend functions) and Compose permission launchers.
 */
expect class CalendarPermissionController() {
    /**
     * Flow that emits true when a permission request is pending and should be launched.
     */
    val permissionRequested: StateFlow<Boolean>

    /**
     * Check if calendar permission is already granted.
     */
    fun hasPermission(): Boolean

    /**
     * Request calendar permission and suspend until the user responds.
     * Returns true if permission was granted, false otherwise.
     */
    suspend fun requestPermission(): Boolean

    /**
     * Called from Compose when the permission result is received.
     */
    fun onPermissionResult(granted: Boolean)
}

/**
 * Composable that sets up the permission launcher for the calendar permission.
 * This should be called at a high level in the composable hierarchy.
 */
@Composable
expect fun SetupCalendarPermissionHandler(controller: CalendarPermissionController)
