package com.inspiredandroid.kai.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Persistence for notifications captured by [com.inspiredandroid.kai.notifications.KaiNotificationListenerService].
 *
 * Two collections:
 * - **Pending queue** — capped FIFO that fills as the listener fires and gets snapshotted
 *   into the heartbeat prompt, then drained. Mirrors [SmsStore].
 * - **Store** — broader rolling history backing [com.inspiredandroid.kai.notifications.NotificationReader],
 *   bounded by per-app cap and age cap.
 *
 * Per-app gating is handled by the system Notification Access "Apps" picker — if the
 * user unchecks an app there, `onNotificationPosted` is never called for that package
 * in the first place, so this store never sees it.
 */
@OptIn(ExperimentalTime::class)
class NotificationStore(private val appSettings: AppSettings) {

    private val json = SharedJson
    private val mutex = Mutex()

    fun getPending(): List<NotificationRecord> {
        val raw = appSettings.getNotificationsPendingJson()
        if (raw.isEmpty()) return emptyList()
        return try {
            json.decodeFromString<List<NotificationRecord>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun addPending(record: NotificationRecord) = mutex.withLock {
        val merged = (getPending() + record).takeLast(MAX_PENDING)
        appSettings.setNotificationsPendingJson(json.encodeToString(merged))
    }

    suspend fun removePending(records: List<NotificationRecord>) = mutex.withLock {
        if (records.isEmpty()) return@withLock
        val ids = records.map { it.id }.toSet()
        val remaining = getPending().filterNot { it.id in ids }
        appSettings.setNotificationsPendingJson(json.encodeToString(remaining))
    }

    suspend fun clearPending() = mutex.withLock {
        appSettings.setNotificationsPendingJson("")
    }

    fun getStore(): List<NotificationRecord> {
        val raw = appSettings.getNotificationsStoreJson()
        if (raw.isEmpty()) return emptyList()
        return try {
            json.decodeFromString<List<NotificationRecord>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun addRecord(record: NotificationRecord) = mutex.withLock {
        val now = Clock.System.now().toEpochMilliseconds()
        val ageCutoff = now - MAX_AGE_MS
        val current = getStore()
            .filter { it.postedAtEpochMs >= ageCutoff }
        // Per-package cap: keep newest [MAX_PER_PACKAGE] for each package after adding the new record.
        val updated = (current + record)
            .groupBy { it.packageName }
            .flatMap { (_, msgs) -> msgs.sortedByDescending { it.postedAtEpochMs }.take(MAX_PER_PACKAGE) }
            .sortedByDescending { it.postedAtEpochMs }
        appSettings.setNotificationsStoreJson(json.encodeToString(updated))
    }

    /** Drops records older than 24h or beyond the per-package cap. Called after each heartbeat. */
    suspend fun sweep() = mutex.withLock {
        val now = Clock.System.now().toEpochMilliseconds()
        val ageCutoff = now - MAX_AGE_MS
        val swept = getStore()
            .filter { it.postedAtEpochMs >= ageCutoff }
            .groupBy { it.packageName }
            .flatMap { (_, msgs) -> msgs.sortedByDescending { it.postedAtEpochMs }.take(MAX_PER_PACKAGE) }
            .sortedByDescending { it.postedAtEpochMs }
        appSettings.setNotificationsStoreJson(json.encodeToString(swept))
    }

    fun getSyncState(): NotificationSyncState {
        val raw = appSettings.getNotificationsSyncStateJson()
        if (raw.isEmpty()) return NotificationSyncState()
        return try {
            json.decodeFromString<NotificationSyncState>(raw)
        } catch (_: Exception) {
            NotificationSyncState()
        }
    }

    suspend fun updateSyncState(state: NotificationSyncState) = mutex.withLock {
        appSettings.setNotificationsSyncStateJson(json.encodeToString(state))
    }

    companion object {
        private const val MAX_PENDING = 100
        private const val MAX_PER_PACKAGE = 50
        private const val MAX_AGE_MS = 24L * 60L * 60L * 1000L
    }
}
