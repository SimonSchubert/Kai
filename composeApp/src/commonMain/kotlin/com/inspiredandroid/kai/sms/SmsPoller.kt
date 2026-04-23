package com.inspiredandroid.kai.sms

import com.inspiredandroid.kai.data.SmsStore
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class SmsPoller(
    private val smsStore: SmsStore,
    private val smsReader: SmsReader,
) {
    suspend fun poll() {
        if (!smsReader.isSupported()) return
        val syncState = smsStore.getSyncState()
        val attemptAt = Clock.System.now().toEpochMilliseconds()
        try {
            if (!smsReader.hasPermission()) {
                smsStore.updateSyncState(
                    syncState.copy(
                        lastAttemptEpochMs = attemptAt,
                        lastError = "Permission not granted",
                    ),
                )
                return
            }

            // First-time enable: seed lastSeenId to the current max and skip the read —
            // everything already in the inbox is "history" and shouldn't flood the
            // pending queue. Subsequent polls only pick up messages with _id > lastSeenId.
            if (syncState.lastSeenId == 0L) {
                smsStore.updateSyncState(
                    syncState.copy(
                        lastSyncEpochMs = attemptAt,
                        lastAttemptEpochMs = attemptAt,
                        lastError = null,
                        lastSeenId = smsReader.currentMaxInboxId(),
                    ),
                )
                return
            }

            val newMessages = smsReader.readInboxSince(syncState.lastSeenId, MAX_FETCH_PER_POLL)
            var updated = syncState.copy(
                lastSyncEpochMs = attemptAt,
                lastAttemptEpochMs = attemptAt,
                unreadCount = newMessages.count { !it.read },
                lastError = null,
            )
            if (newMessages.isNotEmpty()) {
                smsStore.addPending(newMessages)
                updated = updated.copy(lastSeenId = newMessages.maxOf { it.id })
            }
            smsStore.updateSyncState(updated)
        } catch (e: Exception) {
            smsStore.updateSyncState(
                syncState.copy(
                    lastAttemptEpochMs = attemptAt,
                    lastError = e.message ?: e::class.simpleName ?: "Poll failed",
                ),
            )
        }
    }

    companion object {
        const val MAX_FETCH_PER_POLL = 50
    }
}
