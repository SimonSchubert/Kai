package com.inspiredandroid.kai.ui.settings

import androidx.compose.runtime.Immutable
import com.inspiredandroid.kai.data.EmailAccount
import com.inspiredandroid.kai.data.EmailSyncState
import com.inspiredandroid.kai.data.HeartbeatLogEntry
import com.inspiredandroid.kai.data.MemoryEntry
import com.inspiredandroid.kai.data.ScheduledTask
import com.inspiredandroid.kai.data.Service
import com.inspiredandroid.kai.data.ServiceEntry
import com.inspiredandroid.kai.data.SmsSyncState
import com.inspiredandroid.kai.inference.DownloadError
import com.inspiredandroid.kai.inference.LocalModel
import com.inspiredandroid.kai.network.dtos.SponsorsResponseDto
import com.inspiredandroid.kai.network.tools.ToolInfo
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
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
    Agent,
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
    val soulText: String = "",
    val isDynamicUiEnabled: Boolean = true,
    val isOledModeEnabled: Boolean = false,
    val isMemoryEnabled: Boolean = true,
    val memories: ImmutableList<MemoryEntry> = persistentListOf(),
    val isSchedulingEnabled: Boolean = true,
    val scheduledTasks: ImmutableList<ScheduledTask> = persistentListOf(),
    val isDaemonEnabled: Boolean = false,
    val showDaemonToggle: Boolean = false,
    val isHeartbeatEnabled: Boolean = true,
    val heartbeatIntervalMinutes: Int = 30,
    val heartbeatActiveHoursStart: Int = 8,
    val heartbeatActiveHoursEnd: Int = 22,
    val heartbeatPrompt: String = "",
    val heartbeatLog: ImmutableList<HeartbeatLogEntry> = persistentListOf(),
    val heartbeatServiceEntries: ImmutableList<ServiceEntry> = persistentListOf(),
    val heartbeatSelectedInstanceId: String? = null,
    val isEmailEnabled: Boolean = true,
    val showEmailToggle: Boolean = false,
    val emailAccounts: ImmutableList<EmailAccount> = persistentListOf(),
    val emailPollIntervalMinutes: Int = 15,
    val emailPendingCount: Int = 0,
    val emailSyncStates: ImmutableMap<String, EmailSyncState> = persistentMapOf(),
    val refreshingEmailAccountIds: ImmutableSet<String> = persistentSetOf(),
    val showSmsSection: Boolean = false,
    val isSmsEnabled: Boolean = false,
    val smsPermissionGranted: Boolean = false,
    val smsPollIntervalMinutes: Int = 15,
    val smsPendingCount: Int = 0,
    val smsSyncState: SmsSyncState = SmsSyncState(),
    val isRefreshingSms: Boolean = false,
    val isSmsSendEnabled: Boolean = false,
    val smsSendPermissionGranted: Boolean = false,
    val isFreeFallbackEnabled: Boolean = true,
    val uiScale: Float = 1.0f,
    val showUiScale: Boolean = false,
    val mcpServers: ImmutableList<McpServerUiState> = persistentListOf(),
    val showAddMcpServerDialog: Boolean = false,
    val localAvailableModels: ImmutableList<LocalModel> = persistentListOf(),
    val totalDeviceMemoryBytes: Long = Long.MAX_VALUE,
    val localFreeSpaceBytes: Long = 0L,
    val localDownloadingModelId: String? = null,
    val localDownloadProgress: Float? = null,
    val localDownloadError: DownloadError? = null,
    val modelContextTokens: ImmutableMap<String, Int> = persistentMapOf(),
    val currentSponsors: ImmutableList<SponsorsResponseDto.Sponsor> = persistentListOf(),
    val pastSponsors: ImmutableList<SponsorsResponseDto.Sponsor> = persistentListOf(),
    val pendingDeletion: PendingDeletion? = null,
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
    /** Human-readable name to display in place of [id], when the provider exposes one. */
    val displayName: String? = null,
    /** Max context window in tokens, from the API or the curated catalog. */
    val contextWindow: Long? = null,
    /** Release date as "YYYY-MM" or "YYYY-MM-DD", from the API or the curated catalog. */
    val releaseDate: String? = null,
    /** Parameter count, pre-formatted for display (e.g. "70B", "8B", "3.3B"). */
    val parameterCount: String? = null,
    /** LMArena Elo score, or null when unknown. */
    val arenaScore: Int? = null,
)
