package com.inspiredandroid.kai.sms

import com.inspiredandroid.kai.data.SmsMessage

/**
 * Multiplatform SMS reader. Only the Android FOSS build returns real data — the
 * feature is gated by `READ_SMS` being declared in the merged manifest, which is
 * only the case for the `foss` product flavor. iOS, desktop, and wasm return
 * no-op stubs.
 */
expect class SmsReader() {
    /**
     * True when this build can ever read SMS at all — i.e. Android + `READ_SMS`
     * declared in the merged manifest. False on Play Store variant and non-Android.
     */
    fun isSupported(): Boolean

    /**
     * True when `isSupported()` and the user has granted `READ_SMS` at runtime.
     */
    fun hasPermission(): Boolean

    /**
     * Returns inbox messages with `_id > lastSeenId`, ordered by `_id` ascending,
     * capped at [limit]. Empty list if not supported or permission denied.
     */
    suspend fun readInboxSince(lastSeenId: Long, limit: Int): List<SmsMessage>

    /**
     * Fetch a single inbox message by `_id`. Null if not found or not supported.
     */
    suspend fun readById(id: Long): SmsMessage?

    /**
     * Full-text search across inbox address + body. Returns newest-first, capped
     * at [limit].
     */
    suspend fun search(query: String, limit: Int): List<SmsMessage>

    /**
     * The current maximum inbox `_id`. Used to seed [com.inspiredandroid.kai.data.SmsSyncState.lastSeenId]
     * on first enable so we don't dump the entire SMS history into pending.
     */
    suspend fun currentMaxInboxId(): Long
}
