package com.inspiredandroid.kai.data

import androidx.compose.runtime.Immutable
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
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
class HeartbeatManager(
    private val appSettings: AppSettings,
    private val memoryStore: MemoryStore,
    private val taskStore: TaskStore,
    private val emailStore: EmailStore? = null,
) {

    private val config = SettingsJsonValue(
        read = appSettings::getHeartbeatConfigJson,
        write = appSettings::setHeartbeatConfigJson,
        serializer = serializer<HeartbeatConfig>(),
        label = "HeartbeatManager.config",
        default = { HeartbeatConfig() },
    )
    private val log = SettingsJsonList(
        read = appSettings::getHeartbeatLogJson,
        write = appSettings::setHeartbeatLogJson,
        itemSerializer = serializer<HeartbeatLogEntry>(),
        label = "HeartbeatManager.log",
    )

    fun getConfig(): HeartbeatConfig = config.get()

    fun saveConfig(config: HeartbeatConfig) = this.config.set(config)

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
        pendingSms: List<SmsMessage> = emptyList(),
        pendingNotifications: List<NotificationRecord> = emptyList(),
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
        val smsEnabled = appSettings.isSmsEnabled()
        val heartbeatSms: List<HeartbeatPendingSms> = if (smsEnabled) {
            pendingSms.map { msg ->
                HeartbeatPendingSms(
                    id = msg.id,
                    from = msg.address,
                    preview = msg.preview,
                )
            }
        } else {
            emptyList()
        }
        val notificationsEnabled = appSettings.isNotificationsEnabled()
        // Cap the heartbeat snapshot so a flurry of group-chat pings can't blow out the
        // prompt. Newest first; the rest stay in the pending queue and will surface on
        // subsequent heartbeats (or via `check_notifications` on demand).
        val heartbeatNotifications: List<HeartbeatPendingNotification> = if (notificationsEnabled) {
            pendingNotifications
                .sortedByDescending { it.postedAtEpochMs }
                .take(MAX_NOTIFICATIONS_IN_PROMPT)
                .map { record ->
                    HeartbeatPendingNotification(
                        id = record.id,
                        appLabel = record.appLabel,
                        title = record.title,
                        preview = record.preview,
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
            pendingSms = heartbeatSms,
            pendingNotifications = heartbeatNotifications,
            promotionCandidates = promotionCandidates,
        )
    }

    suspend fun recordHeartbeat(success: Boolean, error: String? = null) {
        val entry = HeartbeatLogEntry(
            timestampEpochMs = Clock.System.now().toEpochMilliseconds(),
            success = success,
            error = error,
        )
        log.update { (listOf(entry) + it).take(MAX_LOG_ENTRIES) }
    }

    fun getHeartbeatLog(): List<HeartbeatLogEntry> = log.get()

    companion object {
        private const val MAX_LOG_ENTRIES = 5
        private const val MAX_NOTIFICATIONS_IN_PROMPT = 20
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
