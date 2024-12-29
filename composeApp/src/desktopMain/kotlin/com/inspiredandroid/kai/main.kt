@file:Suppress("ktlint:standard:filename")
@file:OptIn(ExperimentalDesktopTarget::class)

package com.inspiredandroid.kai

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.logo
import nl.marc_apps.tts.TextToSpeechEngine
import nl.marc_apps.tts.experimental.ExperimentalDesktopTarget
import nl.marc_apps.tts.rememberTextToSpeechOrNull
import org.jetbrains.compose.resources.painterResource

fun main() = application {
    val textToSpeech = rememberTextToSpeechOrNull(TextToSpeechEngine.Google)

    Window(
        onCloseRequest = ::exitApplication,
        title = "Kai",
        icon = painterResource(Res.drawable.logo),
    ) {
        App(
            textToSpeech = textToSpeech,
        )
    }
}
