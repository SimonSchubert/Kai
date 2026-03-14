package com.inspiredandroid.kai.data

import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.ic_service_anthropic
import kai.composeapp.generated.resources.ic_service_cerebras
import kai.composeapp.generated.resources.ic_service_deepseek
import kai.composeapp.generated.resources.ic_service_gemini
import kai.composeapp.generated.resources.ic_service_groqcloud
import kai.composeapp.generated.resources.ic_service_longcat
import kai.composeapp.generated.resources.ic_service_mistral
import kai.composeapp.generated.resources.ic_service_nvidia
import kai.composeapp.generated.resources.ic_service_ollamacloud
import kai.composeapp.generated.resources.ic_service_openai
import kai.composeapp.generated.resources.ic_service_openai_compatible
import kai.composeapp.generated.resources.ic_service_openrouter
import kai.composeapp.generated.resources.ic_service_xai
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

data class ModelDefinition(
    val id: String,
    val subtitle: String,
    val descriptionRes: StringResource? = null,
)

sealed class Service(
    val id: String,
    val displayName: String,
    val icon: DrawableResource,
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
        icon = Res.drawable.ic_service_openai_compatible,
        requiresApiKey = false,
        defaultModel = null,
        settingsKeyPrefix = "",
        chatUrl = "https://proxy-api-amber.vercel.app/chat/completions",
        modelsUrl = null,
    )

    data object Groq : Service(
        id = "groqcloud",
        displayName = "GroqCloud",
        icon = Res.drawable.ic_service_groqcloud,
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
        icon = Res.drawable.ic_service_xai,
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
        icon = Res.drawable.ic_service_openrouter,
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
        icon = Res.drawable.ic_service_nvidia,
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
        icon = Res.drawable.ic_service_gemini,
        requiresApiKey = true,
        defaultModel = null,
        settingsKeyPrefix = "gemini",
        chatUrl = "https://generativelanguage.googleapis.com/v1beta/models/",
        modelsUrl = null,
        defaultModels = emptyList(),
        apiKeyUrl = "https://aistudio.google.com/apikey",
        apiKeyUrlDisplay = "aistudio.google.com/apikey",
    )

    data object Anthropic : Service(
        id = "anthropic",
        displayName = "Anthropic",
        icon = Res.drawable.ic_service_anthropic,
        requiresApiKey = true,
        defaultModel = null,
        settingsKeyPrefix = "anthropic",
        chatUrl = "https://api.anthropic.com/v1/messages",
        modelsUrl = "https://api.anthropic.com/v1/models",
        apiKeyUrl = "https://console.anthropic.com/settings/keys",
        apiKeyUrlDisplay = "console.anthropic.com/settings/keys",
    )

    data object OpenAI : Service(
        id = "openai",
        displayName = "OpenAI",
        icon = Res.drawable.ic_service_openai,
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
        icon = Res.drawable.ic_service_deepseek,
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
        icon = Res.drawable.ic_service_mistral,
        requiresApiKey = true,
        defaultModel = null,
        settingsKeyPrefix = "mistral",
        chatUrl = "https://api.mistral.ai/v1/chat/completions",
        modelsUrl = "https://api.mistral.ai/v1/models",
        apiKeyUrl = "https://console.mistral.ai/api-keys",
        apiKeyUrlDisplay = "console.mistral.ai/api-keys",
    )

    data object Cerebras : Service(
        id = "cerebras",
        displayName = "Cerebras",
        icon = Res.drawable.ic_service_cerebras,
        requiresApiKey = true,
        defaultModel = null,
        settingsKeyPrefix = "cerebras",
        chatUrl = "https://api.cerebras.ai/v1/chat/completions",
        modelsUrl = "https://api.cerebras.ai/v1/models",
        apiKeyUrl = "https://cloud.cerebras.ai/",
        apiKeyUrlDisplay = "cloud.cerebras.ai",
    )

    data object OllamaCloud : Service(
        id = "ollamacloud",
        displayName = "Ollama Cloud",
        icon = Res.drawable.ic_service_ollamacloud,
        requiresApiKey = true,
        defaultModel = null,
        settingsKeyPrefix = "ollamacloud",
        chatUrl = "https://ollama.com/v1/chat/completions",
        modelsUrl = "https://ollama.com/v1/models",
        apiKeyUrl = "https://ollama.com/settings/keys",
        apiKeyUrlDisplay = "ollama.com/settings/keys",
    )

    data object LongCat : Service(
        id = "longcat",
        displayName = "LongCat",
        icon = Res.drawable.ic_service_longcat,
        requiresApiKey = true,
        defaultModel = "LongCat-Flash-Lite",
        settingsKeyPrefix = "longcat",
        chatUrl = "https://api.longcat.chat/openai/v1/chat/completions",
        modelsUrl = null,
        defaultModels = listOf(
            ModelDefinition(id = "LongCat-Flash-Chat", subtitle = "LongCat"),
            ModelDefinition(id = "LongCat-Flash-Thinking", subtitle = "LongCat"),
            ModelDefinition(id = "LongCat-Flash-Thinking-2601", subtitle = "LongCat"),
            ModelDefinition(id = "LongCat-Flash-Lite", subtitle = "LongCat"),
            ModelDefinition(id = "LongCat-Flash-Omni-2603", subtitle = "LongCat"),
        ),
        apiKeyUrl = "https://longcat.chat/platform",
        apiKeyUrlDisplay = "longcat.chat/platform",
    )

    data object OpenAICompatible : Service(
        id = "openai-compatible",
        displayName = "OpenAI-Compatible API",
        icon = Res.drawable.ic_service_openai_compatible,
        requiresApiKey = false,
        supportsOptionalApiKey = true,
        defaultModel = null,
        settingsKeyPrefix = "openai-compatible",
        chatUrl = "/chat/completions",
        modelsUrl = "/models",
        sortModelsById = true,
        includeModelDate = false,
    )

    companion object {
        val all: List<Service> get() = listOf(Free, Gemini, Anthropic, OpenAI, DeepSeek, Mistral, XAI, OpenRouter, Groq, Nvidia, Cerebras, OllamaCloud, LongCat, OpenAICompatible)

        const val DEFAULT_OPENAI_COMPATIBLE_BASE_URL = "http://localhost:11434/v1"

        fun fromId(id: String): Service = all.find { it.id == id } ?: Free
    }

    val apiKeyKey: String get() = "service_${settingsKeyPrefix}_api_key"
    val modelIdKey: String get() = "service_${settingsKeyPrefix}_model_id"
    val baseUrlKey: String get() = "service_${settingsKeyPrefix}_base_url"
}
