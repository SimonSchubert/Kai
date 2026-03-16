@file:Suppress("ktlint:standard:filename")
@file:OptIn(ExperimentalBrowserHistoryApi::class)

package com.inspiredandroid.kai

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import androidx.navigation.ExperimentalBrowserHistoryApi
import androidx.navigation.bindToBrowserNavigation
import androidx.navigation.bindToNavigation
import androidx.navigation.compose.rememberNavController
import kotlinx.browser.document
import kotlinx.browser.window
import nl.marc_apps.tts.TextToSpeechEngine
import nl.marc_apps.tts.rememberTextToSpeechOrNull

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val body = document.body ?: return
    document.getElementById("loader")?.remove()
    ComposeViewport(body) {
        // Defer TTS initialization until after the first frame
        var ttsReady by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { ttsReady = true }
        val textToSpeech = if (ttsReady) {
            rememberTextToSpeechOrNull(TextToSpeechEngine.Google)
        } else {
            null
        }
        val navController = rememberNavController()
        App(
            navController = navController,
            textToSpeech = textToSpeech,
        )
        LaunchedEffect(Unit) {
            val initRoute = window.location.hash.substringAfter('#', "")
            when {
                initRoute.endsWith("settings") -> {
                    navController.navigate(Settings)
                }

                else -> {
                    navController.navigate(Home)
                }
            }
            navController.bindToBrowserNavigation { entry ->
                val route = entry.destination.route.orEmpty()
                when {
                    route.startsWith(Settings.serializer().descriptor.serialName) -> {
                        "#settings"
                    }

                    else -> ""
                }
            }
        }
    }
}
