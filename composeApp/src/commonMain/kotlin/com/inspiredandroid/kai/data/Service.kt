package com.inspiredandroid.kai.data

import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.ic_service_aihubmix
import kai.composeapp.generated.resources.ic_service_anthropic
import kai.composeapp.generated.resources.ic_service_atlascloud
import kai.composeapp.generated.resources.ic_service_cerebras
import kai.composeapp.generated.resources.ic_service_deepinfra
import kai.composeapp.generated.resources.ic_service_deepseek
import kai.composeapp.generated.resources.ic_service_fireworksai
import kai.composeapp.generated.resources.ic_service_free_expert
import kai.composeapp.generated.resources.ic_service_free_fast
import kai.composeapp.generated.resources.ic_service_gemini
import kai.composeapp.generated.resources.ic_service_groqcloud
import kai.composeapp.generated.resources.ic_service_huggingface
import kai.composeapp.generated.resources.ic_service_litert
import kai.composeapp.generated.resources.ic_service_longcat
import kai.composeapp.generated.resources.ic_service_minimax
import kai.composeapp.generated.resources.ic_service_mistral
import kai.composeapp.generated.resources.ic_service_moonshot
import kai.composeapp.generated.resources.ic_service_nvidia
import kai.composeapp.generated.resources.ic_service_ollamacloud
import kai.composeapp.generated.resources.ic_service_openai
import kai.composeapp.generated.resources.ic_service_openai_compatible
import kai.composeapp.generated.resources.ic_service_opencode
import kai.composeapp.generated.resources.ic_service_openrouter
import kai.composeapp.generated.resources.ic_service_publicai
import kai.composeapp.generated.resources.ic_service_together
import kai.composeapp.generated.resources.ic_service_venice
import kai.composeapp.generated.resources.ic_service_xai
import kai.composeapp.generated.resources.ic_service_zai
import kai.composeapp.generated.resources.service_free_expert
import kai.composeapp.generated.resources.service_free_fast
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

enum class FreeMode(val modelId: String, val nameRes: StringResource, val icon: DrawableResource) {
    FAST("fast", Res.string.service_free_fast, Res.drawable.ic_service_free_fast),
    EXPERT("expert", Res.string.service_free_expert, Res.drawable.ic_service_free_expert),
    ;

    val instanceId: String get() = "free-$modelId"
}

data class ModelDefinition(
    val id: String,
    val subtitle: String,
    val descriptionRes: StringResource? = null,
)

/**
 * How a service handles a `reasoning_content` field on outgoing assistant messages.
 *
 * Default is [NONE] so any new provider is safe by default — Groq and Cerebras
 * return HTTP 400 when they see this field, so opt-in is the correct posture.
 * See `docs/features/reasoning.md` for the authoritative per-provider matrix.
 */
enum class ReasoningRequestMode {
    /** Strip the field before sending. Safe default. */
    NONE,

    /**
     * Echo `reasoning_content` back on assistant turns that previously produced
     * `tool_calls`. Truly required by Z.AI Coding Plan, OpenCode Zen (DeepSeek
     * route), and Moonshot kimi-k2.6 with `thinking.keep="all"`. Accepted as a
     * documented field by Fireworks, Z.AI standard, and OpenRouter (as an alias
     * for `reasoning`). Tolerated as an unknown field by LongCat, Venice, MiniMax.
     *
     * See `docs/features/reasoning.md` for the authoritative per-provider matrix
     * and known fidelity gaps (`reasoning_details`, `<think>`-in-content, paired
     * flags like `clear_thinking` and `reasoning_history`).
     */
    REASONING_CONTENT,
}

