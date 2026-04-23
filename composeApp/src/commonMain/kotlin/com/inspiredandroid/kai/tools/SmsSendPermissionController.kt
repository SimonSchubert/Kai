package com.inspiredandroid.kai.tools

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.StateFlow

/**
 * Multiplatform controller for `SEND_SMS` runtime permission requests.
 * Independent of [SmsPermissionController] (which handles `READ_SMS`) so the
 * user can opt into read without also granting send.
 */
expect class SmsSendPermissionController() {
    val permissionRequested: StateFlow<Boolean>

    fun hasPermission(): Boolean

    suspend fun requestPermission(): Boolean

    fun onPermissionResult(granted: Boolean)
}

@Composable
expect fun SetupSmsSendPermissionHandler(controller: SmsSendPermissionController)
