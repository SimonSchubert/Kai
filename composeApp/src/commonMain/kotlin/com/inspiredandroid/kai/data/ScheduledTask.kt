@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.inspiredandroid.kai.data

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Immutable
@Serializable
data class ScheduledTask(
    val id: String,
    val description: String,
    val prompt: String,
    val scheduledAtEpochMs: Long,
    val createdAtEpochMs: Long,
    val cron: String? = null,
    /**
     * How this task is dispatched. Defaulted to [TaskTrigger.TIME] for backward
     * compatibility — legacy tasks decoded from storage without this field land on TIME,
     * and [TaskStore.loadTasks] upgrades them to CRON when `cron != null`.
     */
    val trigger: TaskTrigger = TaskTrigger.TIME,
    val status: TaskStatus = TaskStatus.PENDING,
    val lastResult: String? = null,
    val consecutiveFailures: Int = 0,
    /**
     * Most recent execution outcomes, newest first, capped to the last few runs. Surfaced
     * in the task details sheet so users can see why a task isn't producing notifications.
     * Empty for HEARTBEAT-trigger tasks — those share the heartbeat-wide log instead.
     */
    val recentExecutions: List<TaskExecutionLogEntry> = emptyList(),
) {
    val scheduledAt: Instant get() = Instant.fromEpochMilliseconds(scheduledAtEpochMs)
}

@Immutable
@Serializable
data class TaskExecutionLogEntry(
    val timestampEpochMs: Long,
    val success: Boolean,
    val message: String? = null,
)

@Serializable
enum class TaskStatus { PENDING, COMPLETED }

/**
 * How a scheduled task is dispatched.
 *
 * - [TIME] — fires once at [ScheduledTask.scheduledAtEpochMs], transitions to COMPLETED.
 * - [CRON] — recurring; [ScheduledTask.cron] is the spec, [scheduledAtEpochMs] holds the
 *   next computed fire time. Stays PENDING; the scheduler advances it after each run.
 * - [HEARTBEAT] — a standing addition to every heartbeat self-check. Not picked up by
 *   the time-based poll loop; instead its prompt is appended to the heartbeat message
 *   under `## Heartbeat Additions`. Stays PENDING until cancelled. `scheduledAtEpochMs`
 *   and `cron` are ignored.
 */
@Serializable
enum class TaskTrigger { TIME, CRON, HEARTBEAT }
