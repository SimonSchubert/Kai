package com.inspiredandroid.kai

import com.inspiredandroid.kai.data.AppSettings
import com.inspiredandroid.kai.data.ConversationStorage
import com.inspiredandroid.kai.data.DataRepository
import com.inspiredandroid.kai.data.EmailStore
import com.inspiredandroid.kai.data.HeartbeatManager
import com.inspiredandroid.kai.data.MemoryStore
import com.inspiredandroid.kai.data.RemoteDataRepository
import com.inspiredandroid.kai.data.SmsDraftStore
import com.inspiredandroid.kai.data.SmsStore
import com.inspiredandroid.kai.data.TaskScheduler
import com.inspiredandroid.kai.data.TaskStore
import com.inspiredandroid.kai.data.ToolExecutor
import com.inspiredandroid.kai.email.EmailPoller
import com.inspiredandroid.kai.inference.createLocalInferenceEngine
import com.inspiredandroid.kai.mcp.McpServerManager
import com.inspiredandroid.kai.network.Requests
import com.inspiredandroid.kai.sms.SmsPoller
import com.inspiredandroid.kai.sms.SmsReader
import com.inspiredandroid.kai.sms.SmsSender
import com.inspiredandroid.kai.splinterlands.SplinterlandsApi
import com.inspiredandroid.kai.splinterlands.SplinterlandsBattleRunner
import com.inspiredandroid.kai.splinterlands.SplinterlandsStore
import com.inspiredandroid.kai.tools.CalendarPermissionController
import com.inspiredandroid.kai.tools.NotificationPermissionController
import com.inspiredandroid.kai.tools.SmsPermissionController
import com.inspiredandroid.kai.tools.SmsSendPermissionController
import com.inspiredandroid.kai.ui.chat.ChatViewModel
import com.inspiredandroid.kai.ui.sandbox.SandboxFileBrowserViewModel
import com.inspiredandroid.kai.ui.settings.SandboxViewModel
import com.inspiredandroid.kai.ui.settings.SettingsViewModel
import com.inspiredandroid.kai.ui.settings.SplinterlandsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single<CalendarPermissionController> { CalendarPermissionController() }
    single<NotificationPermissionController> { NotificationPermissionController() }
    single<SmsPermissionController> { SmsPermissionController() }
    single<SmsSendPermissionController> { SmsSendPermissionController() }
    single<SmsReader> { SmsReader() }
    single<SmsSender> { SmsSender() }
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
    single<EmailPoller> {
        EmailPoller(get<EmailStore>())
    }
    single<SmsStore> {
        SmsStore(get())
    }
    single<SmsPoller> {
        SmsPoller(get<SmsStore>(), get<SmsReader>())
    }
    single<SmsDraftStore> {
        SmsDraftStore(get())
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
            emailPoller = get(),
            smsStore = get(),
            smsPoller = get(),
            smsReader = get(),
            smsPermissionController = get(),
            smsSendPermissionController = get(),
            smsSender = get(),
            smsDraftStore = get(),
            mcpServerManager = get(),
            localInferenceEngine = createLocalInferenceEngine(),
        )
    }
    single<DataRepository> { get<RemoteDataRepository>() }
    single<SplinterlandsBattleRunner> {
        SplinterlandsBattleRunner(get(), get(), get<DataRepository>(), get<DaemonController>())
    }
    single<TaskScheduler> {
        TaskScheduler(
            get<DataRepository>(),
            get(),
            get(),
            get(),
            get(),
            get<EmailPoller>(),
            get<SmsStore>(),
            get<SmsPoller>(),
        )
    }
    single<DaemonController> { createDaemonController() }
    single<SandboxController> { createSandboxController() }
    viewModel { SettingsViewModel(get<DataRepository>(), get<DaemonController>(), get<NotificationPermissionController>(), get<TaskScheduler>()) }
    viewModel { SandboxViewModel(get<DataRepository>(), get<SandboxController>()) }
    viewModel { SandboxFileBrowserViewModel(get<SandboxController>()) }
    viewModel { SplinterlandsViewModel(get<DataRepository>(), get(), get(), get<SplinterlandsApi>()) }
    viewModel { ChatViewModel(get<DataRepository>(), get<TaskScheduler>()) }
}
