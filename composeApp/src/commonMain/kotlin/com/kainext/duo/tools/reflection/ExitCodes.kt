package com.kainext.duo.tools.reflection

/**
 * POSIX shell exit-code semantics, reduced to the only question the retry loop asks:
 * *"is another attempt — with a corrected command — plausibly able to succeed?"*
 *
 * ## Why "retryable" is defined that way
 * The retry loop never re-runs the same command verbatim: a retry always carries a correction
 * produced by the reflection step. So a code is worth retrying when a *different* command or a
 * different backend could work, not when the identical invocation would. That is why 127
 * (command not found) and 126 (not executable) are retryable — the fix is another path, another
 * binary or another backend — while 130 (SIGINT) is not: the user cancelled, so the intent is
 * withdrawn and any further attempt is wrong no matter how good the command is.
 *
 * Only two codes are non-retryable:
 * - `0` — nothing failed, there is nothing to retry.
 * - `130` (SIGINT) — the user or host pressed stop. Retrying burns tokens and re-runs commands
 *   against the user's explicit wish.
 *
 * Everything else, including unrecognised codes, is retryable: one extra attempt bounded by
 * `SelfCorrectingExecutor.maxAttempts` is cheaper than silently giving up on a transient
 * failure. Callers that disagree can disable the override in the executor.
 *
 * All explanations are English on purpose — they are fed to the model, not to the user. Any
 * user-visible text belongs to the UI layer and must go through the project's i18n resources.
 */
object ExitCodes {

    const val SUCCESS = 0

    /** Generic failure: bad argument, missing file, rejected operation. */
    const val GENERAL_ERROR = 1

    /** Shell builtin or command rejected its arguments (usage error). */
    const val MISUSE = 2

    /** Command was found but cannot be executed (usually permissions). */
    const val NOT_EXECUTABLE = 126

    /** Command was not found: typo, wrong PATH, or not installed on the device. */
    const val NOT_FOUND = 127

    /** Base of the `128 + signal number` range; 128 itself is a wrapper sentinel, not a signal. */
    const val SIGNAL_BASE = 128

    const val SIGHUP = 129
    const val SIGINT = 130
    const val SIGQUIT = 131
    const val SIGKILL = 137
    const val SIGTERM = 143

    /** Generic `255`, most commonly seen from `ssh` / `su` when auth or the remote side fails. */
    const val SSH_OR_SU_FAILURE = 255

    /**
     * Pseudo exit code for "no exit code at all": the process timed out, could not be spawned,
     * or the backend (root / Shizuku / app shell) was unavailable. This is what
     * `PrivilegedShell` returns when it has to give up before starting a process, so the
     * reflection prompt can tell "the command complained" apart from "we never ran it".
     */
    const val TIMEOUT_OR_SPAWN_FAILURE = -1

    /**
     * True when another attempt with a *corrected* command could plausibly succeed.
     *
     * `0` is false because nothing failed. `130` is false because the user cancelled. Everything
     * else — including unknown and negative codes — is true, per the policy documented on this
     * object.
     */
    fun isRetryable(exitCode: Int): Boolean = when (exitCode) {
        SUCCESS -> false
        SIGINT -> false
        else -> true
    }

    /** Model-readable explanation of [exitCode], including a concrete fix hint. */
    fun explain(exitCode: Int): String = when {
        exitCode == SUCCESS ->
            "success: the command finished and reported exit code 0"

        exitCode < 0 ->
            "no exit code was produced (exit $exitCode): the process either timed out or never " +
                "started. The privileged backend may be unavailable, so a retry should also " +
                "consider switching backend (root / Shizuku / app shell) or doing less work"

        exitCode == GENERAL_ERROR ->
            "general error (exit 1): the command ran but reported failure. Usually a bad " +
                "argument, a missing file, or an operation the shell refused. Read the error " +
                "output and adjust the command"

        exitCode == MISUSE ->
            "shell usage error (exit 2): a builtin or command rejected its arguments. The syntax " +
                "is wrong, so rewrite the command instead of retrying it verbatim"

        exitCode == NOT_EXECUTABLE ->
            "command exists but is not executable (exit 126): a permission problem. Try an " +
                "absolute path, `chmod +x`, or run it through a privileged backend (root/Shizuku)"

        exitCode == NOT_FOUND ->
            "command not found (exit 127): the shell could not locate the executable. The name " +
                "may be misspelled, PATH may not include its directory, or the binary is not " +
                "installed on this device. Consider `command -v` or an absolute path"

        exitCode == SSH_OR_SU_FAILURE ->
            "generic failure (exit 255): commonly reported by ssh/su when authentication or the " +
                "remote side fails. Verify that the privileged backend is actually available " +
                "before repeating the same idea"

        exitCode == SIGINT ->
            "interrupted by SIGINT (exit 130): the user or host cancelled the command. Do not " +
                "retry automatically — the request was withdrawn"

        exitCode == SIGKILL ->
            "killed by SIGKILL (exit 137): the process was killed without a chance to clean up. " +
                "On Android this is very often the low-memory killer, so repeating the same " +
                "heavy command will likely be killed again. Make the work smaller (narrower " +
                "query, pagination, fewer files) or free memory first"

        exitCode == SIGTERM ->
            "terminated by SIGTERM (exit 143): something asked the process to stop, typically a " +
                "timeout wrapper or the system reclaiming resources. One retry is reasonable, " +
                "but shorten the work"

        exitCode == SIGHUP ->
            "terminated by SIGHUP (exit 129): the terminal or parent process went away. The " +
                "command itself may be fine; retry in a form that does not depend on a terminal"

        exitCode == SIGQUIT ->
            "terminated by SIGQUIT (exit 131): the process was asked to dump state and quit. " +
                "Retrying the identical command rarely helps; change the approach"

        exitCode == SIGNAL_BASE ->
            "failure reported as exit 128: 128 without a signal number does not map to a real " +
                "signal, which usually means a wrapper script returned a sentinel value. Inspect " +
                "the stderr and try a different approach"

        isSignal(exitCode) -> {
            val signal = exitCode - SIGNAL_BASE
            "terminated by signal $signal (SIG${signalName(signal)}, exit $exitCode): the command " +
                "died inside the kernel, the dynamic linker or a supervisor. Retrying the " +
                "identical command rarely helps; change the approach or report the crash"
        }

        else ->
            "unrecognised exit code $exitCode: the shell or the backend reported something " +
                "non-standard. Treat it as a possible transient failure and allow one corrected " +
                "attempt"
    }

    /** Conventional name for [signal], or `UNKNOWN` for signals outside the common table. */
    fun signalName(signal: Int): String = when (signal) {
        1 -> "HUP"
        2 -> "INT"
        3 -> "QUIT"
        4 -> "ILL"
        6 -> "ABRT"
        8 -> "FPE"
        9 -> "KILL"
        11 -> "SEGV"
        13 -> "PIPE"
        14 -> "ALRM"
        15 -> "TERM"
        else -> "UNKNOWN"
    }

    /**
     * True when [exitCode] was produced by a signal rather than by the process itself.
     * Lets a caller tell "the command failed" apart from "the command was killed".
     */
    fun isSignal(exitCode: Int): Boolean = exitCode in (SIGNAL_BASE + 1)..255

    /**
     * Best-effort OOM hint. `137` means SIGKILL, which on Android is dominated by the low-memory
     * killer but can also be a manual `kill -9`, so this is a hint for prompt wording rather
     * than a fact.
     */
    fun isLikelyOutOfMemory(exitCode: Int): Boolean = exitCode == SIGKILL
}
