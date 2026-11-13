package com.kainext.duo

import android.content.Intent
import android.provider.Settings
import android.util.Log
import com.kainext.duo.data.AppSettings
import com.kainext.duo.overlay.KaiOverlayService
import com.kainext.duo.tools.PrivilegedMode
import com.kainext.duo.tools.PrivilegedShell
import org.koin.java.KoinJavaComponent

actual class DeviceControlManager actual constructor() {

    private val shell = PrivilegedShell()

    actual suspend fun probe(): DeviceControlStatus {
        val root = shell.isRootAvailable(forceRefresh = true)
        val binderAlive = shell.isShizukuBinderAlive()
        val shizukuGranted = shell.isShizukuPermissionGranted()
        val configured = try {
            val settings = KoinJavaComponent.get(AppSettings::class.java) as AppSettings
            PrivilegedMode.fromId(settings.getPrivilegedMode())
        } catch (_: Throwable) {
            PrivilegedMode.AUTO
        }
        val accessibility = isAccessibilityConnected()
        val effective = when {
            configured == PrivilegedMode.ROOT && root -> "root"
            configured == PrivilegedMode.ROOT -> "none"
            configured == PrivilegedMode.SHIZUKU && shizukuGranted -> "shizuku"
            configured == PrivilegedMode.SHIZUKU -> "none"
            configured == PrivilegedMode.ACCESSIBILITY && accessibility -> "accessibility"
            configured == PrivilegedMode.ACCESSIBILITY -> "none"
            // Auto prefers root, then Shizuku, then accessibility: the first two give a full
            // shell, which can do strictly more than the accessibility service alone.
            root -> "root"
            shizukuGranted -> "shizuku"
            accessibility -> "accessibility"
            else -> "none"
        }
        val detail = when {
            effective == "root" -> "Root shell available (su)"
            effective == "shizuku" -> "Shizuku connected (adb shell privileges)"
            effective == "accessibility" -> "Accessibility service connected (screen reading + gestures)"
            !binderAlive && !root && !accessibility ->
                "Grant root, connect Shizuku, or enable the accessibility service"
            binderAlive && !shizukuGranted && !accessibility ->
                "Shizuku is running — grant permission in the Shizuku app, or enable accessibility"
            else -> null
        }
        return DeviceControlStatus(
            rootAvailable = root,
            shizukuInstalled = binderAlive,
            shizukuPermissionGranted = shizukuGranted,
            accessibilityEnabled = accessibility,
            effectiveMode = effective,
            detail = detail,
        )
    }

    actual fun requestShizukuPermission() {
        shell.requestShizukuPermission()
    }

    actual fun openAccessibilitySettings() {
        try {
            val context: android.content.Context = appContext()
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Throwable) {
            Log.w("DeviceControl", "could not open accessibility settings", e)
        }
    }

    actual fun isAccessibilityConnected(): Boolean = KaiAccessibilityService.current() != null

    /**
     * Re-enables the accessibility component through root or Shizuku.
     *
     * Android unbinds an accessibility service when the app process is killed or the system is
     * under memory pressure, and the user has to notice and toggle it back on. A privileged
     * shell can flip the component setting back on, which is the "keep alive" half of this mode.
     * Returns true when the component reports enabled afterwards.
     */
    actual fun canShowOverlay(): Boolean = KaiOverlayService.canDrawOverlays(appContext())

    actual fun openOverlaySettings() {
        KaiOverlayService.openOverlaySettings(appContext())
    }

    actual fun showOverlay() {
        val context = appContext()
        if (!KaiOverlayService.canDrawOverlays(context)) {
            Log.w("DeviceControl", "overlay permission not granted")
            return
        }
        KaiOverlayService.start(context)
    }

    actual fun hideOverlay() {
        KaiOverlayService.stop(appContext())
    }

    /**
     * The application context, resolved from Koin rather than stored as a field: this class is
     * created by Koin and holding a Context in a field would outlive the activity it came from.
     */
    private fun appContext(): android.content.Context =
        KoinJavaComponent.get(android.content.Context::class.java)

    actual suspend fun keepAccessibilityAlive(): Boolean {
        val component = "com.kainext.duo/com.kainext.duo.KaiAccessibilityService"
        if (isAccessibilityConnected()) return true
        val result = shell.execute("settings get secure enabled_accessibility_services")
        val current = result.stdout.trim()
        if (current.contains("com.kainext.duo/")) return true
        val updated = when {
            current.isEmpty() || current == "null" -> component
            else -> "$current:$component"
        }
        shell.execute("settings put secure enabled_accessibility_services ${shell.shellQuote(updated)}")
        shell.execute("settings put secure accessibility_enabled 1")
        return isAccessibilityConnected()
    }
}

actual fun createDeviceControlManager(): DeviceControlManager = DeviceControlManager()
