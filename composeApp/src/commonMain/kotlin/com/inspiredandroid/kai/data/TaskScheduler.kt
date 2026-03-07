package com.inspiredandroid.kai.data

import com.inspiredandroid.kai.email.ImapClient
import com.inspiredandroid.kai.isEmailSupported
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class TaskScheduler(
    private val dataRepository: DataRepository,
    private val taskStore: TaskStore? = null,
    private val appSettings: AppSettings? = null,
    private val heartbeatManager: HeartbeatManager? = null,
    private val emailStore: EmailStore? = null,
    private val enabled: Boolean = true,
) {
    private companion object {
        const val POLL_INTERVAL_MS = 60_000L
    }

    private var activeJob: Job? = null

    /**
     * Starts the scheduler loop in the given scope.
     * [isLoading] is checked before executing a task to avoid concurrent API calls.
     * Safe to call multiple times — only one loop will run at a time.
     */
    fun start(scope: CoroutineScope, isLoading: () -> Boolean = { false }) {
        if (!enabled || taskStore == null || appSettings == null) return
        // If a loop is already running, don't start another
        if (activeJob?.isActive == true) return
        activeJob = scope.launch {
            while (true) {
                delay(POLL_INTERVAL_MS)
                if (!appSettings.isSchedulingEnabled()) continue

                val dueTasks = taskStore.getDueTasks()
                for (task in dueTasks) {
                    if (isLoading()) break

                    try {
                        dataRepository.ask(task.prompt, null)
                        handleTaskCompletion(task)
                    } catch (_: Exception) {
                        // Task failed — leave it pending so it retries next cycle
                    }
                }

                // Heartbeat check
                if (!isLoading() && heartbeatManager?.isHeartbeatDue() == true) {
                    try {
                        val heartbeatPrompt = heartbeatManager.buildHeartbeatPrompt()
                        val response = dataRepository.askSilently(heartbeatPrompt)
                        heartbeatManager.markHeartbeatExecuted()
                        heartbeatManager.recordHeartbeat(success = true)
                        if (response.trim() != "HEARTBEAT_OK") {
                            dataRepository.addAssistantMessage(response)
                        }
                    } catch (_: Exception) {
                        heartbeatManager.recordHeartbeat(success = false)
                    }
                }

                // Email polling
                if (!isLoading() && isEmailSupported && appSettings.isEmailEnabled() && emailStore != null) {
                    checkNewEmails(isLoading)
                }
            }
        }
    }

    private suspend fun checkNewEmails(isLoading: () -> Boolean) {
        if (emailStore == null || appSettings == null) return
        val pollMinutes = appSettings.getEmailPollIntervalMinutes()
        if (pollMinutes <= 0) return // 0 = never poll automatically
        val pollIntervalMs = pollMinutes * 60_000L

        for (account in emailStore.getAccounts()) {
            if (isLoading()) break
            val syncState = emailStore.getSyncState(account.id)
            val elapsed = Clock.System.now().toEpochMilliseconds() - syncState.lastSyncEpochMs
            if (elapsed < pollIntervalMs) continue

            try {
                val password = emailStore.getPassword(account.id)
                val imap = ImapClient(account.imapHost, account.imapPort)
                try {
                    imap.connect()
                    imap.login(account.username.ifEmpty { account.email }, password)
                    imap.selectInbox()
                    val unseenUids = imap.searchUnseen()
                    // Only process UIDs newer than what we've seen
                    val newUids = unseenUids.filter { it > syncState.lastSeenUid }

                    if (newUids.isNotEmpty()) {
                        val messages = imap.fetchHeaders(newUids.takeLast(10), account.id)

                        // Build triage prompt for AI to score relevance
                        val triagePrompt = buildString {
                            appendLine("[EMAIL_TRIAGE] New emails arrived for ${account.email}. Score each email's relevance from 1-5 based on the user's memories and preferences.")
                            appendLine("Only surface emails rated 4-5 by adding a brief notification message. For lower-rated emails, respond with exactly: EMAIL_TRIAGE_OK")
                            appendLine()
                            for (msg in messages) {
                                appendLine("- From: ${msg.from} | Subject: ${msg.subject} | Preview: ${msg.preview}")
                            }
                        }

                        if (!isLoading()) {
                            val response = dataRepository.askSilently(triagePrompt)
                            if (response.trim() != "EMAIL_TRIAGE_OK") {
                                dataRepository.addAssistantMessage(response)
                            }
                        }

                        // Update sync state
                        emailStore.updateSyncState(
                            syncState.copy(
                                lastSeenUid = newUids.max(),
                                lastSyncEpochMs = Clock.System.now().toEpochMilliseconds(),
                                unreadCount = unseenUids.size,
                            ),
                        )
                    } else {
                        emailStore.updateSyncState(
                            syncState.copy(
                                lastSyncEpochMs = Clock.System.now().toEpochMilliseconds(),
                                unreadCount = unseenUids.size,
                            ),
                        )
                    }
                } finally {
                    imap.logout()
                }
            } catch (_: Exception) {
                // Email check failed — skip and retry next cycle
            }
        }
    }

    private suspend fun handleTaskCompletion(task: ScheduledTask) {
        val now = Clock.System.now()
        if (task.cron != null) {
            // Recurring task — compute next execution time
            val nextExecution = try {
                CronExpression(task.cron).nextAfter(now)
            } catch (_: Exception) {
                null
            }
            if (nextExecution != null) {
                taskStore!!.updateTask(
                    task.copy(
                        scheduledAtEpochMs = nextExecution.toEpochMilliseconds(),
                        lastResult = "Executed at $now",
                    ),
                )
            } else {
                // Can't compute next time — mark completed
                taskStore!!.updateTask(
                    task.copy(
                        status = TaskStatus.COMPLETED,
                        lastResult = "Executed at $now (no next schedule)",
                    ),
                )
            }
        } else {
            // One-time task — mark completed
            taskStore!!.updateTask(
                task.copy(
                    status = TaskStatus.COMPLETED,
                    lastResult = "Executed at $now",
                ),
            )
        }
    }
}
