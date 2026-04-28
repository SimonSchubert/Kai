package com.inspiredandroid.kai.tools

/**
 * Controller for notification-listener access. Unlike [SmsPermissionController],
 * `BIND_NOTIFICATION_LISTENER_SERVICE` is not a runtime permission — there is no
 * `requestPermission` dialog. The user must enable the listener manually under
 * **Settings → Apps → Special access → Notification access**, so this controller
 * just checks the granted state and offers a deep-link into that screen.
 */
expect class NotificationListenerController() {
    /** True when the build supports the listener at all (Android FOSS only). */
    fun isSupported(): Boolean

    /** True when the user has enabled Kai under system notification-access settings. */
    fun isAccessGranted(): Boolean

    /** Open the system notification-access settings screen. No-op on unsupported platforms. */
    fun openAccessSettings()
}
