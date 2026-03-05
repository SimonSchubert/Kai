package com.inspiredandroid.kai.ui.settings

import androidx.compose.runtime.Immutable
import com.inspiredandroid.kai.data.EmailAccount
import com.inspiredandroid.kai.data.HeartbeatLogEntry
import com.inspiredandroid.kai.data.MemoryEntry
import com.inspiredandroid.kai.data.ScheduledTask
import com.inspiredandroid.kai.data.Service
import com.inspiredandroid.kai.network.tools.ToolInfo
import org.jetbrains.compose.resources.StringResource

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
}

@Immutable
data class SettingsUiState(
    val currentTab: SettingsTab = SettingsTab.Services,
    val currentService: Service = Service.Free,
    val services: List<Service> = Service.all,
    val apiKey: String = "",
    val baseUrl: String = "",
    val models: List<SettingsModel> = emptyList(),
    val selectedModel: SettingsModel? = null,
    val connectionStatus: ConnectionStatus = ConnectionStatus.Unknown,
    val tools: List<ToolInfo> = emptyList(),
    val onSelectTab: (SettingsTab) -> Unit = {},
    val onSelectService: (Service) -> Unit = {},
    val onChangeApiKey: (String) -> Unit = {},
    val onChangeBaseUrl: (String) -> Unit = {},
    val onSelectModel: (String) -> Unit = {},
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
    val heartbeatPrompt: String = "",
    val heartbeatLog: List<HeartbeatLogEntry> = emptyList(),
    val onToggleHeartbeat: (Boolean) -> Unit = {},
    val onSaveHeartbeatPrompt: (String) -> Unit = {},
    val isEmailEnabled: Boolean = true,
    val showEmailToggle: Boolean = false,
    val emailAccounts: List<EmailAccount> = emptyList(),
    val emailPollIntervalMinutes: Int = 15,
    val onToggleEmail: (Boolean) -> Unit = {},
    val onRemoveEmailAccount: (String) -> Unit = {},
    val onChangeEmailPollInterval: (Int) -> Unit = {},
)

@Immutable
data class SettingsModel(
    val id: String,
    val subtitle: String,
    val description: String? = null,
    val descriptionRes: StringResource? = null,
    val isSelected: Boolean = false,
)
