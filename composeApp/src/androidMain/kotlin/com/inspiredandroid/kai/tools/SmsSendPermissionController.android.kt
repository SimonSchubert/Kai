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
import kotlin.time.Duration.Companion.seconds

actual class SmsSendPermissionController actual constructor() {
    private val context: Context by inject(Context::class.java)

    private val _permissionRequested = MutableStateFlow(false)
    actual val permissionRequested: StateFlow<Boolean> = _permissionRequested

    private val permissionResultFlow = MutableStateFlow<Boolean?>(null)

    actual fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.SEND_SMS,
    ) == PackageManager.PERMISSION_GRANTED

    actual suspend fun requestPermission(): Boolean {
        if (hasPermission()) return true

        permissionResultFlow.value = null
        _permissionRequested.value = true

        val result = withTimeoutOrNull(60.seconds) {
            permissionResultFlow.first { it != null }
        }

        _permissionRequested.value = false
        return result ?: false
    }

    actual fun onPermissionResult(granted: Boolean) {
        permissionResultFlow.value = granted
    }
}

@Composable
actual fun SetupSmsSendPermissionHandler(controller: SmsSendPermissionController) {
    val permissionRequested by controller.permissionRequested.collectAsState()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        controller.onPermissionResult(granted)
    }

    LaunchedEffect(permissionRequested) {
        if (permissionRequested) {
            launcher.launch(Manifest.permission.SEND_SMS)
        }
    }
}
