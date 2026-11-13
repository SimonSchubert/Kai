package com.kainext.duo.overlay

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * What the floating window is currently doing, in the coarsest terms the UI needs.
 *
 * Kept separate from the free-form [OverlayState.statusText] so the window can colour-code the
 * state without parsing English prose, and so a future notification mirror can pick an icon.
 */
enum class OverlayPhase {
    /** Window is up but no task is running. */
    IDLE,

    /** A command or screen action is in flight. */
    WORKING,

    /** Waiting on the user — for example an approval or a follow-up message. */
    WAITING,

    /** The task finished successfully. Terminal until the next task. */
    DONE,

    /** The task failed or was stopped. Terminal until the next task. */
    FAILED,
}

/**
 * One line of the window's activity log.
 *
 * [isError] exists so the window can render a failed step in red without inspecting the text:
 * the agent's own messages are prose, and pattern-matching on them would be brittle.
 */
@Immutable
data class OverlayLogLine(
    val text: String,
    val isError: Boolean = false,
    val timestampMs: Long = 0L,
)

/**
 * Everything the floating window renders. Published as one immutable value so the window never
 * observes a half-updated state — it is recomposed from a single snapshot.
 */
@Immutable
data class OverlayState(
    val isVisible: Boolean = false,
    val phase: OverlayPhase = OverlayPhase.IDLE,
    /** Short description of the current step, e.g. "Tapping 'Sign in'". */
    val statusText: String = "",
    /** Recent activity, oldest first. Bounded by [OverlayController.MAX_LOG_LINES]. */
    val log: List<OverlayLogLine> = emptyList(),
    /** True while a task is running; gates the stop button. */
    val isRunning: Boolean = false,
) {
    companion object {
        val Hidden = OverlayState()
    }
}

/**
 * The channel between the agent loop and the floating window.
 *
 * Why this exists as a separate object rather than living on the chat view model: the window is
 * hosted by an Android `Service`, which has no reference to the Compose tree. A single shared
 * controller gives both sides a plain `StateFlow` to read and write, with no coupling to either
 * platform type. It is deliberately process-wide — there is exactly one floating window.
 */
object OverlayController {

    /** How many activity lines are kept. The window shows the last few; older ones scroll away. */
    const val MAX_LOG_LINES = 40

    private val _state = MutableStateFlow(OverlayState.Hidden)

    /** The state the window and the chat UI both observe. */
    val state: StateFlow<OverlayState> = _state.asStateFlow()

    /** Messages the user typed into the window, for the agent loop to consume. */
    private val _userMessages = MutableStateFlow<List<String>>(emptyList())

    /** Consumed by the chat layer; drained after each read so nothing is handled twice. */
    val userMessages: StateFlow<List<String>> = _userMessages.asStateFlow()

    private val _stopRequests = MutableStateFlow(0)

    /** Increments on every stop request. The agent loop watches this to cancel its job. */
    val stopRequests: StateFlow<Int> = _stopRequests.asStateFlow()

    /** Shows the window and resets it to a fresh idle state. */
    fun show() {
        _state.update { current -> current.copy(isVisible = true) }
    }

    /** Hides the window. Does not stop a running task; call [requestStop] for that. */
    fun hide() {
        _state.update { current -> current.copy(isVisible = false) }
    }

    /**
     * Announces that a task started. Clears the previous run's log so the window never mixes
     * two runs — a stale "Done" line above a fresh "Working" one reads as a contradiction.
     */
    fun startTask(statusText: String) {
        _state.update {
            it.copy(
                isVisible = true,
                phase = OverlayPhase.WORKING,
                statusText = statusText,
                log = emptyList(),
                isRunning = true,
            )
        }
    }

    /** Replaces the one-line status, e.g. when moving from one step to the next. */
    fun setStatus(statusText: String, phase: OverlayPhase = OverlayPhase.WORKING) {
        _state.update { it.copy(statusText = statusText, phase = phase) }
    }

    /** Appends an activity line, trimming the oldest entries past [MAX_LOG_LINES]. */
    fun log(text: String, isError: Boolean = false, timestampMs: Long = 0L) {
        if (text.isBlank()) return
        _state.update { current ->
            val line = OverlayLogLine(text.trim(), isError, timestampMs)
            val next = current.log + line
            current.copy(log = if (next.size > MAX_LOG_LINES) next.takeLast(MAX_LOG_LINES) else next)
        }
    }

    /** Marks the task finished and releases the stop button. */
    fun finishTask(success: Boolean, statusText: String) {
        _state.update {
            it.copy(
                phase = if (success) OverlayPhase.DONE else OverlayPhase.FAILED,
                statusText = statusText,
                isRunning = false,
            )
        }
    }

    /** Marks the task as waiting on the user, keeping it running. */
    fun waitForUser(statusText: String) {
        _state.update { it.copy(phase = OverlayPhase.WAITING, statusText = statusText) }
    }

    /** Records a message typed into the window. */
    fun submitUserMessage(message: String) {
        val trimmed = message.trim()
        if (trimmed.isEmpty()) return
        _userMessages.update { it + trimmed }
        log("You: $trimmed")
    }

    /** Returns the queued messages and clears them, so each is delivered exactly once. */
    fun drainUserMessages(): List<String> {
        val pending = _userMessages.value
        if (pending.isNotEmpty()) _userMessages.value = emptyList()
        return pending
    }

    /** Asks the agent loop to stop. Idempotent: repeated taps just bump the counter. */
    fun requestStop() {
        _stopRequests.update { it + 1 }
        _state.update { it.copy(statusText = "Stopping…", phase = OverlayPhase.WAITING) }
    }

    /** Resets everything. Called when the window is dismissed and no task is running. */
    fun reset() {
        _state.value = OverlayState.Hidden
        _userMessages.value = emptyList()
    }
}
