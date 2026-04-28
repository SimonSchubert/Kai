package com.inspiredandroid.kai.tools

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.inspiredandroid.kai.notifications.declaresNotificationListener
import org.koin.java.KoinJavaComponent.inject

private const val NOTIFICATION_LISTENER_FQN =
    "com.inspiredandroid.kai.notifications.KaiNotificationListenerService"

actual class NotificationListenerController actual constructor() {
    private val context: Context by inject(Context::class.java)
    private val supported: Boolean by lazy { context.declaresNotificationListener() }

    actual fun isSupported(): Boolean = supported

    actual fun isAccessGranted(): Boolean {
        if (!supported) return false
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return false
        return try {
            nm.isNotificationListenerAccessGranted(
                ComponentName(context, NOTIFICATION_LISTENER_FQN),
            )
        } catch (_: Exception) {
            false
        }
    }

    actual fun openAccessSettings() {
        if (!supported) return
        // Try the per-component variant on API 30+ first — drops the user directly on Kai's row.
        // Fall back to the global listener-settings list if that intent isn't resolvable.
        val intents = listOf(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
                putExtra(
                    Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    ComponentName(context, NOTIFICATION_LISTENER_FQN).flattenToString(),
                )
            },
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
        )
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (_: Exception) {
                continue
            }
        }
    }
}
