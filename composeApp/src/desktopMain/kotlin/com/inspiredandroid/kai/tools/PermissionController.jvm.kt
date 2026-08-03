package com.inspiredandroid.kai.tools

import androidx.compose.runtime.Composable

internal actual fun platformHasPermission(permission: AppPermission): Boolean = when (permission) {
    // Desktop has no runtime permissions for calendar or notifications.
    AppPermission.CALENDAR,
    AppPermission.POST_NOTIFICATIONS,
    -> true

    // No SMS support on desktop.
    AppPermission.READ_SMS,
    AppPermission.SEND_SMS,
    -> false

    // Desktop doesn't gate local network access.
    AppPermission.LOCAL_NETWORK -> true
}

internal actual fun platformCanRequest(permission: AppPermission): Boolean = false

internal actual fun platformOpenAppSettings() {
    // No app permission settings screen on desktop.
}

@Composable
actual fun SetupPermissionHandler(controller: PermissionController) = Unit
