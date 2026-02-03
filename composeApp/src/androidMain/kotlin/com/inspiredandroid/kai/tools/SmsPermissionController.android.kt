package com.inspiredandroid.kai.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.java.KoinJavaComponent.inject

actual class SmsPermissionController actual constructor() {
    private val context: Context by inject(Context::class.java)

    private val _permissionRequested = MutableStateFlow(false)
    actual val permissionRequested: StateFlow<Boolean> = _permissionRequested

    private val permissionResultFlow = MutableStateFlow<Boolean?>(null)

    // Track which permission type is being requested
    // Note: Concurrent permission requests are not supported - requests should be sequential
    internal enum class PermissionType { READ_SMS, SEND_SMS, READ_CONTACTS }

    @Volatile
    private var requestingPermissionType = PermissionType.READ_SMS

    actual fun hasReadPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_SMS,
    ) == PackageManager.PERMISSION_GRANTED

    actual fun hasSendPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.SEND_SMS,
    ) == PackageManager.PERMISSION_GRANTED

    actual fun hasContactsPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_CONTACTS,
    ) == PackageManager.PERMISSION_GRANTED

    actual suspend fun requestReadPermission(): Boolean {
        if (hasReadPermission()) {
            return true
        }

        requestingPermissionType = PermissionType.READ_SMS
        permissionResultFlow.value = null
        _permissionRequested.value = true

        val result = withTimeoutOrNull(60_000L) {
            permissionResultFlow.first { it != null }
        }

        _permissionRequested.value = false
        return result ?: false
    }

    actual suspend fun requestSendPermission(): Boolean {
        if (hasSendPermission()) {
            return true
        }

        requestingPermissionType = PermissionType.SEND_SMS
        permissionResultFlow.value = null
        _permissionRequested.value = true

        val result = withTimeoutOrNull(60_000L) {
            permissionResultFlow.first { it != null }
        }

        _permissionRequested.value = false
        return result ?: false
    }

    actual suspend fun requestContactsPermission(): Boolean {
        if (hasContactsPermission()) {
            return true
        }

        requestingPermissionType = PermissionType.READ_CONTACTS
        permissionResultFlow.value = null
        _permissionRequested.value = true

        val result = withTimeoutOrNull(60_000L) {
            permissionResultFlow.first { it != null }
        }

        _permissionRequested.value = false
        return result ?: false
    }

    actual fun onPermissionResult(granted: Boolean) {
        permissionResultFlow.value = granted
    }

    internal fun getRequestingPermissionType(): PermissionType = requestingPermissionType
}

@Composable
actual fun SetupSmsPermissionHandler(controller: SmsPermissionController) {
    val permissionRequested by controller.permissionRequested.collectAsState()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        controller.onPermissionResult(granted)
    }

    LaunchedEffect(permissionRequested) {
        if (permissionRequested) {
            val permission = when (controller.getRequestingPermissionType()) {
                SmsPermissionController.PermissionType.READ_SMS -> Manifest.permission.READ_SMS
                SmsPermissionController.PermissionType.SEND_SMS -> Manifest.permission.SEND_SMS
                SmsPermissionController.PermissionType.READ_CONTACTS -> Manifest.permission.READ_CONTACTS
            }
            launcher.launch(permission)
        }
    }
}
