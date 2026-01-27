package com.inspiredandroid.kai.ui.settings

import androidx.compose.runtime.Immutable
import com.inspiredandroid.kai.data.Service
import org.jetbrains.compose.resources.StringResource

@Immutable
data class SettingsUiState(
    val currentService: Service = Service.Free,
    val services: List<Service> = Service.all,
    val apiKey: String = "",
    val models: List<SettingsModel> = emptyList(),
    val selectedModel: SettingsModel? = null,
    val onSelectService: (Service) -> Unit = {},
    val onChangeApiKey: (String) -> Unit = {},
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