sealed class Service(
    val id: String,
    val displayName: String,
    val icon: DrawableResource,
    val requiresApiKey: Boolean,
    val supportsOptionalApiKey: Boolean = false,
    val defaultModel: String?,
    val settingsKeyPrefix: String,
    val defaultModels: List<ModelDefinition> = emptyList(),
    val chatUrl: String = "",
    val modelsUrl: String? = null,
    val modelsResponseIsArray: Boolean = false,
    val filterActiveStrictly: Boolean = false,
    val filterByModelType: Boolean = false,
    val sortModelsById: Boolean = false,
    val apiKeyUrl: String? = null,
    val apiKeyUrlDisplay: String? = null,
    val isOnDevice: Boolean = false,
    val supportsPdf: Boolean = false,
    val supportsImages: Boolean = true,
    val reasoningRequestMode: ReasoningRequestMode = ReasoningRequestMode.NONE,
) {
    data object Free : Service(
        id = "free",
        displayName = "Free",
        icon = Res.drawable.ic_service_free_fast,
        requiresApiKey = false,
        defaultModel = null,
        settingsKeyPrefix = "",
        chatUrl = "https://api.kai9000.com/chat/completions",
        modelsUrl = null,
        // The kai9000 proxy fans out to a Mistral → Groq → OpenRouter chain. The Groq
        // fallback uses text-only models (gpt-oss-20b/120b) that reject content-parts
        // payloads, so images can't be promised reliably on this path.
        supportsImages = false,
    )

    data object AtlasCloud : Service(
        id = "atlascloud",
        displayName = "Atlas Cloud",
        icon = Res.drawable.ic_service_atlascloud,
        requiresApiKey = true,
        defaultModel = null,
        settingsKeyPrefix = "atlascloud",
        chatUrl = "https://api.atlascloud.ai/v1/chat/completions",
        modelsUrl = "https://api.atlascloud.ai/v1/models",
        apiKeyUrl = "https://www.atlascloud.ai/console/api-keys",
        apiKeyUrlDisplay = "atlascloud.ai/console/api-keys",
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
        supportsPdf = true,
        reasoningRequestMode = ReasoningRequestMode.REASONING_CONTENT,
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
        supportsPdf = true,
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
        supportsPdf = true,
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
        supportsPdf = true,
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
        reasoningRequestMode = ReasoningRequestMode.REASONING_CONTENT,
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
        modelsUrl = "https://api.longcat.chat/openai/v1/models",
        defaultModels = listOf(
            ModelDefinition(id = "LongCat-Flash-Chat", subtitle = "LongCat"),
            ModelDefinition(id = "LongCat-Flash-Thinking", subtitle = "LongCat"),
            ModelDefinition(id = "LongCat-Flash-Thinking-2601", subtitle = "LongCat"),
            ModelDefinition(id = "LongCat-Flash-Lite", subtitle = "LongCat"),
            ModelDefinition(id = "LongCat-Flash-Omni-2603", subtitle = "LongCat"),
        ),
        apiKeyUrl = "https://longcat.chat/platform",
        apiKeyUrlDisplay = "longcat.chat/platform",
        reasoningRequestMode = ReasoningRequestMode.REASONING_CONTENT,
    )

    data object Together : Service(
        id = "together",
        displayName = "Together AI",
        icon = Res.drawable.ic_service_together,
        requiresApiKey = true,
        defaultModel = null,
        settingsKeyPrefix = "together",
        chatUrl = "https://api.together.xyz/v1/chat/completions",
        modelsUrl = "https://api.together.xyz/v1/models",
        modelsResponseIsArray = true,
        filterByModelType = true,
        apiKeyUrl = "https://api.together.ai/settings/api-keys",
        apiKeyUrlDisplay = "api.together.ai/settings/api-keys",
    )

    data object HuggingFace : Service(
        id = "huggingface",
        displayName = "Hugging Face",
        icon = Res.drawable.ic_service_huggingface,
        requiresApiKey = true,
        defaultModel = null,
        settingsKeyPrefix = "huggingface",
        chatUrl = "https://router.huggingface.co/v1/chat/completions",
        modelsUrl = "https://router.huggingface.co/v1/models",
        apiKeyUrl = "https://huggingface.co/settings/tokens",
        apiKeyUrlDisplay = "huggingface.co/settings/tokens",
    )

    data object Venice : Service(
        id = "venice",
        displayName = "Venice AI",
        icon = Res.drawable.ic_service_venice,
        requiresApiKey = true,
        defaultModel = null,
        settingsKeyPrefix = "venice",
        chatUrl = "https://api.venice.ai/api/v1/chat/completions",
        modelsUrl = "https://api.venice.ai/api/v1/models",
        apiKeyUrl = "https://venice.ai/settings/api?ref=DsZFKZ",
        apiKeyUrlDisplay = "venice.ai/settings/api",
        reasoningRequestMode = ReasoningRequestMode.REASONING_CONTENT,
    )

    data object Moonshot : Service(
        id = "moonshot",
        displayName = "Moonshot AI",
        icon = Res.drawable.ic_service_moonshot,
        requiresApiKey = true,
        defaultModel = null,
        settingsKeyPrefix = "moonshot",
        chatUrl = "https://api.moonshot.cn/v1/chat/completions",
        modelsUrl = "https://api.moonshot.cn/v1/models",
        apiKeyUrl = "https://platform.moonshot.cn/console/api-keys",
        apiKeyUrlDisplay = "platform.moonshot.cn/console/api-keys",
        reasoningRequestMode = ReasoningRequestMode.REASONING_CONTENT,
    )

    data object Zai : Service(
        id = "zai",
        displayName = "Z.AI",
        icon = Res.drawable.ic_service_zai,
        requiresApiKey = true,
        defaultModel = null,
        settingsKeyPrefix = "zai",
        chatUrl = "https://api.z.ai/api/paas/v4/chat/completions",
        modelsUrl = "https://api.z.ai/api/paas/v4/models",
        apiKeyUrl = "https://z.ai/manage-apikey/apikey-list",
        apiKeyUrlDisplay = "z.ai/manage-apikey/apikey-list",
        reasoningRequestMode = ReasoningRequestMode.REASONING_CONTENT,
    )

    data object ZaiCodingPlan : Service(
        id = "zai-coding-plan",
        displayName = "Z.AI Coding Plan",
        icon = Res.drawable.ic_service_zai,
        requiresApiKey = true,
        defaultModel = null,
        settingsKeyPrefix = "zai-coding-plan",
        chatUrl = "https://api.z.ai/api/coding/paas/v4/chat/completions",
        modelsUrl = "https://api.z.ai/api/coding/paas/v4/models",
        apiKeyUrl = "https://z.ai/manage-apikey/apikey-list",
        apiKeyUrlDisplay = "z.ai/manage-apikey/apikey-list",
        reasoningRequestMode = ReasoningRequestMode.REASONING_CONTENT,
    )

    data object Minimax : Service(
        id = "minimax",
        displayName = "MiniMax",
        icon = Res.drawable.ic_service_minimax,
        requiresApiKey = true,
        defaultModel = null,
        settingsKeyPrefix = "minimax",
        chatUrl = "https://api.minimax.io/v1/chat/completions",
        modelsUrl = "https://api.minimax.io/v1/models",
        apiKeyUrl = "https://platform.minimax.io",
        apiKeyUrlDisplay = "platform.minimax.io",
        reasoningRequestMode = ReasoningRequestMode.REASONING_CONTENT,
    )

    data object AiHubMix : Service(
        id = "aihubmix",
        displayName = "AIHubMix",
        icon = Res.drawable.ic_service_aihubmix,
        requiresApiKey = true,
        defaultModel = null,
        settingsKeyPrefix = "aihubmix",
        chatUrl = "https://aihubmix.com/v1/chat/completions",
        modelsUrl = "https://aihubmix.com/v1/models",
        apiKeyUrl = "https://aihubmix.com/token",
        apiKeyUrlDisplay = "aihubmix.com/token",
    )

    data object DeepInfra : Service(
        id = "deepinfra",
        displayName = "Deep Infra",
        icon = Res.drawable.ic_service_deepinfra,
        requiresApiKey = true,
        defaultModel = null,
        settingsKeyPrefix = "deepinfra",
        chatUrl = "https://api.deepinfra.com/v1/openai/chat/completions",
        modelsUrl = "https://api.deepinfra.com/v1/openai/models",
        apiKeyUrl = "https://deepinfra.com/dash/api_keys",
        apiKeyUrlDisplay = "deepinfra.com/dash/api_keys",
    )

    data object FireworksAI : Service(
        id = "fireworksai",
        displayName = "Fireworks AI",
        icon = Res.drawable.ic_service_fireworksai,
        requiresApiKey = true,
        defaultModel = null,
        settingsKeyPrefix = "fireworksai",
        chatUrl = "https://api.fireworks.ai/inference/v1/chat/completions",
        modelsUrl = "https://api.fireworks.ai/inference/v1/models",
        apiKeyUrl = "https://app.fireworks.ai/settings/users/api-keys",
        apiKeyUrlDisplay = "app.fireworks.ai/settings/users/api-keys",
        reasoningRequestMode = ReasoningRequestMode.REASONING_CONTENT,
    )

    data object OpenCode : Service(
        id = "opencode",
        displayName = "OpenCode",
        icon = Res.drawable.ic_service_opencode,
        requiresApiKey = true,
        defaultModel = null,
        settingsKeyPrefix = "opencode",
        chatUrl = "https://opencode.ai/zen/v1/chat/completions",
        modelsUrl = "https://opencode.ai/zen/v1/models",
        apiKeyUrl = "https://opencode.ai/docs/zen/",
        apiKeyUrlDisplay = "opencode.ai/docs/zen",
        reasoningRequestMode = ReasoningRequestMode.REASONING_CONTENT,
    )

    data object PublicAI : Service(
        id = "publicai",
        displayName = "Public AI",
        icon = Res.drawable.ic_service_publicai,
        requiresApiKey = true,
        defaultModel = null,
        settingsKeyPrefix = "publicai",
        chatUrl = "https://api.publicai.co/v1/chat/completions",
        modelsUrl = "https://api.publicai.co/v1/models",
        apiKeyUrl = "https://platform.publicai.co",
        apiKeyUrlDisplay = "platform.publicai.co",
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
    )

    data object LiteRT : Service(
        id = "litert",
        displayName = "Local Model",
        icon = Res.drawable.ic_service_litert,
        requiresApiKey = false,
        defaultModel = null,
        settingsKeyPrefix = "litert",
        isOnDevice = true,
    )

    companion object {
        val all: List<Service> get() = listOf(Free, AtlasCloud, Gemini, Anthropic, OpenAI, DeepSeek, Mistral, XAI, OpenRouter, Groq, Nvidia, Cerebras, OllamaCloud, LongCat, Together, HuggingFace, Venice, Moonshot, Zai, ZaiCodingPlan, Minimax, AiHubMix, DeepInfra, FireworksAI, OpenCode, PublicAI, OpenAICompatible, LiteRT)

        const val DEFAULT_OPENAI_COMPATIBLE_BASE_URL = "http://localhost:11434/v1"

        fun fromId(id: String): Service = all.find { it.id == id } ?: Free
    }

    val apiKeyKey: String get() = "service_${settingsKeyPrefix}_api_key"
    val modelIdKey: String get() = "service_${settingsKeyPrefix}_model_id"
    val baseUrlKey: String get() = "service_${settingsKeyPrefix}_base_url"
}
