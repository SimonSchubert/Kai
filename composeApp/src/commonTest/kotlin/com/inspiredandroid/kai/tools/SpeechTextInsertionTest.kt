package com.inspiredandroid.kai.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpeechTextInsertionTest {
    @Test
    fun appendsToAnEmptyField() {
        assertEquals("Hello Kai", appendSpeechTranscript("", " Hello Kai "))
    }

    @Test
    fun appendsWithOneSeparatorWhenNeeded() {
        assertEquals("Existing text spoken words", appendSpeechTranscript("Existing text", "spoken words"))
    }

    @Test
    fun preservesExistingTrailingWhitespace() {
        assertEquals("Existing text\nspoken words", appendSpeechTranscript("Existing text\n", "spoken words"))
    }

    @Test
    fun ignoresBlankRecognition() {
        assertEquals("Keep this", appendSpeechTranscript("Keep this", "   "))
    }

    @Test
    fun vadPaddingPreservesOnsetsAndMergesNearbySpeechRegions() {
        val padded = paddedSpeechRanges(
            regions = listOf(SpeechSampleRange(1_000, 2_000), SpeechSampleRange(2_500, 3_000)),
            totalSamples = 10_000,
            preRollSamples = 500,
            postRollSamples = 500,
        )
        assertEquals(listOf(SpeechSampleRange(500, 3_500)), padded)
    }

    @Test
    fun vadRegionsAreClampedToCapturedAudio() {
        val padded = paddedSpeechRanges(
            regions = listOf(SpeechSampleRange(100, 900)),
            totalSamples = 1_000,
            preRollSamples = 500,
            postRollSamples = 500,
        )
        assertEquals(listOf(SpeechSampleRange(0, 1_000)), padded)
    }

    @Test
    fun concatenatesOnlyDetectedSpeechRegionsInOrder() {
        val samples = floatArrayOf(0f, 1f, 2f, 3f, 4f, 5f)
        val output = concatenateSpeechRegions(
            samples,
            listOf(SpeechSampleRange(1, 3), SpeechSampleRange(4, 6)),
        )
        assertEquals(listOf(1f, 2f, 4f, 5f), output.toList())
    }

    @Test
    fun noVadRegionsProduceNoSpeechSamples() {
        val padded = paddedSpeechRanges(
            regions = emptyList(),
            totalSamples = 1_000,
            preRollSamples = 500,
            postRollSamples = 500,
        )
        assertTrue(padded.isEmpty())
        assertEquals(0, concatenateSpeechRegions(FloatArray(1_000), padded).size)
    }

    @Test
    fun automaticSendingRequiresExplicitOptIn() {
        assertFalse(shouldAutoSendSpeechTranscript(false, "", false, false))
        assertTrue(shouldAutoSendSpeechTranscript(true, "", false, false))
    }

    @Test
    fun automaticSendingFallsBackToReviewWhenDraftExists() {
        assertFalse(shouldAutoSendSpeechTranscript(true, "Existing draft", false, false))
        assertFalse(shouldAutoSendSpeechTranscript(true, "   ", true, false))
        assertFalse(shouldAutoSendSpeechTranscript(true, "", false, true))
    }
}

