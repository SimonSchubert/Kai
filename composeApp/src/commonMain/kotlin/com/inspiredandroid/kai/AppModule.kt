package com.inspiredandroid.kai

import com.inspiredandroid.kai.data.AppSettings
import com.inspiredandroid.kai.data.ConversationStorage
import com.inspiredandroid.kai.data.DataRepository
import com.inspiredandroid.kai.data.EmailStore
import com.inspiredandroid.kai.data.HeartbeatManager
import com.inspiredandroid.kai.data.MemoryStore
import com.inspiredandroid.kai.data.RemoteDataRepository
import com.inspiredandroid.kai.data.TaskScheduler
import com.inspiredandroid.kai.data.TaskStore
import com.inspiredandroid.kai.data.ToolExecutor
import com.inspiredandroid.kai.network.Requests
import com.inspiredandroid.kai.tools.CalendarPermissionController
import com.inspiredandroid.kai.tools.NotificationPermissionController
import com.inspiredandroid.kai.ui.chat.ChatViewModel
import com.inspiredandroid.kai.ui.settings.SettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single<CalendarPermissionController> { CalendarPermissionController() }
    single<NotificationPermissionController> { NotificationPermissionController() }
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
    single<ToolExecutor> {
        ToolExecutor()
    }
    single<MemoryStore> {
        MemoryStore(get())
    }
    single<TaskStore> {
        TaskStore(get())
    }
    single<EmailStore> {
        EmailStore(get())
    }
    single<HeartbeatManager> {
        HeartbeatManager(get(), get(), get(), get())
    }
    single<RemoteDataRepository> {
        RemoteDataRepository(
            requests = get(),
            appSettings = get(),
            conversationStorage = get(),
            toolExecutor = get(),
            memoryStore = get(),
            taskStore = get(),
            heartbeatManager = get(),
            emailStore = get(),
        )
    }
    single<DataRepository> { get<RemoteDataRepository>() }
    single<TaskScheduler> {
        TaskScheduler(get<DataRepository>(), get(), get(), get(), get())
    }
    single<DaemonController> { createDaemonController() }
    viewModel { SettingsViewModel(get<DataRepository>(), get<DaemonController>()) }
    viewModel { ChatViewModel(get<DataRepository>(), get<TaskScheduler>()) }
}
