package com.inspiredandroid.kai.data

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
