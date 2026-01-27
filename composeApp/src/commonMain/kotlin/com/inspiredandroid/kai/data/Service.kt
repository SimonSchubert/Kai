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
        defaultModel = null,
        settingsKeyPrefix = "groq",
        defaultModels = emptyList(),
        chatUrl = "https://api.groq.com/openai/v1/chat/completions",
        modelsUrl = "https://api.groq.com/openai/v1/models",
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

    data object Ollama : Service(
        id = "ollama",
        displayName = "Ollama",
        requiresApiKey = false,
        defaultModel = null,
        settingsKeyPrefix = "ollama",
        chatUrl = "/v1/chat/completions",
        modelsUrl = "/api/tags",
    )

    companion object {
        val all: List<Service> get() = listOf(Free, Gemini, Groq, Ollama)

        const val DEFAULT_OLLAMA_BASE_URL = "http://localhost:11434"

        fun fromId(id: String): Service = all.find { it.id == id } ?: Free
    }

    val apiKeyKey: String get() = "service_${settingsKeyPrefix}_api_key"
    val modelIdKey: String get() = "service_${settingsKeyPrefix}_model_id"
    val baseUrlKey: String get() = "service_${settingsKeyPrefix}_base_url"
}
