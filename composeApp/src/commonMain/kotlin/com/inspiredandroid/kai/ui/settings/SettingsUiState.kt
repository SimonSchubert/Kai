package com.inspiredandroid.kai.ui.settings

import androidx.compose.runtime.Immutable
import com.inspiredandroid.kai.data.EmailAccount
import com.inspiredandroid.kai.data.HeartbeatLogEntry
import com.inspiredandroid.kai.data.ImportSection
import com.inspiredandroid.kai.data.MemoryEntry
import com.inspiredandroid.kai.data.ScheduledTask
import com.inspiredandroid.kai.data.Service
import com.inspiredandroid.kai.data.ServiceEntry
import com.inspiredandroid.kai.inference.LocalModel
import com.inspiredandroid.kai.mcp.PopularMcpServer
import com.inspiredandroid.kai.network.dtos.SponsorsResponseDto
import com.inspiredandroid.kai.network.tools.ToolInfo
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.StringResource

@Immutable
data class ConfiguredServiceEntry(
    val instanceId: String,
    val service: Service,
    val connectionStatus: ConnectionStatus = ConnectionStatus.Unknown,
    val apiKey: String = "",
    val baseUrl: String = "",
    val selectedModel: SettingsModel? = null,
    val models: ImmutableList<SettingsModel> = persistentListOf(),
)

enum class ConnectionStatus {
    Unknown,
    Checking,
    Connected,
    ErrorInvalidKey,
    ErrorQuotaExhausted,
    ErrorRateLimited,
    ErrorConnectionFailed,
    Error,
}

enum class SettingsTab {
    General,
    Services,
    Tools,
    Sandbox,
    Integrations,
}

