package com.inspiredandroid.kai.ui.settings

import androidx.compose.runtime.Immutable
import com.inspiredandroid.kai.data.Service
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

@Immutable
data class SettingsUiState(
    val currentService: Service = Service.Free,
    val services: List<Service> = Service.all,
    val apiKey: String = "",
    val baseUrl: String = "",
    val models: List<SettingsModel> = emptyList(),
    val selectedModel: SettingsModel? = null,
    val connectionStatus: ConnectionStatus = ConnectionStatus.Unknown,
    val onSelectService: (Service) -> Unit = {},
    val onChangeApiKey: (String) -> Unit = {},
    val onChangeBaseUrl: (String) -> Unit = {},
    val onSelectModel: (String) -> Unit = {},
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
