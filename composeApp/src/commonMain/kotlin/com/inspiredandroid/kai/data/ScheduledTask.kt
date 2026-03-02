package com.inspiredandroid.kai.data

import kotlinx.serialization.Serializable

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
)

@Serializable
enum class TaskStatus { PENDING, COMPLETED, CANCELLED }
