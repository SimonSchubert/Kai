
package com.inspiredandroid.kai

import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.inspiredandroid.kai.data.RemoteDataRepository
import com.inspiredandroid.kai.network.Requests
import com.inspiredandroid.kai.ui.chat.ChatScreen
import com.inspiredandroid.kai.ui.chat.ChatViewModel
import com.inspiredandroid.kai.ui.settings.SettingsScreen
import com.inspiredandroid.kai.ui.settings.SettingsViewModel
import com.russhwolf.settings.Settings
import nl.marc_apps.tts.TextToSpeechInstance
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.KoinApplication
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

enum class Screen(val route: String) {
    Home("Home"),
    Settings("Settings"),
}

val appModule = module {
    single<Settings> {
        Settings()
    }
    single<Requests> {
        Requests(get())
    }
    single<RemoteDataRepository> {
        RemoteDataRepository(get(), get())
    }
    viewModel { SettingsViewModel(get(), get()) }
    viewModel { ChatViewModel(get()) }
}

@Composable
@Preview
fun App(
    textToSpeech: TextToSpeechInstance? = null,
) {
    KoinApplication(
        application = {
            modules(appModule)
        },
    ) {
        Theme {
            val navController = rememberNavController()

            NavHost(navController, startDestination = Screen.Home.route) {
                composable(Screen.Home.route) {
                    ChatScreen(
                        textToSpeech = textToSpeech,
                        onNavigateToSettings = {
                            navController.navigate(Screen.Settings.route)
                        },
                    )
                }
                composable(Screen.Settings.route) {
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
