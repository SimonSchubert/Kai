package com.inspiredandroid.kai.ui.settings

import androidx.compose.runtime.Immutable
import com.inspiredandroid.kai.data.Identity
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
    val showTopics: Boolean = true,
    val onToggleShowTopics: (Boolean) -> Unit = {},
    val identities: List<Identity> = emptyList(),
    val selectedIdentity: Identity? = null,
    val onSelectIdentity: (String) -> Unit = {},
    val onSaveIdentity: (Identity) -> Unit = {},
    val onDeleteIdentity: (String) -> Unit = {},
    val onResetIdentity: (String) -> Unit = {},
)

@Immutable
data class SettingsModel(
    val id: String,
    val subtitle: String,
    val description: String? = null,
    val descriptionRes: StringResource? = null,
    val isSelected: Boolean = false,
    val createdAt: Long = 0,
)
