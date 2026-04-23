package com.inspiredandroid.kai.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SmsStore(private val appSettings: AppSettings) {

    private val json = SharedJson
    private val mutex = Mutex()

    fun getSyncState(): SmsSyncState {
        val raw = appSettings.getSmsSyncStateJson()
        if (raw.isEmpty()) return SmsSyncState()
        return try {
            json.decodeFromString<SmsSyncState>(raw)
        } catch (_: Exception) {
            SmsSyncState()
        }
    }

    suspend fun updateSyncState(state: SmsSyncState) = mutex.withLock {
        appSettings.setSmsSyncStateJson(json.encodeToString(state))
    }

    fun getPending(): List<SmsMessage> {
        val raw = appSettings.getSmsPendingJson()
        if (raw.isEmpty()) return emptyList()
        return try {
            json.decodeFromString<List<SmsMessage>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun addPending(messages: List<SmsMessage>) = mutex.withLock {
        if (messages.isEmpty()) return@withLock
        // Capped FIFO so a disabled or slow heartbeat can't let the buffer grow unbounded.
        val merged = (getPending() + messages).takeLast(MAX_PENDING)
        appSettings.setSmsPendingJson(json.encodeToString(merged))
    }

    suspend fun removePending(messages: List<SmsMessage>) = mutex.withLock {
        if (messages.isEmpty()) return@withLock
        val ids = messages.map { it.id }.toSet()
        val remaining = getPending().filterNot { it.id in ids }
        appSettings.setSmsPendingJson(json.encodeToString(remaining))
    }

    companion object {
        private const val MAX_PENDING = 100
    }
}
