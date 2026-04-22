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

    fun buildHeartbeatPrompt(
        recentResponses: List<String> = emptyList(),
        pendingEmails: List<EmailMessage> = emptyList(),
    ): String {
        val customPrompt = appSettings.getHeartbeatPrompt()
        val tasksSplit = taskStore.getPendingTasksPartitioned()
        val pendingTasks = tasksSplit.scheduled
        val heartbeatAdditions = tasksSplit.heartbeatAdditions
        val emailEnabled = emailStore != null && appSettings.isEmailEnabled()
        val accounts = if (emailEnabled) emailStore.getAccounts() else emptyList()
        val emailAccounts: List<EmailAccountSummary> = accounts.map { account ->
            val syncState = emailStore!!.getSyncState(account.id)
            EmailAccountSummary(
                email = account.email,
                unreadCount = syncState.unreadCount,
                lastSyncEpochMs = syncState.lastSyncEpochMs,
                lastError = syncState.lastError,
            )
        }
        val accountEmailById = accounts.associate { it.id to it.email }
        val heartbeatPending: List<HeartbeatPendingEmail> = if (emailEnabled) {
            pendingEmails.map { msg ->
                HeartbeatPendingEmail(
                    accountEmail = accountEmailById[msg.accountId] ?: msg.accountId,
                    from = msg.from,
                    subject = msg.subject,
                    preview = msg.preview,
                )
            }
        } else {
            emptyList()
        }
        val promotionCandidates = memoryStore.getPromotionCandidates().map { entry ->
            HeartbeatPromotionCandidate(
                key = entry.key,
                hitCount = entry.hitCount,
                category = entry.category,
                content = entry.content,
            )
        }
        return buildHeartbeatPrompt(
            customOrDefaultPrompt = customPrompt.ifEmpty { DEFAULT_HEARTBEAT_PROMPT },
            heartbeatAdditions = heartbeatAdditions,
            recentResponses = recentResponses,
            pendingTasks = pendingTasks,
            emailAccounts = emailAccounts,
            pendingEmails = heartbeatPending,
            promotionCandidates = promotionCandidates,
        )
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
                "If something needs attention (stale memories, due tasks, user follow-ups), address it.\n" +
                "You cannot enable, disable, or reschedule heartbeat — the schedule is a user setting."
    }

    fun markHeartbeatExecuted(config: HeartbeatConfig = getConfig()) {
        saveConfig(config.copy(lastHeartbeatEpochMs = Clock.System.now().toEpochMilliseconds()))
    }
}
