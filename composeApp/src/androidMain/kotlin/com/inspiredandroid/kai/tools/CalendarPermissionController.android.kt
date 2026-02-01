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

actual class CalendarPermissionController actual constructor() {
    private val context: Context by inject(Context::class.java)

    private val _permissionRequested = MutableStateFlow(false)
    actual val permissionRequested: StateFlow<Boolean> = _permissionRequested

    private val permissionResultFlow = MutableStateFlow<Boolean?>(null)

    actual fun hasPermission(): Boolean {
        val hasRead = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR,
        ) == PackageManager.PERMISSION_GRANTED
        val hasWrite = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CALENDAR,
        ) == PackageManager.PERMISSION_GRANTED
        return hasRead && hasWrite
    }

    actual suspend fun requestPermission(): Boolean {
        if (hasPermission()) {
            return true
        }

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
}

private val calendarPermissions = arrayOf(
    Manifest.permission.READ_CALENDAR,
    Manifest.permission.WRITE_CALENDAR,
)

@Composable
actual fun SetupCalendarPermissionHandler(controller: CalendarPermissionController) {
    val permissionRequested by controller.permissionRequested.collectAsState()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        controller.onPermissionResult(allGranted)
    }

    LaunchedEffect(permissionRequested) {
        if (permissionRequested) {
            launcher.launch(calendarPermissions)
        }
    }
}
