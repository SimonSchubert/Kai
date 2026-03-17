package com.inspiredandroid.kai.ui.settings

import androidx.compose.runtime.Immutable
import com.inspiredandroid.kai.data.EmailAccount
import com.inspiredandroid.kai.data.HeartbeatLogEntry
import com.inspiredandroid.kai.data.ImportSection
import com.inspiredandroid.kai.data.MemoryEntry
import com.inspiredandroid.kai.data.ScheduledTask
import com.inspiredandroid.kai.data.Service
import com.inspiredandroid.kai.mcp.PopularMcpServer
import com.inspiredandroid.kai.network.dtos.SponsorsResponseDto
import com.inspiredandroid.kai.network.tools.ToolInfo
import org.jetbrains.compose.resources.StringResource

@Immutable
data class ConfiguredServiceEntry(
    val instanceId: String,
    val service: Service,
    val connectionStatus: ConnectionStatus = ConnectionStatus.Unknown,
    val apiKey: String = "",
    val baseUrl: String = "",
    val selectedModel: SettingsModel? = null,
    val models: List<SettingsModel> = emptyList(),
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
    Integrations,
}

@Immutable
data class SettingsUiState(
    val currentTab: SettingsTab = SettingsTab.Services,
    val configuredServices: List<ConfiguredServiceEntry> = emptyList(),
    val expandedServiceId: String? = null,
    val availableServicesToAdd: List<Service> = emptyList(),
    val tools: List<ToolInfo> = emptyList(),
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
    val isMemoryEnabled: Boolean = true,
    val onToggleMemory: (Boolean) -> Unit = {},
    val memories: List<MemoryEntry> = emptyList(),
    val onDeleteMemory: (String) -> Unit = {},
    val isSchedulingEnabled: Boolean = true,
    val onToggleScheduling: (Boolean) -> Unit = {},
    val scheduledTasks: List<ScheduledTask> = emptyList(),
    val onCancelTask: (String) -> Unit = {},
    val isDaemonEnabled: Boolean = false,
    val onToggleDaemon: (Boolean) -> Unit = {},
    val showDaemonToggle: Boolean = false,
    val isHeartbeatEnabled: Boolean = true,
    val heartbeatIntervalMinutes: Int = 30,
    val heartbeatActiveHoursStart: Int = 8,
    val heartbeatActiveHoursEnd: Int = 22,
    val heartbeatPrompt: String = "",
    val heartbeatLog: List<HeartbeatLogEntry> = emptyList(),
    val onToggleHeartbeat: (Boolean) -> Unit = {},
    val onChangeHeartbeatInterval: (Int) -> Unit = {},
    val onChangeHeartbeatActiveHours: (Int, Int) -> Unit = { _, _ -> },
    val onSaveHeartbeatPrompt: (String) -> Unit = {},
    val isEmailEnabled: Boolean = true,
    val showEmailToggle: Boolean = false,
    val emailAccounts: List<EmailAccount> = emptyList(),
    val emailPollIntervalMinutes: Int = 15,
    val onToggleEmail: (Boolean) -> Unit = {},
    val onRemoveEmailAccount: (String) -> Unit = {},
    val onChangeEmailPollInterval: (Int) -> Unit = {},
    val isFreeFallbackEnabled: Boolean = true,
    val onToggleFreeFallback: (Boolean) -> Unit = {},
    val uiScale: Float = 1.0f,
    val onChangeUiScale: (Float) -> Unit = {},
    val showUiScale: Boolean = false,
    val mcpServers: List<McpServerUiState> = emptyList(),
    val onAddMcpServer: (String, String, Map<String, String>) -> Unit = { _, _, _ -> },
    val onRemoveMcpServer: (String) -> Unit = {},
    val onToggleMcpServer: (String, Boolean) -> Unit = { _, _ -> },
    val onRefreshMcpServer: (String) -> Unit = {},
    val showAddMcpServerDialog: Boolean = false,
    val onShowAddMcpServerDialog: (Boolean) -> Unit = {},
    val onAddPopularMcpServer: (PopularMcpServer) -> Unit = {},
    val onExportSettings: () -> String = { "" },
    val onImportSettings: (ByteArray, Set<ImportSection>, Boolean) -> ImportResult = { _, _, _ -> ImportResult.Failure },
    val currentSponsors: List<SponsorsResponseDto.Sponsor> = emptyList(),
    val pastSponsors: List<SponsorsResponseDto.Sponsor> = emptyList(),
)

@Immutable
data class McpServerUiState(
    val id: String,
    val name: String,
    val url: String,
    val isEnabled: Boolean,
    val connectionStatus: McpConnectionStatus,
    val tools: List<ToolInfo>,
)

enum class McpConnectionStatus {
    Unknown,
    Connecting,
    Connected,
    Error,
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
