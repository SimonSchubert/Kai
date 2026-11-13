package com.kainext.duo.tools.reflection

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Describes a single attempt that ran past its own budget, without being a cancellation.
 *
 * Why not reuse `TimeoutCancellationException`: that type *is* a [CancellationException], so
 * handing it to a [shouldRetry] predicate would push callers towards "retry on cancellation",
 * which is precisely the bug this module exists to prevent. A plain exception keeps the two
 * categories distinguishable by type alone.
 */
class AttemptTimeoutException(
    val attempt: Int,
    val timeoutMs: Long,
) : Exception("attempt $attempt timed out after $timeoutMs ms")

/**
 * Generic retry template: runs [block] up to [maxAttempts] times under a per-attempt timeout and
 * an overall deadline, returning the first successful value or `null` once the budget is spent.
 *
 * ## Why there are two timeouts
 * A per-attempt limit bounds a single hung call, but on its own it is not enough: three attempts
 * that each finish just under the limit still stall the agent for three times what the user
 * expects. The total budget is the promise made to the user ("this will not take longer than
 * [totalTimeoutMs]"), and it is what keeps one slow failure from turning into a stuck
 * conversation. Whichever limit is reached first wins.
 *
 * ## Why [CancellationException] is rethrown
 * Cancellation is cooperative. Swallowing it — the classic `runCatching { ... }` mistake — makes
 * the caller believe work finished when the surrounding scope is already dead (the user pressed
 * stop, the screen was destroyed, the parent coroutine gave up). Retrying after that point runs
 * work the user explicitly cancelled, and a `null` result would be indistinguishable from an
 * honest failure. The catch block below therefore rethrows it before examining anything else, and
 * the per-attempt limit is expressed with `withTimeoutOrNull`, which reports *its own* timeout as
 * `null` while still propagating an outer cancellation as an exception — so a timeout never has
 * to be recognised by exception type.
 *
 * @param maxAttempts total number of tries; values below 1 are treated as 1.
 * @param attemptTimeoutMs budget for one invocation of [block].
 * @param totalTimeoutMs budget for all attempts combined.
 * @param shouldRetry consulted with the exception thrown by a failed attempt (or with
 *   [AttemptTimeoutException] when an attempt timed out). Returning `false` stops immediately.
 * @param block the work; receives the 1-based attempt number. It should not return `null` —
 *   `null` is this function's "gave up" signal, so wrap a legitimately nullable value in a
 *   holder before returning it.
 * @return the first successful value, or `null` if every attempt failed or the overall deadline
 *   passed. Those two cases are deliberately conflated; use [SelfCorrectingExecutor] when the
 *   caller must tell them apart.
 */
suspend fun <T> retryWithTimeout(
    maxAttempts: Int = 3,
    attemptTimeoutMs: Long = 30_000,
    totalTimeoutMs: Long = 90_000,
    shouldRetry: (Throwable) -> Boolean = { true },
    block: suspend (attempt: Int) -> T,
): T? {
    val attempts = maxAttempts.coerceAtLeast(1)
    var value: T? = null
    var succeeded = false

    withTimeoutOrNull(totalTimeoutMs) {
        var attempt = 1
        while (attempt <= attempts) {
            val step: AttemptStep<T> = try {
                val produced = withTimeoutOrNull(attemptTimeoutMs) { block(attempt) }
                if (produced == null) {
                    AttemptStep(AttemptTimeoutException(attempt, attemptTimeoutMs))
                } else {
                    AttemptStep(value = produced)
                }
            } catch (e: CancellationException) {
                // Outer cancellation, not a failure: never retry, never swallow.
                throw e
            } catch (e: Throwable) {
                AttemptStep(e)
            }

            if (step.error == null) {
                value = step.value
                succeeded = true
                return@withTimeoutOrNull
            }
            if (!shouldRetry(step.error)) return@withTimeoutOrNull
            attempt++
        }
    }

    return if (succeeded) value else null
}

/**
 * One attempt's result: either a value or an error, never both.
 *
 * A plain class rather than a sealed hierarchy because the loop only needs to branch on two
 * nullable fields; a `sealed` carrier would force an unchecked cast back to `T` on the success
 * path for no benefit.
 */
private class AttemptStep<T>(
    val error: Throwable? = null,
    val value: T? = null,
)
