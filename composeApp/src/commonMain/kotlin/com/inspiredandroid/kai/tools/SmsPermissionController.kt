package com.inspiredandroid.kai.tools

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.StateFlow

/**
 * Multiplatform controller for SMS permission requests.
 * This bridges the gap between tool execution (suspend functions) and Compose permission launchers.
 */
expect class SmsPermissionController() {
    /**
     * Flow that emits true when a permission request is pending and should be launched.
     */
    val permissionRequested: StateFlow<Boolean>

    /**
     * Check if READ_SMS permission is already granted.
     */
    fun hasReadPermission(): Boolean

    /**
     * Check if SEND_SMS permission is already granted.
     */
    fun hasSendPermission(): Boolean

    /**
     * Check if READ_CONTACTS permission is already granted.
     */
    fun hasContactsPermission(): Boolean

    /**
     * Request READ_SMS permission and suspend until the user responds.
     * Returns true if permission was granted, false otherwise.
     */
    suspend fun requestReadPermission(): Boolean

    /**
     * Request SEND_SMS permission and suspend until the user responds.
     * Returns true if permission was granted, false otherwise.
     */
    suspend fun requestSendPermission(): Boolean

    /**
     * Request READ_CONTACTS permission and suspend until the user responds.
     * Returns true if permission was granted, false otherwise.
     */
    suspend fun requestContactsPermission(): Boolean

    /**
     * Called from Compose when the permission result is received.
     */
    fun onPermissionResult(granted: Boolean)
}

/**
 * Composable that sets up the permission launcher for SMS permissions.
 * This should be called at a high level in the composable hierarchy.
 */
@Composable
expect fun SetupSmsPermissionHandler(controller: SmsPermissionController)
