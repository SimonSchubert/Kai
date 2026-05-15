package com.inspiredandroid.kai.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.serializer

class SmsStore(private val appSettings: AppSettings) {

    private val json = SharedJson
    private val mutex = Mutex()
    private val pendingQueue = PendingQueue<SmsMessage, Long>(
        readJson = appSettings::getSmsPendingJson,
        writeJson = appSettings::setSmsPendingJson,
        serializer = ListSerializer(serializer<SmsMessage>()),
        keyOf = { it.id },
    )

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

    fun getPending(): List<SmsMessage> = pendingQueue.get()

    suspend fun addPending(messages: List<SmsMessage>) = pendingQueue.add(messages)

    suspend fun removePending(messages: List<SmsMessage>) = pendingQueue.remove(messages)
}
