package com.inspiredandroid.kai.data

@Suppress("UNUSED_PARAMETER")
actual class SkillExecutor actual constructor(skillStore: SkillStore) {
    actual suspend fun execute(
        script: String,
        input: String?,
        dataJson: String?,
        timeoutMs: Long,
    ): SkillExecutionResult {
        return SkillExecutionResult(
            success = false,
            output = "",
            error = "Script execution is not supported on Web",
        )
    }

    actual suspend fun validate(script: String): String? = null
}
