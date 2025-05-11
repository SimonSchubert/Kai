
package com.inspiredandroid.kai

import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.inspiredandroid.kai.data.RemoteDataRepository
import com.inspiredandroid.kai.network.Requests
import com.inspiredandroid.kai.ui.chat.ChatScreen
import com.inspiredandroid.kai.ui.chat.ChatViewModel
import com.inspiredandroid.kai.ui.settings.SettingsScreen
import com.inspiredandroid.kai.ui.settings.SettingsViewModel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.marc_apps.tts.TextToSpeechInstance
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.KoinApplication
import org.koin.core.KoinApplication
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

@Serializable
@SerialName("home")
object Home

@Serializable
@SerialName("settings")
object Settings

val appModule = module {
    single<com.russhwolf.settings.Settings> {
        com.russhwolf.settings.Settings()
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
    navController: NavHostController,
    textToSpeech: TextToSpeechInstance? = null,
    koinApplication: (KoinApplication.() -> Unit)? = null,
) {
    KoinApplication(
        application = {
            koinApplication?.let { koinApplication() }
            modules(appModule)
        },
    ) {
        Theme {
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
