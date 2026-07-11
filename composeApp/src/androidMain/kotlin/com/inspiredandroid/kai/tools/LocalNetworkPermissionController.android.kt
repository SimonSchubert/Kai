package com.inspiredandroid.kai.tools

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
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

/** Android 17 (API 37), where local network protection became enforced for apps targeting 37+. */
private const val LOCAL_NETWORK_ENFORCEMENT_SDK = 37

actual class LocalNetworkPermissionController actual constructor() {
    private val context: Context by inject(Context::class.java)

    private val _permissionRequested = MutableStateFlow(false)
    actual val permissionRequested: StateFlow<Boolean> = _permissionRequested

    private val permissionResultFlow = MutableStateFlow<Boolean?>(null)

    actual fun hasPermission(): Boolean {
        // Only gated on Android 17+ for apps targeting SDK 37+
        return if (Build.VERSION.SDK_INT >= LOCAL_NETWORK_ENFORCEMENT_SDK) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_LOCAL_NETWORK,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Permission not required on older Android versions
            true
        }
    }

    actual suspend fun requestPermission(): Boolean {
        if (hasPermission()) {
            return true
        }

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

    actual fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // No settings app to handle the intent — nothing we can do.
        }
    }
}

@Composable
actual fun SetupLocalNetworkPermissionHandler(controller: LocalNetworkPermissionController) {
    val permissionRequested by controller.permissionRequested.collectAsState()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        controller.onPermissionResult(granted)
    }

    LaunchedEffect(permissionRequested) {
        if (permissionRequested && Build.VERSION.SDK_INT >= LOCAL_NETWORK_ENFORCEMENT_SDK) {
            launcher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
        }
    }
}
