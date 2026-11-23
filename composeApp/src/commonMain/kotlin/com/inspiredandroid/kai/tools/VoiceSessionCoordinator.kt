package com.inspiredandroid.kai.tools

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The three user-facing ways to speak with Kai.
 *
 * These modes share one microphone session at a time, but have different
 * completion and response behavior at the UI / conversation layer.
 */
enum class VoiceInteractionMode {
    /** Insert recognized words into the selected input; the user reviews and submits them. */
    DICTATION,

    /** Capture one spoken request, then let Kai process and answer it. */
    SPOKEN_REQUEST,

    /** Keep a conversational voice session open for alternating speech and responses. */
    LIVE_CONVERSATION,
}

/** A destination for recognized speech. A destination is explicit so text cannot
 * accidentally be delivered to whichever input happens to be visible later.
 */
sealed interface VoiceInputTarget {
    /** The regular chat message composer. */
    data object ChatComposer : VoiceInputTarget

    /** A specific generated Interactive UI text field. */
    data class InteractiveField(val fieldId: String) : VoiceInputTarget

    /**
     * The command line of a specific terminal session.
     *
     * Voice routing may populate this input, but this target never authorizes
     * execution. The user must explicitly submit / execute the command.
     */
    data class TerminalCommandLine(val terminalSessionId: String) : VoiceInputTarget
}

/** Lifecycle phase for the one active voice session. */
enum class VoiceSessionPhase {
    PREPARING,
    LISTENING,
    PROCESSING,
    SPEAKING,
}

/** Immutable snapshot exposed to UI and platform adapters. */
data class VoiceSession(
    val id: Long,
    val mode: VoiceInteractionMode,
    val target: VoiceInputTarget,
    val phase: VoiceSessionPhase,
)

/**
 * Coordinates ownership of Kai's microphone across screens and platforms.
 *
 * This first-stage coordinator deliberately owns no microphone, recognizer,
 * player, or UI. Platform adapters remain responsible for those resources.
 * It guarantees a single active session and rejects late callbacks belonging
 * to an older session, preventing one screen from mutating a newer session.
 */
class VoiceSessionCoordinator {
    private val mutex = Mutex()
    private val mutableActiveSession = MutableStateFlow<VoiceSession?>(null)
    private var nextSessionId = 1L

    val activeSession: StateFlow<VoiceSession?> = mutableActiveSession.asStateFlow()

    /**
     * Starts a session if no other voice session is active.
     *
     * Returns null when another screen already owns the microphone. Callers
     * should surface that state instead of silently starting a second capture.
     */
    suspend fun begin(
        mode: VoiceInteractionMode,
        target: VoiceInputTarget,
    ): VoiceSession? = mutex.withLock {
        if (mutableActiveSession.value != null) {
            return@withLock null
        }

        val session = VoiceSession(
            id = nextSessionId++,
            mode = mode,
            target = target,
            phase = VoiceSessionPhase.PREPARING,
        )
        mutableActiveSession.value = session
        session
    }

    /**
     * Advances the matching active session.
     *
     * A delayed callback from an ended session cannot update a newer session.
     */
    suspend fun updatePhase(sessionId: Long, phase: VoiceSessionPhase): Boolean = mutex.withLock {
        val current = mutableActiveSession.value
        if (current == null || current.id != sessionId) {
            return@withLock false
        }

        mutableActiveSession.value = current.copy(phase = phase)
        true
    }

    /** Ends only the matching session; stale stop/cancel callbacks are ignored. */
    suspend fun end(sessionId: Long): Boolean = mutex.withLock {
        val current = mutableActiveSession.value
        if (current == null || current.id != sessionId) {
            return@withLock false
        }

        mutableActiveSession.value = null
        true
    }
}
