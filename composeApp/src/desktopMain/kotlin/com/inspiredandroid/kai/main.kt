@file:Suppress("ktlint:standard:filename")
@file:OptIn(ExperimentalDesktopTarget::class)

package com.inspiredandroid.kai

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.navigation.compose.rememberNavController
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.logo
import nl.marc_apps.tts.TextToSpeechEngine
import nl.marc_apps.tts.TextToSpeechInstance
import nl.marc_apps.tts.experimental.ExperimentalDesktopTarget
import nl.marc_apps.tts.rememberTextToSpeechOrNull
import org.jetbrains.compose.resources.painterResource

fun main() {
    System.setProperty("apple.awt.application.appearance", "system")
    // Help AWT/Skiko pick up HiDPI on Linux/Wayland (Sway, GNOME fractional scaling).
    // Without this, the JVM ignores GDK_SCALE and renders at 1× on a hi-res monitor.
    if (System.getProperty("sun.java2d.uiScale.enabled") == null) {
        System.setProperty("sun.java2d.uiScale.enabled", "true")
    }
    if (System.getProperty("sun.java2d.uiScale") == null) {
        System.setProperty("sun.java2d.uiScale", "auto")
    }
    application {
        val windowState = rememberWindowState(size = DpSize(1280.dp, 800.dp))
        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "Kai 9000",
            icon = painterResource(Res.drawable.logo),
        ) {
            // Defer TTS initialization until after the first frame
            var ttsReady by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { ttsReady = true }
            val textToSpeech: TextToSpeechInstance? = if (ttsReady) {
                rememberTextToSpeechOrNull(TextToSpeechEngine.Google)
            } else {
                null
            }

            val navController = rememberNavController()
            App(
                navController = navController,
                textToSpeech = textToSpeech,
            )
        }
    }
}
