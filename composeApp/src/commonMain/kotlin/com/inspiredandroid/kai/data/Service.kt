package com.inspiredandroid.kai.data

import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.gemini_model_2_5_flash_description
import kai.composeapp.generated.resources.gemini_model_2_5_flash_lite_description
import kai.composeapp.generated.resources.gemini_model_2_5_pro_description
import kai.composeapp.generated.resources.gemini_model_3_flash_description
import kai.composeapp.generated.resources.gemini_model_3_pro_preview_description
import org.jetbrains.compose.resources.StringResource

data class ModelDefinition(
    val id: String,
    val subtitle: String,
    val descriptionRes: StringResource? = null,
)

sealed class Service(
    val id: String,
    val displayName: String,
    val requiresApiKey: Boolean,
    val defaultModel: String?,
    val settingsKeyPrefix: String,
    val defaultModels: List<ModelDefinition> = emptyList(),
    val chatUrl: String,
    val modelsUrl: String? = null,
) {
    data object Free : Service(
        id = "free",
        displayName = "Free",
        requiresApiKey = false,
        defaultModel = null,
        settingsKeyPrefix = "",
        chatUrl = "https://proxy-api-amber.vercel.app/chat",
        modelsUrl = null,
    )

    data object Groq : Service(
        id = "groqcloud",
        displayName = "GroqCloud",
        requiresApiKey = true,
        defaultModel = "llama-3.3-70b-versatile",
        settingsKeyPrefix = "groq",
        defaultModels = listOf(
            ModelDefinition(
                id = "llama-3.3-70b-versatile",
                subtitle = "Meta",
            ),
        ),
        chatUrl = "https://api.groq.com/openai/v1/chat/completions",
        modelsUrl = "https://api.groq.com/openai/v1/models",
    )

    data object Gemini : Service(
        id = "gemini",
        displayName = "Gemini",
        requiresApiKey = true,
        defaultModel = "gemini-3-pro-preview",
        settingsKeyPrefix = "gemini",
        chatUrl = "https://generativelanguage.googleapis.com/v1beta/models/",
        modelsUrl = null,
        defaultModels = listOf(
            ModelDefinition(
                id = "gemini-3-pro-preview",
                subtitle = "Gemini 3 Pro",
                descriptionRes = Res.string.gemini_model_3_pro_preview_description,
            ),
            ModelDefinition(
                id = "gemini-3-flash-preview",
                subtitle = "Gemini 3 Flash",
                descriptionRes = Res.string.gemini_model_3_flash_description,
            ),
            ModelDefinition(
                id = "gemini-2.5-flash",
                subtitle = "Gemini 2.5 Flash",
                descriptionRes = Res.string.gemini_model_2_5_flash_description,
            ),
            ModelDefinition(
                id = "gemini-2.5-flash-lite",
                subtitle = "Gemini 2.5 Flash Lite",
                descriptionRes = Res.string.gemini_model_2_5_flash_lite_description,
            ),
            ModelDefinition(
                id = "gemini-2.5-pro",
                subtitle = "Gemini 2.5 Pro",
                descriptionRes = Res.string.gemini_model_2_5_pro_description,
            ),
        ),
    )

    companion object {
        val all: List<Service> get() = listOf(Free, Gemini, Groq)

        fun fromId(id: String): Service = all.find { it.id == id } ?: Free
    }

    val apiKeyKey: String get() = "service_${settingsKeyPrefix}_api_key"
    val modelIdKey: String get() = "service_${settingsKeyPrefix}_model_id"
}
