package com.inspiredandroid.kai

import com.inspiredandroid.kai.data.AppSettings
import com.inspiredandroid.kai.data.DataRepository
import com.inspiredandroid.kai.data.RemoteDataRepository
import com.inspiredandroid.kai.network.Requests
import com.inspiredandroid.kai.ui.chat.ChatViewModel
import com.inspiredandroid.kai.ui.settings.SettingsViewModel
import com.russhwolf.settings.Settings
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single<AppSettings> {
        AppSettings(Settings())
    }
    single<Requests> {
        Requests(get())
    }
    single<RemoteDataRepository> {
        RemoteDataRepository(get(), get())
    }
    single<DataRepository> { get<RemoteDataRepository>() }
    viewModel { SettingsViewModel(get<DataRepository>()) }
    viewModel { ChatViewModel(get()) }
}
