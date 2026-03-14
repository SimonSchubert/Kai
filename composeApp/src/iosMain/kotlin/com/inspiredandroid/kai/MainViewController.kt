package com.inspiredandroid.kai

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import androidx.navigation.compose.rememberNavController
import nl.marc_apps.tts.TextToSpeechEngine
import nl.marc_apps.tts.TextToSpeechInstance
import nl.marc_apps.tts.rememberTextToSpeechOrNull

fun MainViewController() = ComposeUIViewController {
    // Defer TTS initialization until after the first frame
    var ttsReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { ttsReady = true }
    val textToSpeech: TextToSpeechInstance? = if (ttsReady) {
        rememberTextToSpeechOrNull(TextToSpeechEngine.SystemDefault)
    } else {
        null
    }

    val navController = rememberNavController()
    App(
        navController = navController,
        textToSpeech = textToSpeech,
    )
}
