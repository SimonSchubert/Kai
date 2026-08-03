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
import org.koin.java.KoinJavaComponent.inject

/** Android 17 (API 37), where local network protection became enforced for apps targeting 37+. */
private const val LOCAL_NETWORK_ENFORCEMENT_SDK = 37

/**
 * The manifest permissions backing [permission] on this OS version. An empty array means the
 * permission isn't gated here — [platformHasPermission] then reports it as granted and
 * [platformCanRequest] suppresses the launcher.
 */
private fun permissionsFor(permission: AppPermission): Array<String> = when (permission) {
    AppPermission.CALENDAR -> arrayOf(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR,
    )

    // POST_NOTIFICATIONS is only required on Android 13+.
    AppPermission.POST_NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        emptyArray()
    }

    // READ_SMS/SEND_SMS aren't declared in the Play Store flavor's merged manifest, so
    // checkSelfPermission returns DENIED there and the feature stays invisible.
    AppPermission.READ_SMS -> arrayOf(Manifest.permission.READ_SMS)

    AppPermission.SEND_SMS -> arrayOf(Manifest.permission.SEND_SMS)

    // Only gated on Android 17+ for apps targeting SDK 37+.
    AppPermission.LOCAL_NETWORK -> if (Build.VERSION.SDK_INT >= LOCAL_NETWORK_ENFORCEMENT_SDK) {
        arrayOf(Manifest.permission.ACCESS_LOCAL_NETWORK)
    } else {
        emptyArray()
    }
}

private val androidContext: Context by inject(Context::class.java)

internal actual fun platformHasPermission(permission: AppPermission): Boolean = permissionsFor(permission).all {
    ContextCompat.checkSelfPermission(androidContext, it) == PackageManager.PERMISSION_GRANTED
}

internal actual fun platformCanRequest(permission: AppPermission): Boolean = permissionsFor(permission).isNotEmpty()

internal actual fun platformOpenAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", androidContext.packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        androidContext.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // No settings app to handle the intent — nothing we can do.
    }
}

@Composable
actual fun SetupPermissionHandler(controller: PermissionController) {
    val permissionRequested by controller.permissionRequested.collectAsState()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        controller.onPermissionResult(permissions.values.all { it })
    }

    LaunchedEffect(permissionRequested) {
        val permissions = permissionsFor(controller.permission)
        if (permissionRequested && permissions.isNotEmpty()) {
            launcher.launch(permissions)
        }
    }
}
