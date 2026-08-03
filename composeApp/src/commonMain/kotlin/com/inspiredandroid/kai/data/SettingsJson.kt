package com.inspiredandroid.kai.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Decodes [raw] into [T], falling back to [default] when the string is blank or unparseable.
 * The single decode implementation behind every settings-backed JSON store — a corrupt or
 * partially written blob costs the user that one collection, never a crash.
 */
internal fun <T> decodeJsonOr(
    raw: String,
    serializer: KSerializer<T>,
    label: String,
    json: Json = SharedJson,
    default: () -> T,
): T {
    if (raw.isBlank()) return default()
    return try {
        json.decodeFromString(serializer, raw)
    } catch (e: Exception) {
        println("$label: failed to decode persisted JSON: ${e.message}")
        default()
    }
}

/**
 * A list of [T] persisted as JSON in a single app-settings string.
 *
 * Callers keep their own retention policy — caps, ordering, prepend vs append — inside the
 * [update] lambda; this class only owns the read/decode/encode/write mechanics and the lock that
 * makes read-modify-write safe against concurrent mutators.
 */
class SettingsJsonList<T>(
    private val read: () -> String,
    private val write: (String) -> Unit,
    itemSerializer: KSerializer<T>,
    private val label: String,
    private val json: Json = SharedJson,
    /** Notified after every write, e.g. to mirror the list into a [kotlinx.coroutines.flow.StateFlow]. */
    private val onWrite: ((List<T>) -> Unit)? = null,
    /** Last chance to read a legacy shape that no longer decodes. Null means "give up, return empty". */
    private val recover: ((raw: String) -> List<T>?)? = null,
    /** Post-decode upgrade of legacy rows. A non-null result is persisted so later loads are no-ops. */
    private val migrate: ((List<T>) -> List<T>?)? = null,
) {
    private val serializer = ListSerializer(itemSerializer)
    private val mutex = Mutex()

    // Decoding runs on hot paths (system prompt assembly re-reads these lists per turn), so keep
    // the last result keyed on the exact raw string it came from. Any write — ours or another
    // process's — changes the string and invalidates the memo on its own. Held as one reference
    // so a concurrent reader can never pair a raw string with someone else's decoded list.
    private var memo: Pair<String, List<T>>? = null

    fun get(): List<T> {
        val raw = read()
        memo?.let { (memoRaw, memoValue) -> if (raw == memoRaw) return memoValue }

        val decoded = if (raw.isBlank()) {
            emptyList()
        } else {
            try {
                json.decodeFromString(serializer, raw)
            } catch (e: Exception) {
                recover?.invoke(raw) ?: run {
                    println("$label: failed to decode persisted JSON: ${e.message}")
                    emptyList()
                }
            }
        }

        val migrated = migrate?.invoke(decoded)
        if (migrated != null) {
            set(migrated)
            return migrated
        }
        memo = raw to decoded
        return decoded
    }

    fun set(list: List<T>) {
        val raw = json.encodeToString(serializer, list)
        write(raw)
        memo = raw to list
        onWrite?.invoke(list)
    }

    /** Reads, applies [transform] and persists the result under a lock. Returns the stored list. */
    suspend fun update(transform: (List<T>) -> List<T>): List<T> = mutex.withLock {
        val updated = transform(get())
        set(updated)
        updated
    }
}

/**
 * A single [T] persisted as JSON in one app-settings string, with a [default] for the
 * not-yet-written and corrupt cases.
 */
class SettingsJsonValue<T>(
    private val read: () -> String,
    private val write: (String) -> Unit,
    private val serializer: KSerializer<T>,
    private val label: String,
    private val json: Json = SharedJson,
    private val default: () -> T,
) {
    private val mutex = Mutex()

    fun get(): T = decodeJsonOr(read(), serializer, label, json, default)

    fun set(value: T) = write(json.encodeToString(serializer, value))

    /** Reads, applies [transform] and persists the result under a lock. Returns the stored value. */
    suspend fun update(transform: (T) -> T): T = mutex.withLock {
        val updated = transform(get())
        set(updated)
        updated
    }
}
