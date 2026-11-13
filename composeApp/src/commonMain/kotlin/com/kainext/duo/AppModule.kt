package com.kainext.duo

import com.kainext.duo.data.AppSettings
import com.kainext.duo.data.ConversationStorage
import com.kainext.duo.data.DataRepository
import com.kainext.duo.data.EmailStore
import com.kainext.duo.data.HeartbeatManager
import com.kainext.duo.data.MemoryStore
import com.kainext.duo.data.NotificationStore
import com.kainext.duo.data.RemoteDataRepository
import com.kainext.duo.data.SmsDraftStore
import com.kainext.duo.data.SmsStore
import com.kainext.duo.data.TaskScheduler
import com.kainext.duo.data.TaskStore
import com.kainext.duo.data.ToolExecutor
import com.kainext.duo.data.createConversationPersistence
import com.kainext.duo.data.runMigrations
import com.kainext.duo.email.EmailPoller
import com.kainext.duo.inference.createLocalInferenceEngine
import com.kainext.duo.mcp.McpServerManager
import com.kainext.duo.network.Requests
import com.kainext.duo.notifications.NotificationReader
import com.kainext.duo.skills.CreativeModeManager
import com.kainext.duo.skills.SkillManager
import com.kainext.duo.sms.SmsPoller
import com.kainext.duo.sms.SmsReader
import com.kainext.duo.sms.SmsSender
import com.kainext.duo.splinterlands.SplinterlandsApi
import com.kainext.duo.splinterlands.SplinterlandsBattleRunner
import com.kainext.duo.splinterlands.SplinterlandsStore
import com.kainext.duo.tools.AppPermission
import com.kainext.duo.tools.NotificationListenerController
import com.kainext.duo.tools.PermissionController
import com.kainext.duo.ui.build.KaiBuildViewModel
import com.kainext.duo.ui.chat.ChatViewModel
import com.kainext.duo.ui.sandbox.SandboxFileBrowserViewModel
import com.kainext.duo.ui.sandbox.SandboxPackagesViewModel
import com.kainext.duo.ui.sandbox.SandboxSessionViewModel
import com.kainext.duo.ui.settings.SandboxViewModel
import com.kainext.duo.ui.settings.SettingsViewModel
import com.kainext.duo.ui.settings.SplinterlandsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Picks the file browser bound to Kai Build's Debian rather than the chat sandbox. */
val KAI_BUILD_FILES = named("kaiBuildFiles")

/** Qualifier for the [PermissionController] singleton handling [permission]. */
fun permissionQualifier(permission: AppPermission) = named(permission.name)

val appModule = module {
    AppPermission.entries.forEach { permission ->
        single(permissionQualifier(permission)) { PermissionController(permission) }
    }
    single<SmsReader> { SmsReader() }
    single<SmsSender> { SmsSender() }
    single<NotificationListenerController> { NotificationListenerController() }
    single<NotificationReader> { NotificationReader() }
    single<AppSettings> {
        AppSettings(createSecureSettings()).also {
            it.runMigrations(createLegacySettings())
        }
    }
    single<Requests> {
        Requests()
    }
    single<ConversationStorage> {
        ConversationStorage(get(), createConversationPersistence(get()))
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
    single<NotificationStore> {
        NotificationStore(get())
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
    single<SkillManager> {
        SkillManager(get<SandboxController>())
    }
    single<CreativeModeManager> {
        CreativeModeManager(get<SkillManager>())
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
            smsPermissionController = get(permissionQualifier(AppPermission.READ_SMS)),
            smsSendPermissionController = get(permissionQualifier(AppPermission.SEND_SMS)),
            smsSender = get(),
            smsDraftStore = get(),
            notificationStore = get(),
            notificationListenerController = get(),
            mcpServerManager = get(),
            skillManager = get(),
            sandboxController = get(),
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
            get<NotificationStore>(),
        )
    }
    single<DaemonController> { createDaemonController() }
    single<SandboxController> { createSandboxController() }
    single<KaiBuildController> { createKaiBuildController() }
    viewModel { SettingsViewModel(get<DataRepository>(), get<DaemonController>(), get(permissionQualifier(AppPermission.POST_NOTIFICATIONS)), get<TaskScheduler>(), localNetworkPermissionController = get(permissionQualifier(AppPermission.LOCAL_NETWORK))) }
    viewModel { SandboxViewModel(get<DataRepository>(), get<SandboxController>()) }
    viewModel { SandboxFileBrowserViewModel(get<SandboxController>()) }
    viewModel { SandboxPackagesViewModel(get<SandboxController>()) }
    viewModel { SandboxSessionViewModel(get<SandboxController>(), get<DataRepository>()) }
    viewModel { KaiBuildViewModel(get<KaiBuildController>(), get<DataRepository>()) }
    // Same browser, second environment: Kai Build's Debian instead of the chat sandbox.
    viewModel(KAI_BUILD_FILES) { SandboxFileBrowserViewModel(get<KaiBuildController>().files) }
    viewModel { SplinterlandsViewModel(get<DataRepository>(), get(), get(), get<SplinterlandsApi>()) }
    viewModel { ChatViewModel(get<DataRepository>(), get<TaskScheduler>(), localNetworkPermissionController = get(permissionQualifier(AppPermission.LOCAL_NETWORK))) }
}
