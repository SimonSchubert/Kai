@file:OptIn(ExperimentalVoiceApi::class)

package com.inspiredandroid.kai.screenshots

import kotlinx.coroutines.flow.StateFlow
import nl.marc_apps.tts.TextToSpeechInstance
import nl.marc_apps.tts.Voice
import nl.marc_apps.tts.experimental.ExperimentalVoiceApi

class FakeTextToSpeechInstance : TextToSpeechInstance {
    override val isSynthesizing: StateFlow<Boolean>
        get() = TODO("Not yet implemented")
    override val isWarmingUp: StateFlow<Boolean>
        get() = TODO("Not yet implemented")
    override var volume: Int
        get() = TODO("Not yet implemented")
        set(value) {}
    override var isMuted: Boolean
        get() = TODO("Not yet implemented")
        set(value) {}
    override var pitch: Float
        get() = TODO("Not yet implemented")
        set(value) {}
    override var rate: Float
        get() = TODO("Not yet implemented")
        set(value) {}
    override val language: String
        get() = TODO("Not yet implemented")

    @ExperimentalVoiceApi
    override var currentVoice: Voice?
        get() = TODO("Not yet implemented")
        set(value) {}

    @ExperimentalVoiceApi
    override val voices: Sequence<Voice>
        get() = TODO("Not yet implemented")

    override fun enqueue(text: String, clearQueue: Boolean) {
        TODO("Not yet implemented")
    }

    override fun say(
        text: String,
        clearQueue: Boolean,
        callback: (Result<Unit>) -> Unit,
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun say(
        text: String,
        clearQueue: Boolean,
        clearQueueOnCancellation: Boolean,
    ) {
        TODO("Not yet implemented")
    }

    override fun plusAssign(text: String) {
        TODO("Not yet implemented")
    }

    override fun stop() {
        TODO("Not yet implemented")
    }

    override fun close() {
        TODO("Not yet implemented")
    }
}
