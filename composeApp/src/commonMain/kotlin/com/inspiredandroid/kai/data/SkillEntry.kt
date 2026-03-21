package com.inspiredandroid.kai.data

import kotlinx.serialization.Serializable

@Serializable
data class SkillEntry(
    val id: String,
    val name: String,
    val description: String,
    val script: String,
    val readme: String = "",
    val dataJson: String = "",
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val executionCount: Int = 0,
    val lastResult: String? = null,
    val schemaVersion: Int = 1,
)

data class SkillExecutionResult(
    val success: Boolean,
    val output: String,
    val error: String? = null,
    val timedOut: Boolean = false,
)
