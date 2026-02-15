
package com.inspiredandroid.kai

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
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
import com.inspiredandroid.kai.tools.SetupSmsPermissionHandler
import com.inspiredandroid.kai.tools.SmsPermissionController
import com.inspiredandroid.kai.ui.DarkColorScheme
import com.inspiredandroid.kai.ui.LightColorScheme
import com.inspiredandroid.kai.ui.Theme
import com.inspiredandroid.kai.ui.chat.ChatScreen
import com.inspiredandroid.kai.ui.chat.ChatViewModel
import com.inspiredandroid.kai.ui.explore.ExploreDetailScreen
import com.inspiredandroid.kai.ui.explore.ExploreScreen
import com.inspiredandroid.kai.ui.history.HistoryScreen
import com.inspiredandroid.kai.ui.settings.SettingsScreen
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.marc_apps.tts.TextToSpeechInstance
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.KoinApplication

@Serializable
@SerialName("home")
object Home

@Serializable
@SerialName("settings")
object Settings

@Serializable
@SerialName("history")
object History

@Serializable
@SerialName("explore")
data class Explore(val topic: String)

@Serializable
@SerialName("explore_detail")
data class ExploreDetail(val itemName: String)

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
    setSingletonImageLoaderFactory { context: PlatformContext ->
        ImageLoader.Builder(context)
            .components {
                add(KtorNetworkFetcherFactory())
                add(SvgDecoder.Factory())
            }
            .build()
    }

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

        // Set up permission handlers
        val calendarPermissionController = koinInject<CalendarPermissionController>()
        SetupCalendarPermissionHandler(calendarPermissionController)

        val notificationPermissionController = koinInject<NotificationPermissionController>()
        SetupNotificationPermissionHandler(notificationPermissionController)

        val smsPermissionController = koinInject<SmsPermissionController>()
        SetupSmsPermissionHandler(smsPermissionController)

        Theme(colorScheme = colorScheme) {
            val chatViewModel: ChatViewModel = koinViewModel()

            NavHost(navController, startDestination = Home) {
                composable<Home> {
                    ChatScreen(
                        viewModel = chatViewModel,
                        textToSpeech = textToSpeech,
                        onNavigateToSettings = {
                            navController.navigate(Settings)
                        },
                        onNavigateToHistory = {
                            navController.navigate(History)
                        },
                        onNavigateToExplore = { topic ->
                            navController.navigate(Explore(topic))
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
                composable<History> {
                    HistoryScreen(
                        onNavigateBack = {
                            navController.navigateUp()
                        },
                        onSelectConversation = { id ->
                            chatViewModel.loadConversation(id)
                            navController.navigateUp()
                        },
                    )
                }
                composable<Explore> { backStackEntry ->
                    val route = backStackEntry.toRoute<Explore>()
                    ExploreScreen(
                        topic = route.topic,
                        onNavigateBack = {
                            navController.navigateUp()
                        },
                        onNavigateToDetail = { itemName ->
                            navController.navigate(ExploreDetail(itemName))
                        },
                    )
                }
                composable<ExploreDetail> { backStackEntry ->
                    val route = backStackEntry.toRoute<ExploreDetail>()
                    ExploreDetailScreen(
                        itemName = route.itemName,
                        onNavigateBack = {
                            navController.navigateUp()
                        },
                        onNavigateToDetail = { itemName ->
                            navController.navigate(ExploreDetail(itemName))
                        },
                    )
                }
            }
        }
    }
}
