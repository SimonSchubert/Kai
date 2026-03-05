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
    val filterActiveStrictly: Boolean = false,
    val sortModelsById: Boolean = false,
    val includeModelDate: Boolean = true,
    val apiKeyUrl: String? = null,
    val apiKeyUrlDisplay: String? = null,
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
        filterActiveStrictly = true,
        apiKeyUrl = "https://console.groq.com/keys",
        apiKeyUrlDisplay = "console.groq.com/keys",
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
        apiKeyUrl = "https://console.x.ai",
        apiKeyUrlDisplay = "console.x.ai",
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
        apiKeyUrl = "https://openrouter.ai/settings/keys",
        apiKeyUrlDisplay = "openrouter.ai/settings/keys",
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
        sortModelsById = true,
        includeModelDate = false,
        apiKeyUrl = "https://build.nvidia.com/settings/api-keys",
        apiKeyUrlDisplay = "build.nvidia.com/settings/api-keys",
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
        apiKeyUrl = "https://aistudio.google.com/apikey",
        apiKeyUrlDisplay = "aistudio.google.com/apikey",
    )

    data object OpenAI : Service(
        id = "openai",
        displayName = "OpenAI",
        requiresApiKey = true,
        defaultModel = null,
        settingsKeyPrefix = "openai",
        chatUrl = "https://api.openai.com/v1/chat/completions",
        modelsUrl = "https://api.openai.com/v1/models",
        apiKeyUrl = "https://platform.openai.com/api-keys",
        apiKeyUrlDisplay = "platform.openai.com/api-keys",
    )

    data object DeepSeek : Service(
        id = "deepseek",
        displayName = "DeepSeek",
        requiresApiKey = true,
        defaultModel = null,
        settingsKeyPrefix = "deepseek",
        chatUrl = "https://api.deepseek.com/chat/completions",
        modelsUrl = "https://api.deepseek.com/models",
        apiKeyUrl = "https://platform.deepseek.com/api_keys",
        apiKeyUrlDisplay = "platform.deepseek.com/api_keys",
    )

    data object Mistral : Service(
        id = "mistral",
        displayName = "Mistral",
        requiresApiKey = true,
        defaultModel = null,
        settingsKeyPrefix = "mistral",
        chatUrl = "https://api.mistral.ai/v1/chat/completions",
        modelsUrl = "https://api.mistral.ai/v1/models",
        apiKeyUrl = "https://console.mistral.ai/api-keys",
        apiKeyUrlDisplay = "console.mistral.ai/api-keys",
    )

    data object OpenAICompatible : Service(
        id = "openai-compatible",
        displayName = "OpenAI-Compatible API",
        requiresApiKey = false,
        supportsOptionalApiKey = true,
        defaultModel = null,
        settingsKeyPrefix = "openai-compatible",
        chatUrl = "/v1/chat/completions",
        modelsUrl = "/v1/models",
        sortModelsById = true,
        includeModelDate = false,
    )

    companion object {
        val all: List<Service> get() = listOf(Free, Gemini, OpenAI, DeepSeek, Mistral, XAI, OpenRouter, Groq, Nvidia, OpenAICompatible)

        const val DEFAULT_OPENAI_COMPATIBLE_BASE_URL = "http://localhost:11434"

        fun fromId(id: String): Service = all.find { it.id == id } ?: Free
    }

    val apiKeyKey: String get() = "service_${settingsKeyPrefix}_api_key"
    val modelIdKey: String get() = "service_${settingsKeyPrefix}_model_id"
    val baseUrlKey: String get() = "service_${settingsKeyPrefix}_base_url"
}
