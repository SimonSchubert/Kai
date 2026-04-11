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
    val status: TaskStatus = TaskStatus.PENDING,
    val lastResult: String? = null,
    val consecutiveFailures: Int = 0,
) {
    val scheduledAt: Instant get() = Instant.fromEpochMilliseconds(scheduledAtEpochMs)
}

@Serializable
enum class TaskStatus { PENDING, COMPLETED }
