@file:OptIn(ExperimentalMaterial3Api::class)

package com.inspiredandroid.kai

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.svg.SvgDecoder
import com.inspiredandroid.kai.data.AppSettings
import com.inspiredandroid.kai.tools.CalendarPermissionController
import com.inspiredandroid.kai.tools.NotificationPermissionController
import com.inspiredandroid.kai.tools.SetupCalendarPermissionHandler
import com.inspiredandroid.kai.tools.SetupNotificationPermissionHandler
import com.inspiredandroid.kai.ui.DarkColorScheme
import com.inspiredandroid.kai.ui.LightColorScheme
import com.inspiredandroid.kai.ui.Theme
import com.inspiredandroid.kai.ui.chat.ChatScreen
import com.inspiredandroid.kai.ui.chat.ChatViewModel
import com.inspiredandroid.kai.ui.handCursor
import com.inspiredandroid.kai.ui.settings.SettingsScreen
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.tab_chat
import kai.composeapp.generated.resources.tab_settings
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import androidx.compose.ui.text.intl.Locale
import nl.marc_apps.tts.TextToSpeechInstance
import nl.marc_apps.tts.experimental.ExperimentalVoiceApi
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Serializable
@SerialName("home")
object Home

@Serializable
@SerialName("settings")
object Settings

@Composable
fun App(
    navController: NavHostController,
    colorScheme: ColorScheme = if (isSystemInDarkTheme()) {
        DarkColorScheme
    } else {
        LightColorScheme
    },
    textToSpeech: TextToSpeechInstance? = null,
    isKoinStarted: Boolean = false,
    onAppOpens: ((Int) -> Unit)? = null,
) {
    setSingletonImageLoaderFactory { context: PlatformContext ->
        ImageLoader.Builder(context)
            .components {
                add(KtorNetworkFetcherFactory())
                add(SvgDecoder.Factory())
            }
            .build()
    }

    // Reuse global Koin if already started (Android Application class),
    // otherwise create a new instance (iOS, Desktop, Wasm).
    if (isKoinStarted) {
        AppContent(navController, colorScheme, textToSpeech, onAppOpens)
    } else {
        KoinApplication(
            application = {
                modules(appModule)
            },
        ) {
            AppContent(navController, colorScheme, textToSpeech, onAppOpens)
        }
    }
}

@Composable
private fun AppContent(
    navController: NavHostController,
    colorScheme: ColorScheme,
    textToSpeech: TextToSpeechInstance?,
    onAppOpens: ((Int) -> Unit)?,
) {
    // Track app opens after Koin is initialized
    onAppOpens?.let { callback ->
        val appSettings = koinInject<AppSettings>()
        LaunchedEffect(Unit) {
            callback(appSettings.trackAppOpen())
        }
    }

    // Set up permission handlers
    val calendarPermissionController = koinInject<CalendarPermissionController>()
    SetupCalendarPermissionHandler(calendarPermissionController)

    val notificationPermissionController = koinInject<NotificationPermissionController>()
    SetupNotificationPermissionHandler(notificationPermissionController)

    // Set TTS voice to match system language
    @OptIn(ExperimentalVoiceApi::class)
    LaunchedEffect(textToSpeech) {
        val tts = textToSpeech ?: return@LaunchedEffect
        val systemLanguage = Locale.current.language
        val matchingVoice = tts.voices
            .firstOrNull { it.languageTag.startsWith(systemLanguage) }
        if (matchingVoice != null) {
            tts.currentVoice = matchingVoice
        }
    }

    val appSettingsForScale = koinInject<AppSettings>()
    val uiScale by appSettingsForScale.uiScaleFlow.collectAsState()
    val defaultDensity = LocalDensity.current
    val scaledDensity = remember(defaultDensity, uiScale) {
        Density(defaultDensity.density * uiScale, defaultDensity.fontScale)
    }

    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        Theme(colorScheme = colorScheme) {
            val chatViewModel: ChatViewModel = koinViewModel()
            val showTabBar = !isMobilePlatform
            val currentBackStackEntry by navController.currentBackStackEntryAsState()
            val isHome = currentBackStackEntry?.destination?.route == "home"

            val navigationTabBar: @Composable () -> Unit = {
                val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
                val count = 2
                SingleChoiceSegmentedButtonRow {
                    SegmentedButton(
                        selected = isHome,
                        onClick = {
                            navController.navigate(Home) {
                                popUpTo(Home) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = if (isRtl) count - 1 else 0, count = count),
                        modifier = Modifier.handCursor(),
                    ) {
                        Text(stringResource(Res.string.tab_chat))
                    }
                    SegmentedButton(
                        selected = !isHome,
                        onClick = {
                            navController.navigate(Settings) {
                                popUpTo(Home)
                                launchSingleTop = true
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = if (isRtl) 0 else count - 1, count = count),
                        modifier = Modifier.handCursor(),
                    ) {
                        Text(stringResource(Res.string.tab_settings))
                    }
                }
            }

            NavHost(
                navController,
                startDestination = Home,
                modifier = Modifier.background(MaterialTheme.colorScheme.background),
            ) {
                composable<Home> {
                    ChatScreen(
                        viewModel = chatViewModel,
                        textToSpeech = textToSpeech,
                        onNavigateToSettings = {
                            navController.navigate(Settings)
                        },
                        navigationTabBar = if (showTabBar) navigationTabBar else null,
                    )
                }
                composable<Settings> {
                    if (showTabBar) {
                        DisposableEffect(Unit) {
                            onDispose {
                                chatViewModel.refreshSettings()
                            }
                        }
                    }
                    SettingsScreen(
                        onNavigateBack = {
                            chatViewModel.refreshSettings()
                            navController.navigateUp()
                        },
                        navigationTabBar = if (showTabBar) navigationTabBar else null,
                    )
                }
            }
        }
    }
}
