package com.inspiredandroid.kai.data

import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Serializable
data class MemoryEntry(
    val key: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@OptIn(ExperimentalTime::class)
class MemoryStore(private val appSettings: AppSettings) {

    private val json = SharedJson

    private fun loadMemories(): MutableList<MemoryEntry> = try {
        json.decodeFromString<List<MemoryEntry>>(appSettings.getMemoriesJson()).toMutableList()
    } catch (e: Exception) {
        mutableListOf()
    }

    private fun saveMemories(memories: List<MemoryEntry>) {
        appSettings.setMemoriesJson(json.encodeToString(memories))
    }

    fun store(key: String, content: String): MemoryEntry {
        val memories = loadMemories()
        val now = Clock.System.now().toEpochMilliseconds()
        val existing = memories.indexOfFirst { it.key == key }
        val entry = if (existing >= 0) {
            val updated = memories[existing].copy(content = content, updatedAt = now)
            memories[existing] = updated
            updated
        } else {
            val newEntry = MemoryEntry(key = key, content = content, createdAt = now, updatedAt = now)
            memories.add(newEntry)
            newEntry
        }
        saveMemories(memories)
        return entry
    }

    fun recall(query: String?): List<MemoryEntry> {
        val memories = loadMemories()
        if (query.isNullOrBlank()) return memories
        val lowerQuery = query.lowercase()
        return memories.filter {
            it.key.lowercase().contains(lowerQuery) || it.content.lowercase().contains(lowerQuery)
        }
    }

    fun forget(key: String): Boolean {
        val memories = loadMemories()
        val removed = memories.removeAll { it.key == key }
        if (removed) saveMemories(memories)
        return removed
    }

    fun getAllMemories(): List<MemoryEntry> = loadMemories()
}
