package com.kainext.duo.tools.reflection

import kotlinx.serialization.Serializable

/**
 * Default number of characters kept from `stdout` / `stderr` when building an LLM-facing error
 * summary. 800 holds a typical shell error plus a stack-trace head, and keeps three attempts
 * comfortably inside one reflection prompt.
 */
private const val DEFAULT_OUTPUT_LIMIT = 800

private val whitespaceRegex = Regex("\\s+")

/**
 * One execution attempt, shaped so a *language model* can decide what went wrong and how to fix
 * it.
 *
 * The type is deliberately backend-agnostic: the Android side fills it from `PrivilegedResult`,
 * but a UI-automation or HTTP attempt can use it just as well. That is also why it lives in
 * `commonMain`, where `android.*` / `java.*` do not exist — the module only sees strings and
 * numbers.
 *
 * Conversion at the Android call site (no Android import is needed here):
 * ```
 * val result = shell.execute(command)
 * ExecutionOutcome.fromRaw(result.stdout, result.stderr, result.exitCode, result.backend)
 * ```
 *
 * @property success mirrors `exitCode == 0` for process results, but is stored rather than
 *   computed so a caller can model a *logical* failure (a command that exits 0 but returns an
 *   error payload, or a UI tap that hit nothing) without inventing an exit code.
 * @property errorMessage failure text produced *outside* the process — spawn failure, timeout,
 *   exception inside the executor. Kept separate from [stderr] so the reflection prompt can
 *   distinguish "the command complained" from "we never managed to run it".
 * @property durationMs wall-clock duration of the attempt; 0 when unknown.
 */
@Serializable
data class ExecutionOutcome(
    val success: Boolean,
    val stdout: String = "",
    val stderr: String = "",
    val exitCode: Int = 0,
    val backend: String = "",
    val errorMessage: String? = null,
    val durationMs: Long = 0L,
) {

    /**
     * Error digest handed to the LLM instead of the raw streams.
     *
     * Why truncate: a failing `logcat` or `dumpsys` dump can be megabytes, and a prompt that
     * repeats three of those spends the context window (and money) on text the model cannot
     * use. Each stream is middle-truncated to [outputLimit] characters — head *and* tail are
     * kept because shell errors usually lead with the cause (`Permission denied`) and sometimes
     * trail with the offending path or line number.
     *
     * The first line is always the exit-code line, so the model can apply the retry policy
     * before reading any prose.
     */
    fun failureReason(outputLimit: Int = DEFAULT_OUTPUT_LIMIT): String {
        val header = buildString {
            append("exit code ").append(exitCode)
            append(" (").append(ExitCodes.explain(exitCode)).append(')')
            if (backend.isNotBlank()) append(" via ").append(backend)
            if (durationMs > 0L) append(", ").append(durationMs).append(" ms")
        }
        if (success) return header

        val sections = buildList {
            errorMessage?.takeIf { it.isNotBlank() }?.let {
                add("error: " + it.trim().middleTruncate(outputLimit))
            }
            if (stderr.isNotBlank()) add("stderr: " + stderr.trim().middleTruncate(outputLimit))
            if (stdout.isNotBlank()) add("stdout: " + stdout.trim().middleTruncate(outputLimit))
        }
        return (listOf(header) + sections).joinToString("\n")
    }

    /**
     * Single-line error for summaries shown to a human: the explicit [errorMessage], else the
     * first non-blank stderr line, else the exit-code explanation. Never contains newlines, so
     * it can be dropped into a chat bubble or a notification without further escaping.
     */
    fun shortError(maxChars: Int = 200): String {
        val primary = errorMessage?.takeIf { it.isNotBlank() }
            ?: stderr.lineSequence().firstOrNull { it.isNotBlank() }
            ?: ExitCodes.explain(exitCode)
        val oneLine = primary.replace(whitespaceRegex, " ").trim()
        return if (oneLine.length <= maxChars) oneLine else oneLine.take(maxChars) + "..."
    }

    companion object {

        /** Builds an outcome from raw process data; success is derived from [exitCode]. */
        fun fromRaw(
            stdout: String,
            stderr: String,
            exitCode: Int,
            backend: String,
            durationMs: Long = 0L,
        ): ExecutionOutcome = ExecutionOutcome(
            success = exitCode == ExitCodes.SUCCESS,
            stdout = stdout,
            stderr = stderr,
            exitCode = exitCode,
            backend = backend,
            durationMs = durationMs,
        )

        /** An attempt that produced no process, e.g. an in-process UI tap that worked. */
        fun successful(
            stdout: String = "",
            backend: String = "",
            durationMs: Long = 0L,
        ): ExecutionOutcome = ExecutionOutcome(
            success = true,
            stdout = stdout,
            exitCode = ExitCodes.SUCCESS,
            backend = backend,
            durationMs = durationMs,
        )

        /**
         * A failure that never reached a shell exit code: spawn error, timeout, thrown exception.
         *
         * Defaults to exit code 1 (generic) because that is the least misleading value for a
         * model that will look the code up in the retry policy — `-1` would claim a timeout even
         * when the real problem was, say, a bad argument.
         */
        fun failed(
            errorMessage: String,
            exitCode: Int = ExitCodes.GENERAL_ERROR,
            backend: String = "",
            stdout: String = "",
            stderr: String = "",
            durationMs: Long = 0L,
        ): ExecutionOutcome = ExecutionOutcome(
            success = false,
            stdout = stdout,
            stderr = stderr,
            exitCode = exitCode,
            backend = backend,
            errorMessage = errorMessage,
            durationMs = durationMs,
        )
    }
}

/**
 * Keeps the head and the tail of a long string, replacing the middle with an explicit
 * `... (truncated N chars) ...` marker.
 *
 * The marker states how much was dropped so the model can tell "the output was short" from "the
 * output was cut"; an apparently clean, short error message means something different depending
 * on which of those it is.
 */
internal fun String.middleTruncate(limit: Int): String {
    val safeLimit = limit.coerceAtLeast(0)
    if (length <= safeLimit) return this
    val head = safeLimit / 2
    val tail = safeLimit - head
    val dropped = length - safeLimit
    return take(head) + "\n... (truncated $dropped chars) ...\n" + takeLast(tail)
}
