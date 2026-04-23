package com.inspiredandroid.kai.tools

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.StateFlow

/**
 * Multiplatform controller for SMS read permission requests.
 * This bridges the gap between tool execution (suspend functions) and Compose permission launchers.
 */
expect class SmsPermissionController() {
    val permissionRequested: StateFlow<Boolean>

    fun hasPermission(): Boolean

    suspend fun requestPermission(): Boolean

    fun onPermissionResult(granted: Boolean)
}

@Composable
expect fun SetupSmsPermissionHandler(controller: SmsPermissionController)
