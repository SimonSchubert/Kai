package com.inspiredandroid.kai.tools

import androidx.compose.runtime.Composable

internal actual fun platformHasPermission(permission: AppPermission): Boolean = when (permission) {
    // Not implemented on iOS.
    AppPermission.CALENDAR,
    AppPermission.POST_NOTIFICATIONS,
    AppPermission.READ_SMS,
    AppPermission.SEND_SMS,
    -> false

    // iOS shows its own local network prompt automatically on first access.
    AppPermission.LOCAL_NETWORK -> true
}

internal actual fun platformCanRequest(permission: AppPermission): Boolean = false

internal actual fun platformOpenAppSettings() {
    // Never reached: LOCAL_NETWORK is always granted on iOS, so the denied status
    // (and its settings button) can't appear.
}

@Composable
actual fun SetupPermissionHandler(controller: PermissionController) = Unit
