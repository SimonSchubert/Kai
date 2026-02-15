package com.inspiredandroid.kai

import com.inspiredandroid.kai.data.AppSettings
import com.inspiredandroid.kai.data.ConversationStorage
import com.inspiredandroid.kai.data.DataRepository
import com.inspiredandroid.kai.data.RemoteDataRepository
import com.inspiredandroid.kai.network.Requests
import com.inspiredandroid.kai.tools.CalendarPermissionController
import com.inspiredandroid.kai.tools.NotificationPermissionController
import com.inspiredandroid.kai.tools.SmsPermissionController
import com.inspiredandroid.kai.ui.chat.ChatViewModel
import com.inspiredandroid.kai.ui.explore.ExploreDetailViewModel
import com.inspiredandroid.kai.ui.explore.ExploreViewModel
import com.inspiredandroid.kai.ui.history.HistoryViewModel
import com.inspiredandroid.kai.ui.settings.SettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single<CalendarPermissionController> { CalendarPermissionController() }
    single<NotificationPermissionController> { NotificationPermissionController() }
    single<SmsPermissionController> { SmsPermissionController() }
    single<AppSettings> {
        val secureSettings = createSecureSettings()
        val legacySettings = createLegacySettings()
        AppSettings(secureSettings).also {
            it.migrateFromLegacyIfNeeded(legacySettings)
        }
    }
    single<Requests> {
        Requests(get())
    }
    single<ConversationStorage> {
        ConversationStorage(get())
    }
    single<RemoteDataRepository> {
        RemoteDataRepository(get(), get(), get())
    }
    single<DataRepository> { get<RemoteDataRepository>() }
    viewModel { SettingsViewModel(get<DataRepository>()) }
    viewModel { ChatViewModel(get<DataRepository>()) }
    viewModel { HistoryViewModel(get<DataRepository>()) }
    viewModel { ExploreViewModel(get<DataRepository>()) }
    viewModel { ExploreDetailViewModel(get<DataRepository>()) }
}
