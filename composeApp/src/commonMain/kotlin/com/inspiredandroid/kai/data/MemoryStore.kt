package com.inspiredandroid.kai.data

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Serializable
enum class MemoryCategory {
    GENERAL,
    LEARNING,
    ERROR,
    PREFERENCE,
}

@Immutable
@Serializable
data class MemoryEntry(
    val key: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
    val category: MemoryCategory = MemoryCategory.GENERAL,
    val hitCount: Int = 1,
    val source: String? = null,
)

@OptIn(ExperimentalTime::class)
class MemoryStore(appSettings: AppSettings) {

    private val memories = SettingsJsonList(
        read = appSettings::getMemoriesJson,
        write = appSettings::setMemoriesJson,
        itemSerializer = serializer<MemoryEntry>(),
        label = "MemoryStore",
    )

    suspend fun store(
        key: String,
        content: String,
        category: MemoryCategory = MemoryCategory.GENERAL,
        source: String? = null,
    ): MemoryEntry {
        val now = Clock.System.now().toEpochMilliseconds()
        lateinit var entry: MemoryEntry
        memories.update { current ->
            val existing = current.find { it.key == key }
            entry = existing?.copy(content = content, updatedAt = now, category = category, source = source ?: existing.source)
                ?: MemoryEntry(key = key, content = content, createdAt = now, updatedAt = now, category = category, source = source)
            val stored = entry
            if (existing != null) current.map { if (it.key == key) stored else it } else current + stored
        }
        return entry
    }

    suspend fun updateContent(key: String, content: String): MemoryEntry? = mutateEntry(key) { it.copy(content = content, updatedAt = Clock.System.now().toEpochMilliseconds()) }

    suspend fun reinforceMemory(key: String): MemoryEntry? = mutateEntry(key) { it.copy(hitCount = it.hitCount + 1, updatedAt = Clock.System.now().toEpochMilliseconds()) }

    /** Applies [transform] to the entry under [key], persisting only when it exists. */
    private suspend fun mutateEntry(key: String, transform: (MemoryEntry) -> MemoryEntry): MemoryEntry? {
        var updated: MemoryEntry? = null
        memories.update { current ->
            val existing = current.find { it.key == key } ?: return@update current
            val next = transform(existing)
            updated = next
            current.map { if (it.key == key) next else it }
        }
        return updated
    }

    fun getPromotionCandidates(minHits: Int = 5): List<MemoryEntry> = memories.get().filter { it.hitCount >= minHits }

    suspend fun forget(key: String): Boolean {
        var removed = false
        memories.update { current ->
            removed = current.any { it.key == key }
            if (removed) current.filterNot { it.key == key } else current
        }
        return removed
    }

    fun getAllMemories(): List<MemoryEntry> = memories.get()
}
