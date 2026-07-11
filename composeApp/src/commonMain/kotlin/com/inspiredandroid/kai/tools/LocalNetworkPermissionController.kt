package com.inspiredandroid.kai.tools

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.StateFlow

/**
 * Multiplatform controller for the local network permission. Android 17+ blocks all traffic to
 * LAN addresses for apps targeting SDK 37+ unless ACCESS_LOCAL_NETWORK is granted, which silently
 * breaks self-hosted servers (Jan, Ollama, LM Studio, ...) on the user's home network.
 * Other platforms don't gate local network access at runtime, so their actuals are no-ops.
 */
expect class LocalNetworkPermissionController() {
    /**
     * Flow that emits true when a permission request is pending and should be launched.
     */
    val permissionRequested: StateFlow<Boolean>

    /**
     * Check if local network access is already granted (or not gated on this platform/OS version).
     */
    fun hasPermission(): Boolean

    /**
     * Request local network access and suspend until the user responds.
     * Returns true if permission was granted, false otherwise.
     */
    suspend fun requestPermission(): Boolean

    /**
     * Called from Compose when the permission result is received.
     */
    fun onPermissionResult(granted: Boolean)

    /**
     * Open the app's page in the system settings so the user can grant the permission
     * manually after denying the dialog. No-op on platforms without app permissions.
     */
    fun openAppSettings()
}

/**
 * Composable that sets up the permission launcher for the local network permission.
 * This should be called at a high level in the composable hierarchy.
 */
@Composable
expect fun SetupLocalNetworkPermissionHandler(controller: LocalNetworkPermissionController)

/**
 * True if the URL points at a host on the local network — the traffic Android's local network
 * protection gates. Loopback stays false: it never leaves the device and isn't gated. Public DNS
 * names that happen to resolve to LAN addresses can't be detected without resolving them; those
 * still fail with a plain connection error.
 */
fun isLocalNetworkUrl(url: String): Boolean {
    if (url.isBlank()) return false
    val afterScheme = url.substringAfter("://")
    val authority = afterScheme.substringBefore("/").substringAfterLast("@").lowercase()
    val host = if (authority.startsWith("[")) {
        authority.substringAfter("[").substringBefore("]")
    } else {
        authority.substringBefore(":")
    }
    if (host.isEmpty()) return false

    // Loopback isn't gated by local network protection.
    if (host == "localhost" || host == "::1" || host.startsWith("127.")) return false

    // IPv6 link-local and unique-local addresses.
    if (host.contains(":")) {
        return host.startsWith("fe8") || host.startsWith("fe9") ||
            host.startsWith("fea") || host.startsWith("feb") ||
            host.startsWith("fc") || host.startsWith("fd")
    }

    // Private and link-local IPv4 ranges.
    val octets = host.split(".").mapNotNull { it.toIntOrNull() }
    if (octets.size == 4 && octets.all { it in 0..255 }) {
        val first = octets[0]
        val second = octets[1]
        return first == 10 ||
            (first == 172 && second in 16..31) ||
            (first == 192 && second == 168) ||
            (first == 169 && second == 254)
    }

    // mDNS names and bare hostnames resolve on the local network.
    return host.endsWith(".local") || !host.contains(".")
}
