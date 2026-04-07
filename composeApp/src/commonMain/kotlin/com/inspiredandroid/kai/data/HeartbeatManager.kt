package com.inspiredandroid.kai.data

import androidx.compose.runtime.Immutable
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Immutable
@Serializable
data class HeartbeatLogEntry(
    val timestampEpochMs: Long,
    val success: Boolean,
    val error: String? = null,
)

@Serializable
data class HeartbeatConfig(
    val enabled: Boolean = true,
    val intervalMinutes: Int = 30,
    val activeHoursStart: Int = 8,
    val activeHoursEnd: Int = 22,
    val lastHeartbeatEpochMs: Long = 0L,
    val heartbeatInstanceId: String? = null,
)

@OptIn(ExperimentalTime::class)
class HeartbeatManager(private val appSettings: AppSettings, private val memoryStore: MemoryStore, private val taskStore: TaskStore, private val emailStore: EmailStore? = null) {

    private val json = SharedJson

    fun getConfig(): HeartbeatConfig {
        val raw = appSettings.getHeartbeatConfigJson()
        if (raw.isEmpty()) return HeartbeatConfig()
        return try {
            json.decodeFromString<HeartbeatConfig>(raw)
        } catch (_: Exception) {
            HeartbeatConfig()
        }
    }

    fun saveConfig(config: HeartbeatConfig) {
        appSettings.setHeartbeatConfigJson(json.encodeToString(config))
    }

    fun isHeartbeatDue(): Boolean {
        val config = getConfig()
        if (!config.enabled) return false

        val now = Clock.System.now()
        val localNow = now.toLocalDateTime(TimeZone.currentSystemDefault())
        val currentHour = localNow.hour

        // Check active hours
        if (currentHour < config.activeHoursStart || currentHour >= config.activeHoursEnd) return false

        // Check elapsed time
        val elapsedMs = now.toEpochMilliseconds() - config.lastHeartbeatEpochMs
        val intervalMs = config.intervalMinutes * 60_000L
        return elapsedMs >= intervalMs
    }

    fun buildHeartbeatPrompt(recentResponses: List<String> = emptyList()): String = buildString {
        val customPrompt = appSettings.getHeartbeatPrompt()
        append(customPrompt.ifEmpty { DEFAULT_HEARTBEAT_PROMPT })
        append("\n")

        // Include recent heartbeat responses so the AI can track trends and avoid repeating itself
        if (recentResponses.isNotEmpty()) {
            append("\n## Previous Heartbeat Results\n")
            for ((i, response) in recentResponses.withIndex()) {
                append("${i + 1}. $response\n")
            }
        }

        // Append pending tasks
        val pendingTasks = taskStore.getPendingTasks()
        if (pendingTasks.isNotEmpty()) {
            append("\n## Pending Tasks\n")
            for (t in pendingTasks) {
                append("- **${t.description}** (id: ${t.id}, scheduled: ${Instant.fromEpochMilliseconds(t.scheduledAtEpochMs)})")
                if (t.cron != null) append(" [cron: ${t.cron}]")
                append("\n")
            }
        }

        // Append email status
        if (emailStore != null && appSettings.isEmailEnabled()) {
            val accounts = emailStore.getAccounts()
            if (accounts.isNotEmpty()) {
                append("\n## Email Status\n")
                for (account in accounts) {
                    val syncState = emailStore.getSyncState(account.id)
                    append("- **${account.email}**: ${syncState.unreadCount} unread")
                    if (syncState.lastSyncEpochMs > 0) {
                        append(" (last sync: ${Instant.fromEpochMilliseconds(syncState.lastSyncEpochMs)})")
                    }
                    append("\n")
                }
            }
        }

        // Append promotion candidates
        val candidates = memoryStore.getPromotionCandidates()
        if (candidates.isNotEmpty()) {
            append("\n## Promotion Candidates\n")
            append("These memories have been reinforced ${candidates.first().hitCount}+ times. ")
            append("Consider using the promote_learning tool to add well-established patterns to your soul/system prompt:\n")
            for (entry in candidates) {
                append("- **${entry.key}** (hits: ${entry.hitCount}, category: ${entry.category}): ${entry.content}\n")
            }
        }
    }

    fun recordHeartbeat(success: Boolean, error: String? = null) {
        val entry = HeartbeatLogEntry(
            timestampEpochMs = Clock.System.now().toEpochMilliseconds(),
            success = success,
            error = error,
        )
        val log = getHeartbeatLog().toMutableList()
        log.add(0, entry)
        val trimmed = log.take(MAX_LOG_ENTRIES)
        appSettings.setHeartbeatLogJson(json.encodeToString(trimmed))
    }

    fun getHeartbeatLog(): List<HeartbeatLogEntry> {
        val raw = appSettings.getHeartbeatLogJson()
        if (raw.isEmpty()) return emptyList()
        return try {
            json.decodeFromString<List<HeartbeatLogEntry>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val MAX_LOG_ENTRIES = 5
        const val DEFAULT_HEARTBEAT_PROMPT =
            "[HEARTBEAT] This is an automatic self-check. Review your memories and pending tasks. " +
                "If everything looks good and nothing needs attention, respond with exactly: HEARTBEAT_OK\n" +
                "If something needs attention (stale memories, due tasks, user follow-ups), address it."
    }

    fun markHeartbeatExecuted(config: HeartbeatConfig = getConfig()) {
        saveConfig(config.copy(lastHeartbeatEpochMs = Clock.System.now().toEpochMilliseconds()))
    }
}
