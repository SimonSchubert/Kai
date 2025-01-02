package com.inspiredandroid.kai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.WindowCompat
import nl.marc_apps.tts.TextToSpeechEngine
import nl.marc_apps.tts.rememberTextToSpeechOrNull
import org.koin.android.ext.koin.androidContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = true

        setContent {
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
