package com.inspiredandroid.kai.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val MAX_SKILLS_JSON_SIZE = 2_000_000

@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
class SkillStore(private val appSettings: AppSettings) {

    private val json = SharedJson
    private val mutex = Mutex()
    private var cache: List<SkillEntry>? = null

    private fun loadSkills(): MutableList<SkillEntry> {
        cache?.let { return it.toMutableList() }
        return try {
            json.decodeFromString<List<SkillEntry>>(appSettings.getSkillsJson()).toMutableList()
        } catch (e: Exception) {
            println("SkillStore: failed to load skills: ${e.message}")
            mutableListOf()
        }
    }

    private fun saveSkills(skills: List<SkillEntry>) {
        val encoded = json.encodeToString(skills)
        if (encoded.length > MAX_SKILLS_JSON_SIZE) {
            println("SkillStore: WARNING — skills JSON is ${encoded.length} bytes, approaching platform limits")
        }
        appSettings.setSkillsJson(encoded)
        cache = skills.toList()
    }

    fun migrateIfNeeded() {
        if (appSettings.isSkillsMigrationComplete()) return
        val skills = loadSkills()
        val migrated = skills.map { if (it.schemaVersion == 0) it.copy(schemaVersion = 1) else it }
        if (migrated != skills) saveSkills(migrated)
        appSettings.setSkillsMigrationComplete()
    }

    suspend fun saveSkill(
        name: String,
        description: String,
        script: String,
        readme: String = "",
        dataJson: String = "",
    ): SkillEntry = mutex.withLock {
        val skills = loadSkills()
        val now = Clock.System.now().toEpochMilliseconds()
        val existing = skills.indexOfFirst { it.name == name }
        val entry = if (existing >= 0) {
            val updated = skills[existing].copy(
                description = description,
                script = script,
                readme = readme.ifEmpty { skills[existing].readme },
                dataJson = dataJson.ifEmpty { skills[existing].dataJson },
                updatedAtEpochMs = now,
            )
            skills[existing] = updated
            updated
        } else {
            val newEntry = SkillEntry(
                id = Uuid.random().toString(),
                name = name,
                description = description,
                script = script,
                readme = readme,
                dataJson = dataJson,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
            )
            skills.add(newEntry)
            newEntry
        }
        saveSkills(skills)
        entry
    }

    suspend fun updateData(name: String, dataJson: String): SkillEntry? = mutex.withLock {
        val skills = loadSkills()
        val index = skills.indexOfFirst { it.name == name }
        if (index < 0) return@withLock null
        val now = Clock.System.now().toEpochMilliseconds()
        val updated = skills[index].copy(dataJson = dataJson, updatedAtEpochMs = now)
        skills[index] = updated
        saveSkills(skills)
        updated
    }

    fun getSkill(name: String): SkillEntry? = loadSkills().find { it.name == name }

    fun getAllSkills(): List<SkillEntry> = loadSkills()

    suspend fun deleteSkill(name: String): Boolean = mutex.withLock {
        val skills = loadSkills()
        val removed = skills.removeAll { it.name == name }
        if (removed) saveSkills(skills)
        removed
    }

    suspend fun recordExecution(name: String, result: String): SkillEntry? = mutex.withLock {
        val skills = loadSkills()
        val index = skills.indexOfFirst { it.name == name }
        if (index < 0) return@withLock null
        val now = Clock.System.now().toEpochMilliseconds()
        val updated = skills[index].copy(
            executionCount = skills[index].executionCount + 1,
            lastResult = result.take(500),
            updatedAtEpochMs = now,
        )
        skills[index] = updated
        saveSkills(skills)
        updated
    }
}
