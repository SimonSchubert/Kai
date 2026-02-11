package com.inspiredandroid.kai.data

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
    val supportsOptionalApiKey: Boolean = false,
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
        chatUrl = "https://proxy-api-amber.vercel.app/chat/completions",
        modelsUrl = null,
    )

    data object Groq : Service(
        id = "groqcloud",
        displayName = "GroqCloud",
        requiresApiKey = true,
        defaultModel = null,
        settingsKeyPrefix = "groq",
        defaultModels = emptyList(),
        chatUrl = "https://api.groq.com/openai/v1/chat/completions",
        modelsUrl = "https://api.groq.com/openai/v1/models",
    )

    data object XAI : Service(
        id = "xai",
        displayName = "xAI",
        requiresApiKey = true,
        defaultModel = null,
        settingsKeyPrefix = "xai",
        defaultModels = emptyList(),
        chatUrl = "https://api.x.ai/v1/chat/completions",
        modelsUrl = "https://api.x.ai/v1/models",
    )

    data object OpenRouter : Service(
        id = "openrouter",
        displayName = "OpenRouter",
        requiresApiKey = true,
        defaultModel = null,
        settingsKeyPrefix = "openrouter",
        defaultModels = emptyList(),
        chatUrl = "https://openrouter.ai/api/v1/chat/completions",
        modelsUrl = "https://openrouter.ai/api/v1/models",
    )

    data object Nvidia : Service(
        id = "nvidia",
        displayName = "NVIDIA",
        requiresApiKey = true,
        defaultModel = null,
        settingsKeyPrefix = "nvidia",
        defaultModels = emptyList(),
        chatUrl = "https://integrate.api.nvidia.com/v1/chat/completions",
        modelsUrl = "https://integrate.api.nvidia.com/v1/models",
    )

    data object Gemini : Service(
        id = "gemini",
        displayName = "Gemini",
        requiresApiKey = true,
        defaultModel = null,
        settingsKeyPrefix = "gemini",
        chatUrl = "https://generativelanguage.googleapis.com/v1beta/models/",
        modelsUrl = null,
        defaultModels = emptyList(),
    )

    data object OpenAICompatible : Service(
        id = "openai-compatible",
        displayName = "OpenAI-Compatible API",
        requiresApiKey = false,
        supportsOptionalApiKey = true,
        defaultModel = null,
        settingsKeyPrefix = "openai-compatible",
        chatUrl = "/v1/chat/completions",
        modelsUrl = "/api/tags",
    )

    companion object {
        val all: List<Service> get() = listOf(Free, Gemini, XAI, OpenRouter, Groq, Nvidia, OpenAICompatible)

        const val DEFAULT_OPENAI_COMPATIBLE_BASE_URL = "http://localhost:11434"

        fun fromId(id: String): Service = all.find { it.id == id } ?: Free
    }

    val apiKeyKey: String get() = "service_${settingsKeyPrefix}_api_key"
    val modelIdKey: String get() = "service_${settingsKeyPrefix}_model_id"
    val baseUrlKey: String get() = "service_${settingsKeyPrefix}_base_url"
}
