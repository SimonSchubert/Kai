package com.inspiredandroid.kai

import androidx.compose.ui.window.ComposeUIViewController
import androidx.navigation.compose.rememberNavController
import nl.marc_apps.tts.TextToSpeechEngine
import nl.marc_apps.tts.rememberTextToSpeechOrNull

fun MainViewController() = ComposeUIViewController {
    val navController = rememberNavController()
    val textToSpeech = rememberTextToSpeechOrNull(TextToSpeechEngine.SystemDefault)
    App(
        navController = navController,
        textToSpeech = textToSpeech,
    )
}
