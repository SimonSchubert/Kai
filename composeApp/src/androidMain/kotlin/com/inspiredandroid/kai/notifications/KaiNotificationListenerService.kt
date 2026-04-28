package com.inspiredandroid.kai.notifications

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.inspiredandroid.kai.data.AppSettings
import com.inspiredandroid.kai.data.NotificationRecord
import com.inspiredandroid.kai.data.NotificationStore
import com.inspiredandroid.kai.data.NotificationSyncState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Captures notifications posted to the system tray and forwards them into
 * [NotificationStore]. Registered only in the FOSS flavor manifest.
 *
 * Per-app gating is handled by the system Notification Access "Apps" picker — if
 * the user unchecks an app there, this callback is never fired for that package
 * in the first place, so we don't need an app-side ignore list.
 *
 * Remaining filters applied here:
 * - User toggle off → drop. Lets the user pause capture without revoking access.
 * - Hard-blocked package (Kai itself, system UI) → drop, avoids feedback loops.
 * - Ongoing/foreground-service notification (media controls, downloads) → drop,
 *   these are sticky UI affordances, not events.
 * - `VISIBILITY_SECRET` → drop, the user signalled the post should not appear on
 *   lockscreens or external surfaces.
 *
 * Everything that survives is recorded both in the pending queue (for the next
 * heartbeat snapshot) and the rolling store (for tool lookups).
 */
@OptIn(ExperimentalTime::class)
class KaiNotificationListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store: NotificationStore by inject(NotificationStore::class.java)
    private val appSettings: AppSettings by inject(AppSettings::class.java)

    override fun onListenerConnected() {
        super.onListenerConnected()
        scope.launch {
            store.updateSyncState(
                NotificationSyncState(
                    listenerBound = true,
                    lastBoundEpochMs = Clock.System.now().toEpochMilliseconds(),
                    lastError = null,
                ),
            )
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        scope.launch {
            store.updateSyncState(
                store.getSyncState().copy(listenerBound = false),
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (!appSettings.isNotificationsEnabled()) return
        val pkg = sbn.packageName ?: return
        if (pkg in HARD_BLOCKED_PACKAGES || pkg == applicationContext.packageName) return

        val notification = sbn.notification ?: return
        if (notification.flags and Notification.FLAG_ONGOING_EVENT != 0) return
        if (notification.flags and Notification.FLAG_FOREGROUND_SERVICE != 0) return
        if (notification.visibility == Notification.VISIBILITY_SECRET) return

        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().trim()
        val text = (
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)
            )?.toString().orEmpty().trim()
        val subtext = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty().trim()
        if (title.isBlank() && text.isBlank()) return

        val appLabel = lookupAppLabel(pkg)
        val record = NotificationRecord(
            id = sbn.key ?: "$pkg|${sbn.id}|${sbn.postTime}",
            packageName = pkg,
            appLabel = appLabel,
            title = title,
            text = text,
            subtext = subtext,
            postedAtEpochMs = sbn.postTime,
            isOngoing = false,
            category = notification.category.orEmpty(),
            preview = text.take(NotificationRecord.PREVIEW_CHARS),
        )

        scope.launch {
            store.addRecord(record)
            store.addPending(record)
        }
    }

    private fun lookupAppLabel(packageName: String): String = try {
        val pm = applicationContext.packageManager
        val info = pm.getApplicationInfo(packageName, 0)
        pm.getApplicationLabel(info).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        packageName
    } catch (_: Exception) {
        packageName
    }

    companion object {
        private val HARD_BLOCKED_PACKAGES = setOf(
            "android",
            "com.android.systemui",
        )
    }
}