@Immutable
data class SettingsUiState(
    val currentTab: SettingsTab = SettingsTab.Services,
    val configuredServices: ImmutableList<ConfiguredServiceEntry> = persistentListOf(),
    val expandedServiceId: String? = null,
    val availableServicesToAdd: ImmutableList<Service> = persistentListOf(),
    val tools: ImmutableList<ToolInfo> = persistentListOf(),
    val onSelectTab: (SettingsTab) -> Unit = {},
    val onAddService: (Service) -> Unit = {},
    val onRemoveService: (String) -> Unit = {},
    val onReorderServices: (List<String>) -> Unit = {},
    val onExpandService: (String?) -> Unit = {},
    val onChangeApiKey: (String, String) -> Unit = { _, _ -> },
    val onChangeBaseUrl: (String, String) -> Unit = { _, _ -> },
    val onSelectModel: (String, String) -> Unit = { _, _ -> },
    val onToggleTool: (String, Boolean) -> Unit = { _, _ -> },
    val soulText: String = "",
    val onSaveSoul: (String) -> Unit = {},
    val isDynamicUiEnabled: Boolean = true,
    val onToggleDynamicUi: (Boolean) -> Unit = {},
    val isMemoryEnabled: Boolean = true,
    val onToggleMemory: (Boolean) -> Unit = {},
    val memories: ImmutableList<MemoryEntry> = persistentListOf(),
    val onDeleteMemory: (String) -> Unit = {},
    val isSchedulingEnabled: Boolean = true,
    val onToggleScheduling: (Boolean) -> Unit = {},
    val scheduledTasks: ImmutableList<ScheduledTask> = persistentListOf(),
    val onCancelTask: (String) -> Unit = {},
    val isDaemonEnabled: Boolean = false,
    val onToggleDaemon: (Boolean) -> Unit = {},
    val showDaemonToggle: Boolean = false,
    val isHeartbeatEnabled: Boolean = true,
    val heartbeatIntervalMinutes: Int = 30,
    val heartbeatActiveHoursStart: Int = 8,
    val heartbeatActiveHoursEnd: Int = 22,
    val heartbeatPrompt: String = "",
    val heartbeatLog: ImmutableList<HeartbeatLogEntry> = persistentListOf(),
    val heartbeatServiceEntries: ImmutableList<ServiceEntry> = persistentListOf(),
    val heartbeatSelectedInstanceId: String? = null,
    val onToggleHeartbeat: (Boolean) -> Unit = {},
    val onChangeHeartbeatInterval: (Int) -> Unit = {},
    val onChangeHeartbeatActiveHours: (Int, Int) -> Unit = { _, _ -> },
    val onSaveHeartbeatPrompt: (String) -> Unit = {},
    val onChangeHeartbeatService: (String?) -> Unit = {},
    val isEmailEnabled: Boolean = true,
    val showEmailToggle: Boolean = false,
    val emailAccounts: ImmutableList<EmailAccount> = persistentListOf(),
    val emailPollIntervalMinutes: Int = 15,
    val onToggleEmail: (Boolean) -> Unit = {},
    val onRemoveEmailAccount: (String) -> Unit = {},
    val onChangeEmailPollInterval: (Int) -> Unit = {},
    val isFreeFallbackEnabled: Boolean = true,
    val onToggleFreeFallback: (Boolean) -> Unit = {},
    val uiScale: Float = 1.0f,
    val onChangeUiScale: (Float) -> Unit = {},
    val showUiScale: Boolean = false,
    val mcpServers: ImmutableList<McpServerUiState> = persistentListOf(),
    val onAddMcpServer: (String, String, Map<String, String>) -> Unit = { _, _, _ -> },
    val onRemoveMcpServer: (String) -> Unit = {},
    val onToggleMcpServer: (String, Boolean) -> Unit = { _, _ -> },
    val onRefreshMcpServer: (String) -> Unit = {},
    val showAddMcpServerDialog: Boolean = false,
    val onShowAddMcpServerDialog: (Boolean) -> Unit = {},
    val onAddPopularMcpServer: (PopularMcpServer) -> Unit = {},
    val localAvailableModels: ImmutableList<LocalModel> = persistentListOf(),
    val localFreeSpaceBytes: Long = 0L,
    val localDownloadingModelId: String? = null,
    val localDownloadProgress: Float? = null,
    val onDownloadLocalModel: (LocalModel) -> Unit = {},
    val onCancelLocalModelDownload: () -> Unit = {},
    val onDeleteLocalModel: (String) -> Unit = {},
    val onExportSettings: () -> String = { "" },
    val onImportSettings: (ByteArray, Set<ImportSection>, Boolean) -> ImportResult = { _, _, _ -> ImportResult.Failure },
    val currentSponsors: ImmutableList<SponsorsResponseDto.Sponsor> = persistentListOf(),
    val pastSponsors: ImmutableList<SponsorsResponseDto.Sponsor> = persistentListOf(),
    val pendingDeletion: PendingDeletion? = null,
    val onUndoDelete: () -> Unit = {},
)

@Immutable
data class McpServerUiState(
    val id: String,
    val name: String,
    val url: String,
    val isEnabled: Boolean,
    val connectionStatus: McpConnectionStatus,
    val tools: ImmutableList<ToolInfo>,
)

enum class McpConnectionStatus {
    Unknown,
    Connecting,
    Connected,
    Error,
}

sealed interface PendingDeletion {
    data class Memory(val key: String) : PendingDeletion
    data class Task(val id: String) : PendingDeletion
    data class EmailAccount(val id: String) : PendingDeletion
    data class Service(val instanceId: String) : PendingDeletion
    data class McpServer(val serverId: String) : PendingDeletion
}

sealed interface ImportResult {
    data object Success : ImportResult
    data class PartialSuccess(val errorCount: Int) : ImportResult
    data object Failure : ImportResult
}

@Immutable
data class SettingsModel(
    val id: String,
    val subtitle: String,
    val description: String? = null,
    val descriptionRes: StringResource? = null,
    val isSelected: Boolean = false,
)
