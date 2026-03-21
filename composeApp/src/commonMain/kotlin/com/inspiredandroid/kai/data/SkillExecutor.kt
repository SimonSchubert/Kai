package com.inspiredandroid.kai.data

expect class SkillExecutor(skillStore: SkillStore) {
    suspend fun execute(
        script: String,
        input: String?,
        dataJson: String? = null,
        timeoutMs: Long = 120_000L,
    ): SkillExecutionResult

    suspend fun validate(script: String): String?
}
