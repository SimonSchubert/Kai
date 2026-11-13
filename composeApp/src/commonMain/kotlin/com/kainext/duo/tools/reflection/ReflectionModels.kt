package com.kainext.duo.tools.reflection

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What the executor hands to the `attempt` lambda for one try.
 *
 * [command] is `null` on the first attempt, meaning "you produce the command from [intent]
 * yourself" (see `SelfCorrectingExecutor.run` for why the module does not spend an extra LLM
 * round-trip on generating the first command). On later attempts it carries the command the
 * reflection step proposed, or stays `null` when reflection had no concrete fix — in which case
 * the lambda is expected to re-plan from [intent] while taking [previousFailure] into account.
 *
 * [previousFailure] is the failure digest of the immediately preceding attempt, optionally
 * followed by the reflection reasoning. It is `null` on the first attempt.
 */
@Serializable
data class AttemptInput(
    val command: String?,
    val intent: String,
    val attemptNumber: Int,
    val previousFailure: String? = null,
)

/**
 * One recorded attempt, as fed back to the model.
 *
 * [explanation] is the pre-computed, truncated [ExecutionOutcome.failureReason] rather than the
 * raw streams, so the prompt stays small and every attempt is described consistently.
 */
@Serializable
data class AttemptRecord(
    val attemptNumber: Int,
    val command: String?,
    val outcome: ExecutionOutcome,
    val explanation: String,
)

/**
 * Everything the reflection step needs: the goal, the history, and the hard attempt budget.
 *
 * The history is complete (not just the last failure) because "failed the same way three times"
 * and "failed three different ways" call for different decisions — the former means the whole
 * approach is wrong, the latter means it is being refined.
 */
@Serializable
data class ReflectionRequest(
    val intent: String,
    val attempts: List<AttemptRecord>,
    val maxAttempts: Int,
)

/**
 * The model's verdict on how to continue.
 *
 * Field names are annotated with snake_case because the prompt asks for snake_case JSON; keeping
 * the wire names on the class means [ReflectionPrompts.parseReflectionResponse] and any direct
 * serialization agree instead of drifting.
 *
 * [shouldRetry] defaults to `true` so a truncated response that still parses does not silently
 * abort a recoverable run, while [correctedCommand] defaults to `null`: a retry with no proposed
 * fix is only allowed as a re-plan (see [AttemptInput.command]), never as a blind re-run of the
 * previous command.
 */
@Serializable
data class ReflectionResponse(
    @SerialName("corrected_command") val correctedCommand: String? = null,
    @SerialName("reasoning") val reasoning: String = "",
    @SerialName("should_retry") val shouldRetry: Boolean = true,
    @SerialName("give_up_reason") val giveUpReason: String? = null,
)

/**
 * Outcome of a whole self-correcting run.
 *
 * [attempts] is always the full history — even on success — because the UI needs it to explain
 * "it failed twice, then worked" rather than pretending the first try succeeded, and the agent
 * loop needs it to learn that the original command was wrong.
 *
 * [timedOut] distinguishes "we ran out of the overall time budget" from "we used up the attempt
 * budget": both are failures, but the first may be retried later by the caller while the second
 * should change the approach.
 */
@Serializable
data class ReflectionResult(
    val success: Boolean,
    val attempts: List<AttemptRecord>,
    val finalOutcome: ExecutionOutcome?,
    val giveUpReason: String? = null,
    val timedOut: Boolean = false,
) {

    /** How many attempts were actually made. */
    val attemptCount: Int get() = attempts.size

    /**
     * Compact, English, human-readable summary for a chat bubble or log line.
     *
     * Deliberately short: the full history is available in [attempts] and the UI should offer it
     * on demand rather than dumping three stack traces into the conversation. The wording avoids
     * blaming the user — it reports what was tried and why it stopped.
     */
    fun toUserSummary(): String {
        if (success) {
            val n = attempts.size
            val suffix = if (n <= 1) "on the first attempt" else "after $n attempts"
            val command = attempts.lastOrNull()?.command
            return if (command.isNullOrBlank()) {
                "Succeeded $suffix."
            } else {
                "Succeeded $suffix: $command"
            }
        }

        val tried = when (attempts.size) {
            0 -> "No attempt could be made"
            1 -> "Tried once"
            else -> "Tried ${attempts.size} times"
        }
        val reason = giveUpReason?.takeIf { it.isNotBlank() }
        val lastError = finalOutcome?.shortError()

        return buildString {
            append(tried).append(if (attempts.size <= 1) ". It failed." else ". All failed.")
            if (timedOut) append(" Ran out of the overall time budget.")
            when {
                lastError != null -> append(" Last error: ").append(lastError)
                reason != null -> append(" Reason: ").append(reason)
            }
            if (lastError != null && reason != null) append(" (Reason: ").append(reason).append(')')
        }
    }
}
