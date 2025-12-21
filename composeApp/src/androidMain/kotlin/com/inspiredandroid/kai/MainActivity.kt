package com.inspiredandroid.kai

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.WindowCompat
import androidx.navigation.compose.rememberNavController
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.init
import nl.marc_apps.tts.TextToSpeechEngine
import nl.marc_apps.tts.rememberTextToSpeechOrNull
import org.koin.android.ext.koin.androidContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        FileKit.init(this)

        val dynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        setContent {
            val isDarkTheme = isSystemInDarkTheme()
            LaunchedEffect(isDarkTheme) {
                WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars =
                    !isDarkTheme
            }
            val colorScheme = when {
                dynamicColor && isDarkTheme -> dynamicDarkColorScheme(LocalContext.current)
                dynamicColor && !isDarkTheme -> dynamicLightColorScheme(LocalContext.current)
                isDarkTheme -> DarkColorScheme
                else -> LightColorScheme
            }
            val navController = rememberNavController()
            val textToSpeech = rememberTextToSpeechOrNull(TextToSpeechEngine.Google)
            App(
                navController = navController,
                colorScheme = colorScheme,
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
    App(navController = rememberNavController())
}
