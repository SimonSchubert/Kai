package com.inspiredandroid.kai

import com.inspiredandroid.kai.data.AppSettings
import com.inspiredandroid.kai.data.ConversationStorage
import com.inspiredandroid.kai.data.DataRepository
import com.inspiredandroid.kai.data.EmailStore
import com.inspiredandroid.kai.data.HeartbeatManager
import com.inspiredandroid.kai.data.MemoryStore
import com.inspiredandroid.kai.data.RemoteDataRepository
import com.inspiredandroid.kai.data.SkillExecutor
import com.inspiredandroid.kai.data.SkillStore
import com.inspiredandroid.kai.data.TaskScheduler
import com.inspiredandroid.kai.data.TaskStore
import com.inspiredandroid.kai.data.ToolExecutor
import com.inspiredandroid.kai.mcp.McpServerManager
import com.inspiredandroid.kai.network.Requests
import com.inspiredandroid.kai.splinterlands.SplinterlandsApi
import com.inspiredandroid.kai.splinterlands.SplinterlandsBattleRunner
import com.inspiredandroid.kai.splinterlands.SplinterlandsStore
import com.inspiredandroid.kai.tools.CalendarPermissionController
import com.inspiredandroid.kai.tools.NotificationPermissionController
import com.inspiredandroid.kai.ui.chat.ChatViewModel
import com.inspiredandroid.kai.ui.settings.SandboxViewModel
import com.inspiredandroid.kai.ui.settings.SettingsViewModel
import com.inspiredandroid.kai.ui.settings.SplinterlandsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single<CalendarPermissionController> { CalendarPermissionController() }
    single<NotificationPermissionController> { NotificationPermissionController() }
    single<AppSettings> {
        AppSettings(createSecureSettings()).also {
            it.runMigrations(createLegacySettings())
        }
    }
    single<Requests> {
        Requests()
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
    single<SkillStore> {
        SkillStore(get()).also { it.migrateIfNeeded() }
    }
    single<SkillExecutor> {
        SkillExecutor(get())
    }
    single<SplinterlandsStore> {
        SplinterlandsStore(get())
    }
    single<SplinterlandsApi> {
        SplinterlandsApi()
    }
    single<HeartbeatManager> {
        HeartbeatManager(get(), get(), get(), get())
    }
    single<McpServerManager> {
        McpServerManager(get())
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
            mcpServerManager = get(),
            skillStore = get(),
            skillExecutor = get(),
        )
    }
    single<DataRepository> { get<RemoteDataRepository>() }
    single<SplinterlandsBattleRunner> {
        SplinterlandsBattleRunner(get(), get(), get<DataRepository>(), get<DaemonController>())
    }
    single<TaskScheduler> {
        TaskScheduler(get<DataRepository>(), get(), get(), get(), get())
    }
    single<DaemonController> { createDaemonController() }
    single<SandboxController> { createSandboxController() }
    viewModel { SettingsViewModel(get<DataRepository>(), get<DaemonController>()) }
    viewModel { SandboxViewModel(get<DataRepository>(), get<SandboxController>()) }
    viewModel { SplinterlandsViewModel(get<DataRepository>(), get(), get(), get<SplinterlandsApi>()) }
    viewModel { ChatViewModel(get<DataRepository>(), get<TaskScheduler>()) }
}
