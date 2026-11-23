package com.inspiredandroid.kai.tools

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VoiceSessionCoordinatorTest {
    @Test
    fun onlyOneVoiceSessionCanOwnTheMicrophone() = runTest {
        val coordinator = VoiceSessionCoordinator()

        val first = coordinator.begin(
            mode = VoiceInteractionMode.DICTATION,
            target = VoiceInputTarget.ChatComposer,
        )
        val second = coordinator.begin(
            mode = VoiceInteractionMode.LIVE_CONVERSATION,
            target = VoiceInputTarget.ChatComposer,
        )

        assertNotNull(first)
        assertNull(second)
        assertEquals(first, coordinator.activeSession.value)
    }

    @Test
    fun endingSessionAllowsAnotherScreenToStart() = runTest {
        val coordinator = VoiceSessionCoordinator()
        val first = assertNotNull(
            coordinator.begin(
                VoiceInteractionMode.DICTATION,
                VoiceInputTarget.ChatComposer,
            ),
        )

        assertTrue(coordinator.end(first.id))

        val next = coordinator.begin(
            VoiceInteractionMode.SPOKEN_REQUEST,
            VoiceInputTarget.InteractiveField("customer-name"),
        )

        assertNotNull(next)
        assertEquals(VoiceInteractionMode.SPOKEN_REQUEST, next.mode)
        assertEquals(VoiceInputTarget.InteractiveField("customer-name"), next.target)
    }

    @Test
    fun staleCallbacksCannotChangeOrEndANewerSession() = runTest {
        val coordinator = VoiceSessionCoordinator()
        val first = assertNotNull(
            coordinator.begin(
                VoiceInteractionMode.DICTATION,
                VoiceInputTarget.ChatComposer,
            ),
        )
        assertTrue(coordinator.end(first.id))

        val second = assertNotNull(
            coordinator.begin(
                VoiceInteractionMode.DICTATION,
                VoiceInputTarget.TerminalCommandLine("terminal-1"),
            ),
        )

        assertFalse(coordinator.updatePhase(first.id, VoiceSessionPhase.SPEAKING))
        assertFalse(coordinator.end(first.id))
        assertEquals(second, coordinator.activeSession.value)
    }

    @Test
    fun terminalVoiceTargetIsOnlyAnInputDestination() = runTest {
        val coordinator = VoiceSessionCoordinator()
        val terminalTarget = VoiceInputTarget.TerminalCommandLine("terminal-42")

        val session = assertNotNull(
            coordinator.begin(VoiceInteractionMode.DICTATION, terminalTarget),
        )

        assertEquals(terminalTarget, session.target)
        assertEquals(VoiceSessionPhase.PREPARING, session.phase)
        // The coordinator exposes no terminal execution operation: routing and
        // command submission remain separate user-controlled actions.
    }

    @Test
    fun matchingSessionCanAdvanceThroughItsPhases() = runTest {
        val coordinator = VoiceSessionCoordinator()
        val session = assertNotNull(
            coordinator.begin(
                VoiceInteractionMode.LIVE_CONVERSATION,
                VoiceInputTarget.ChatComposer,
            ),
        )

        assertTrue(coordinator.updatePhase(session.id, VoiceSessionPhase.LISTENING))
        assertEquals(VoiceSessionPhase.LISTENING, coordinator.activeSession.value?.phase)
        assertTrue(coordinator.updatePhase(session.id, VoiceSessionPhase.SPEAKING))
        assertEquals(VoiceSessionPhase.SPEAKING, coordinator.activeSession.value?.phase)
    }
}
