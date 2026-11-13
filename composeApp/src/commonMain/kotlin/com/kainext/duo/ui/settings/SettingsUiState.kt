package com.kainext.duo.ui.settings

import androidx.compose.runtime.Immutable
import com.kainext.duo.DeviceControlStatus
import com.kainext.duo.data.EmailAccount
import com.kainext.duo.data.EmailSyncState
import com.kainext.duo.data.Goal
import com.kainext.duo.data.HeartbeatLogEntry
import com.kainext.duo.data.MemoryEntry
import com.kainext.duo.data.ScheduledTask
import com.kainext.duo.data.Service
import com.kainext.duo.data.ServiceEntry
import com.kainext.duo.data.SmsSyncState
import com.kainext.duo.data.ThemeMode
import com.kainext.duo.data.ThinkingEffort
import com.kainext.duo.inference.DownloadError
import com.kainext.duo.inference.LocalModel
import com.kainext.duo.inference.ModelImportError
import com.kainext.duo.network.dtos.SponsorsResponseDto
import com.kainext.duo.network.tools.ToolInfo
import com.kainext.duo.skills.RegistrySkillEntry
import com.kainext.duo.skills.SkillManifest
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
    /** OpenAI-Compatible only: when true, [customModelId] is used for chat instead of [selectedModel]. */
    val useCustomModel: Boolean = false,
    val customModelId: String = "",
)

enum class ConnectionStatus {
    Unknown,
    Checking,
    Connected,
    ErrorInvalidKey,
    ErrorQuotaExhausted,
    ErrorRateLimited,
    ErrorConnectionFailed,
    ErrorLocalNetworkDenied,
    Error,
}

enum class SettingsTab {
    General,
    Agent,
    Services,
    Goals,
    Tools,
    Sandbox,
    DeviceControl,
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
    val themeMode: ThemeMode = ThemeMode.System,
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
    val isRefreshingHeartbeat: Boolean = false,
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
    val showNotificationsSection: Boolean = false,
    val isNotificationsEnabled: Boolean = false,
    val notificationListenerAccessGranted: Boolean = false,
    val notificationListenerBound: Boolean = false,
    val notificationPendingCount: Int = 0,
    val isFreeFallbackEnabled: Boolean = true,
    val uiScale: Float = 1.0f,
    val showUiScale: Boolean = false,
    val mcpServers: ImmutableList<McpServerUiState> = persistentListOf(),
    val showAddMcpServerDialog: Boolean = false,
    val skills: ImmutableList<SkillManifest> = persistentListOf(),
    val showAddSkillDialog: Boolean = false,
    val isInstallingSkill: Boolean = false,
    val skillInstallError: String? = null,
    val browsableSkills: ImmutableList<RegistrySkillEntry> = persistentListOf(),
    val isBrowsingSkills: Boolean = false,
    val browseSkillsFailed: Boolean = false,
    val localAvailableModels: ImmutableList<LocalModel> = persistentListOf(),
    val localImportedModels: ImmutableList<LocalModel> = persistentListOf(),
    val totalDeviceMemoryBytes: Long = Long.MAX_VALUE,
    val localFreeSpaceBytes: Long = 0L,
    val localDownloadingModelId: String? = null,
    val localDownloadProgress: Float? = null,
    val localDownloadError: DownloadError? = null,
    val localImportingFileName: String? = null,
    val localImportProgress: Float? = null,
    val localImportError: ModelImportError? = null,
    val modelContextTokens: ImmutableMap<String, Int> = persistentMapOf(),
    val currentSponsors: ImmutableList<SponsorsResponseDto.Sponsor> = persistentListOf(),
    val pastSponsors: ImmutableList<SponsorsResponseDto.Sponsor> = persistentListOf(),
    val pendingDeletion: PendingDeletion? = null,
    // Device control (Android): root / Shizuku privileges, /sdcard access, UI automation.
    val isDeviceControlEnabled: Boolean = false,
    val privilegedMode: String = "auto",
    val isDeviceShellEnabled: Boolean = true,
    val isSdcardAccessEnabled: Boolean = true,
    val isUiAutomationEnabled: Boolean = false,
    /** Whether the floating control window is currently shown. */
    val isOverlayEnabled: Boolean = false,
    /** Whether the OS has granted "display over other apps". */
    val canShowOverlay: Boolean = false,
    val deviceControlStatus: DeviceControlStatus? = null,
    val isProbingDeviceControl: Boolean = false,
    // Goals (目标/计划)
    val goals: ImmutableList<Goal> = persistentListOf(),
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
    data class Skill(val id: String) : PendingDeletion
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
    /**
     * True when this model is on the provider's free tier according to the
     * curated [com.kainext.duo.data.FreeTierModels] catalog
     * (currently Ollama Cloud and OpenRouter only).
     */
    val isFreeTier: Boolean = false,
    /**
     * True when this entry was typed by the user and is not (yet) present in the
     * provider's model list. At most one manual entry is kept; retyping replaces it
     * instead of accumulating intermediate keystrokes in the picker.
     */
    val isManualEntry: Boolean = false,
    /** Per-model thinking/reasoning effort. Defaults to MEDIUM for models that support it. */
    val thinkingEffort: ThinkingEffort = ThinkingEffort.MEDIUM,
)
