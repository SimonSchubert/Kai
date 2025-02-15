package com.inspiredandroid.kai.ui.settings

data class SettingsUiState(
    val groqApiKey: String = "",
    val geminiApiKey: String = "",
    val groqModels: List<SettingsModel> = emptyList(),
    val groqSelectedModel: SettingsModel? = null,
    val geminiModels: List<SettingsModel> = emptyList(),
    val geminiSelectedModel: SettingsModel? = null,
    val onClickGroqModel: (String) -> Unit = {},
    val onClickGeminiModel: (String) -> Unit = {},
    val services: List<Service> = emptyList(),
    val onClickService: (String) -> Unit = {},
    val onChangeGroqApiKey: (String) -> Unit = {},
    val onChangeGeminiApiKey: (String) -> Unit = {},
) {
    data class Service(
        val id: String,
        val name: String,
        val isSelected: Boolean = false,
    )
    data class SettingsModel(
        val id: String,
        val subtitle: String,
        val description: String,
        val isSelected: Boolean = false,
    )
}
