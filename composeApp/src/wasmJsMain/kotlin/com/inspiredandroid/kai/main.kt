@file:Suppress("ktlint:standard:filename")
@file:OptIn(ExperimentalBrowserHistoryApi::class)

package com.inspiredandroid.kai

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import androidx.navigation.ExperimentalBrowserHistoryApi
import androidx.navigation.bindToNavigation
import androidx.navigation.compose.rememberNavController
import kotlinx.browser.document
import kotlinx.browser.window
import nl.marc_apps.tts.TextToSpeechEngine
import nl.marc_apps.tts.rememberTextToSpeechOrNull

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val body = document.body ?: return
    ComposeViewport(body) {
        val textToSpeech = rememberTextToSpeechOrNull(TextToSpeechEngine.Google)
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
            window.bindToNavigation(navController) { entry ->
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
