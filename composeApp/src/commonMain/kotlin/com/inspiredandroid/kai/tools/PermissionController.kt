package com.inspiredandroid.kai.tools

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * A runtime permission Kai can ask the user for. Only Android gates any of these at runtime;
 * the other platforms answer from a fixed table in their [platformHasPermission] actual.
 */
enum class AppPermission {
    /** Read *and* write access to the device calendar. */
    CALENDAR,

    /** Posting notifications. Only gated on Android 13+. */
    POST_NOTIFICATIONS,

    READ_SMS,

    SEND_SMS,

    /**
     * Access to LAN addresses. Android 17+ blocks all traffic to local network addresses for apps
     * targeting SDK 37+ unless granted, which silently breaks self-hosted servers (Jan, Ollama,
     * LM Studio, ...) on the user's home network.
     */
    LOCAL_NETWORK,
}

/**
 * Multiplatform controller for a runtime permission request. Bridges the gap between tool
 * execution (suspend functions) and Compose permission launchers: [requestPermission] suspends
 * until the launcher set up by [SetupPermissionHandler] reports a result through
 * [onPermissionResult].
 */
class PermissionController(internal val permission: AppPermission) {
    private val _permissionRequested = MutableStateFlow(false)

    /** Emits true when a permission request is pending and should be launched. */
    val permissionRequested: StateFlow<Boolean> = _permissionRequested

    private val permissionResultFlow = MutableStateFlow<Boolean?>(null)

    /** True if the permission is already granted, or not gated on this platform/OS version. */
    fun hasPermission(): Boolean = platformHasPermission(permission)

    /**
     * Request the permission and suspend until the user responds.
     * Returns true if permission was granted, false otherwise.
     */
    suspend fun requestPermission(): Boolean {
        if (hasPermission()) return true
        // Platforms without a launcher would otherwise sit out the full timeout below waiting for
        // a result that can never arrive.
        if (!platformCanRequest(permission)) return false

        permissionResultFlow.value = null
        _permissionRequested.value = true

        val result = withTimeoutOrNull(60.seconds) {
            permissionResultFlow.first { it != null }
        }

        _permissionRequested.value = false
        return result ?: false
    }

    /** Called from Compose when the permission result is received. */
    fun onPermissionResult(granted: Boolean) {
        permissionResultFlow.value = granted
    }

    /**
     * Open the app's page in the system settings so the user can grant the permission manually
     * after denying the dialog. No-op on platforms without app permissions.
     */
    fun openAppSettings() = platformOpenAppSettings()
}

/** True if [permission] is granted, or isn't gated on this platform/OS version. */
internal expect fun platformHasPermission(permission: AppPermission): Boolean

/**
 * True if this platform can actually show a permission prompt for [permission]. False means
 * [PermissionController.requestPermission] resolves to the current grant state immediately
 * instead of waiting on a launcher that will never respond.
 */
internal expect fun platformCanRequest(permission: AppPermission): Boolean

internal expect fun platformOpenAppSettings()

/**
 * Sets up the permission launcher backing [controller].
 * This should be called at a high level in the composable hierarchy.
 */
@Composable
expect fun SetupPermissionHandler(controller: PermissionController)
