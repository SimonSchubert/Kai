@file:Suppress("ktlint:standard:filename")

package com.inspiredandroid.kai

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import nl.marc_apps.tts.TextToSpeechEngine
import nl.marc_apps.tts.rememberTextToSpeechOrNull

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(document.body!!) {
        val textToSpeech = rememberTextToSpeechOrNull(TextToSpeechEngine.Google)

        App(textToSpeech = textToSpeech)
    }
}
