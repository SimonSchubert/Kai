package com.inspiredandroid.kai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.WindowCompat
import io.github.vinceglb.filekit.core.FileKit
import nl.marc_apps.tts.TextToSpeechEngine
import nl.marc_apps.tts.rememberTextToSpeechOrNull
import org.koin.android.ext.koin.androidContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        FileKit.init(this)

        setContent {
            val isDarkTheme = isSystemInDarkTheme()
            LaunchedEffect(isDarkTheme) {
                WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars =
                    !isDarkTheme
            }
            val textToSpeech = rememberTextToSpeechOrNull(TextToSpeechEngine.Google)
            App(
                textToSpeech = textToSpeech,
                koinApplication = {
                    androidContext(this@MainActivity)
                },
            )
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
