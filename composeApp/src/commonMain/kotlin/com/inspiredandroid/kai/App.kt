
package com.inspiredandroid.kai

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.inspiredandroid.kai.ui.DarkColorScheme
import com.inspiredandroid.kai.ui.LightColorScheme
import com.inspiredandroid.kai.ui.Theme
import com.inspiredandroid.kai.ui.chat.ChatScreen
import com.inspiredandroid.kai.ui.settings.SettingsScreen
import com.inspiredandroid.kai.data.AppSettings
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.marc_apps.tts.TextToSpeechInstance
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.core.KoinApplication

@Serializable
@SerialName("home")
object Home

@Serializable
@SerialName("settings")
object Settings

@Composable
@Preview
fun App(
    navController: NavHostController,
    colorScheme: ColorScheme = if (isSystemInDarkTheme()) {
        DarkColorScheme
    } else {
        LightColorScheme
    },
    textToSpeech: TextToSpeechInstance? = null,
    koinApplication: (KoinApplication.() -> Unit)? = null,
    onAppOpens: ((Int) -> Unit)? = null,
) {
    KoinApplication(
        application = {
            koinApplication?.let { koinApplication() }
            modules(appModule)
        },
    ) {
        // Track app opens after Koin is initialized
        onAppOpens?.let { callback ->
            val appSettings = koinInject<AppSettings>()
            LaunchedEffect(Unit) {
                callback(appSettings.trackAppOpen())
            }
        }

        Theme(colorScheme = colorScheme) {
            NavHost(navController, startDestination = Home) {
                composable<Home> {
                    ChatScreen(
                        textToSpeech = textToSpeech,
                        onNavigateToSettings = {
                            navController.navigate(Settings)
                        },
                    )
                }
                composable<Settings> {
                    SettingsScreen(
                        onNavigateBack = {
                            navController.navigateUp()
                        },
                    )
                }
            }
        }
    }
}
