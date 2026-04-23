package com.inspiredandroid.kai.tools

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

actual class SmsPermissionController actual constructor() {
    actual val permissionRequested: StateFlow<Boolean> = MutableStateFlow(false)
    actual fun hasPermission(): Boolean = false
    actual suspend fun requestPermission(): Boolean = false
    actual fun onPermissionResult(granted: Boolean) = Unit
}

@Composable
actual fun SetupSmsPermissionHandler(controller: SmsPermissionController) = Unit
