package com.kainext.duo

/**
 * Result of probing the device for privileged-access backends. Android is the only
 * platform the app ships on now, but the interface lives in commonMain so Settings
 * and the data layer can report status without importing Android classes.
 */
data class DeviceControlStatus(
    val rootAvailable: Boolean = false,
    val shizukuInstalled: Boolean = false,
    val shizukuPermissionGranted: Boolean = false,
    /** True when the user has enabled Kai in system accessibility settings. */
    val accessibilityEnabled: Boolean = false,
    /** The backend that would actually be used for the next command: "root", "shizuku", "accessibility" or "none". */
    val effectiveMode: String = "none",
    /** Human-readable detail for the status row, e.g. why Shizuku is unavailable. */
    val detail: String? = null,
)

/**
 * Bridges the UI/data layer to the privileged shell implementation. The Android actual
 * wraps [com.kainext.duo.tools.PrivilegedShell].
 */
expect class DeviceControlManager() {
    /** Probes root/Shizuku/accessibility availability. Safe to call repeatedly. */
    suspend fun probe(): DeviceControlStatus

    /** Sends the Shizuku permission request to the Shizuku app. No-op for root. */
    fun requestShizukuPermission()

    /** Opens the system accessibility settings page so the user can enable Kai's service. */
    fun openAccessibilitySettings()

    /**
     * True when Kai's accessibility service is connected right now. Checked before any
     * accessibility-driven tap so the agent gets a clear error instead of a silent no-op.
     */
    fun isAccessibilityConnected(): Boolean

    /**
     * Asks root or Shizuku to keep the accessibility service bound. Android can unbind an
     * accessibility service under memory pressure; re-enabling the component from a privileged
     * shell brings it back without user interaction.
     */
    suspend fun keepAccessibilityAlive(): Boolean

    /** True when the OS grants "display over other apps". */
    fun canShowOverlay(): Boolean

    /** Opens the system page where the user grants the overlay permission. */
    fun openOverlaySettings()

    /** Shows the floating control window. No-op when the permission is missing. */
    fun showOverlay()

    /** Hides the floating control window. */
    fun hideOverlay()
}

expect fun createDeviceControlManager(): DeviceControlManager
