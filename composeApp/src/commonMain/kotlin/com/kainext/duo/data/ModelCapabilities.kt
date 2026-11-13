package com.kainext.duo.data

/**
 * Model IDs (by prefix, lower-cased) that can't drive tool/function-calling loops
 * reliably. Add new small/weak models here when they prove unable to emit well-formed
 * tool_use JSON or to parse tool schemas.
 */
internal val LIMITED_MODELS = listOf(
    "llama3.2:1b",
    "llama3.2:3b",
    "llama3.1:8b",
    "gemma2",
    "gemma:2b",
    "gemma:7b",
    "gemma3",
    "gemma-3",
    "gemma-4-e2b",
    "gemma-4-e4b",
    "phi3:mini",
    "tinyllama",
    "stablelm",
    "codellama",
    "deepseek-coder:1.3b",
    "deepseek-coder:6.7b",
    // Perplexity Sonar models use built-in web search, not OpenAI tool/function calling.
    // Sending a tools[] array is rejected (or silently ignored), so treat them as text-only.
    "sonar",
)

/** True if the model can handle tool_use / tool_result round-trips. */
internal fun supportsTools(modelId: String): Boolean {
    val lower = modelId.lowercase()
    return LIMITED_MODELS.none { lower.startsWith(it) }
}

/**
 * GLM / Zhipu text model ids (normalized: any `provider/` prefix stripped, lower-cased) that
 * DON'T accept image input even though Z.AI's service does — Z.AI serves these text models next
 * to the multimodal GLM-V variants. Sending image content-parts to them returns a hard 400 that
 * then poisons every later turn in the chat (the image stays in history), so we strip images.
 *
 * Exact match only: the vision variants (glm-4.6v, glm-4v-plus, …) share a prefix with the text
 * ones (glm-4.6, glm-4), so prefix matching would wrongly flag them as text-only. DeepSeek, which
 * has no vision models at all, is matched by family prefix in [modelSupportsImages] instead.
 */
internal val TEXT_ONLY_IMAGE_MODELS: Set<String> = setOf(
    // GLM / Zhipu text models. The -v / v-plus variants are multimodal and are
    // deliberately left out so images still reach them.
    "glm-4.6",
    "glm-4.6-air",
    "glm-4.5",
    "glm-4.5-air",
    "glm-4.5-air:free",
    "glm-4.5-x",
    "glm-4-plus",
    "glm-4-plus-0111",
    "glm-4-air",
    "glm-4-airx",
    "glm-4-long",
    "glm-4-flash",
    "glm-4-32b",
    "glm-z1-airx",
    "glm-z1-air",
    "glm-z1-flash",
    "glm-5",
    "glm5",
    "glm-5.1",
    "glm-5.2",
    "glm5.2",
    "glm-5.2-max",
    "glm-5-turbo",
    "glm-4.7",
    "glm4.7",
    "zai-glm-4.7",
    "glm-4.7-flash",
    "chatglm3-6b",
)

/**
 * DeepSeek model families that accept image input.
 *
 * The old rule treated every DeepSeek chat model as text-only, which was true up to V3 but is
 * no longer: V4 and newer are multimodal. Vision capability is decided by the generation number
 * rather than by an id allow-list, so `deepseek-v4-flash`, `deepseek-v4.1-flash`,
 * `deepseek-v4-pro-thinking` and any future `deepseek-v5…` are all covered without a code change,
 * including the ids aggregators prefix (`openrouter/deepseek/…` — the provider prefix is stripped
 * before matching).
 *
 * `deepseek-vl*` remains recognised through the legacy "vl" check below, and unknown DeepSeek
 * ids keep the text-only default: sending image parts to a text model is a hard 400 that poisons
 * the rest of the conversation, which is the worse failure here.
 */
private val DEEPSEEK_VISION_GENERATION = Regex("""deepseek-(?:v)?(\d+)(?:\.\d+)?""")

internal fun deepseekSupportsImages(key: String): Boolean {
    if (!key.startsWith("deepseek")) return false
    // Legacy DeepSeek-VL family and any id that spells out a vision marker.
    if (key.contains("vl")) return true
    val match = DEEPSEEK_VISION_GENERATION.find(key) ?: return false
    return (match.groupValues[1].toIntOrNull() ?: 0) >= 4
}

/**
 * True if the model accepts image input.
 *
 * Order of authority:
 *  1. What the provider declared in its model listing ([ModelCapabilityRegistry]) — the vendor is
 *     the source of truth for its own model, and this needs no app update when a new generation
 *     ships. OpenRouter-style `architecture.input_modalities` and the flat `modality` string both
 *     feed this.
 *  2. DeepSeek generation check ([deepseekSupportsImages]) — V4+ multimodal, V3-and-older text.
 *  3. [TEXT_ONLY_IMAGE_MODELS] for families whose vision variants share a prefix with text ones.
 *  4. Default `true`: most modern flagship models are multimodal, and stripping images from a
 *     model that supports them is a worse failure than leaving a rare unknown text model to
 *     reject them.
 */
internal fun modelSupportsImages(modelId: String): Boolean {
    ModelCapabilityRegistry.lookup(modelId)?.let { return it.supportsImages }
    val key = modelId.substringAfterLast('/').lowercase()
    if (key.startsWith("deepseek")) return deepseekSupportsImages(key)
    return key !in TEXT_ONLY_IMAGE_MODELS
}

/**
 * True if a service+model combo is suitable for autonomous/agentic flows —
 * heartbeat, interactive mode, and any future background feature that runs a
 * tool-calling loop without the user present to course-correct.
 *
 * Two gates, both must hold:
 *  - **Service** must be remote: on-device inference (LiteRT) can't run long
 *    agentic loops reliably.
 *  - **Model** must support tools: some small open-weight models don't
 *    (see [LIMITED_MODELS]).
 *
 * If you're filtering a service/model picker for a background feature,
 * prefer this over checking either gate in isolation.
 */
internal fun supportsAgenticFlows(serviceId: String, modelId: String): Boolean = !Service.fromId(serviceId).isOnDevice && supportsTools(modelId)

/**
 * OpenAI GPT families from 5.6 on only support function calling on the Responses API
 * (`/v1/responses`). Chat completions answers them with
 * `400 Function tools with reasoning_effort are not supported for gpt-5.6-terra in
 * /v1/chat/completions. To use function tools, use /v1/responses or set reasoning_effort to
 * 'none'.`
 *
 * These families reason by default, so the rejection lands even though Kai never sends
 * `reasoning_effort` — and the documented chat-completions escape hatch (`reasoning_effort:
 * "none"`) would trade away the reasoning these models are chosen for.
 *
 * Matching on the version rather than a list of prefixes means newer families (GPT-6, …) are
 * routed without a code change. Covers the tier ids (`-sol`, `-terra`, `-luna`), bare aliases
 * (`gpt-5.6`, `gpt-6`), and effort-suffixed variants (`gpt-5.6-luna-xhigh`).
 */
private val GPT_VERSION = Regex("""^gpt-(\d+)(?:\.(\d+))?(?:-|$)""")

internal fun isResponsesApiGptFamily(modelId: String): Boolean {
    val match = GPT_VERSION.find(modelId.substringAfterLast('/').lowercase()) ?: return false
    val major = match.groupValues[1].toInt()
    val minor = match.groupValues[2].toIntOrNull() ?: 0
    return major > 5 || (major == 5 && minor >= 6)
}

/**
 * True when this service+model must talk to OpenAI's Responses API instead of chat completions.
 *
 * Gated on reaching OpenAI directly: aggregators that resell the same models (OpenRouter, AI
 * HubMix, …) translate to the Responses API themselves and only accept chat completions, so
 * routing their ids to `/responses` would break them. The OpenAI-Compatible service qualifies when
 * its base URL points at OpenAI, which is what a user reaching for that service as a workaround
 * would configure.
 */
internal fun requiresResponsesApi(service: Service, modelId: String, baseUrl: String = ""): Boolean {
    if (service.responsesUrl == null) return false
    val isOpenAiEndpoint = service == Service.OpenAI || baseUrl.contains("api.openai.com", ignoreCase = true)
    if (!isOpenAiEndpoint) return false
    return isResponsesApiGptFamily(modelId)
}

/**
 * True when [modelId] on [service] accepts a reasoning-effort knob that Kai actually sends.
 *
 * Only two paths carry the value today: the OpenAI Responses API (`reasoning.effort`) and the
 * OpenAI-compatible chat completions body (`reasoning_effort`). Gemini and Anthropic are driven
 * through their own message shapes and never receive it, so the picker hides the control there
 * instead of offering a switch that would do nothing.
 *
 * Reaching OpenAI through an aggregator is a special case: OpenRouter and friends resell models
 * that may or may not honour `reasoning_effort`, and the request is dropped silently when they
 * don't. Those are reported as unsupported so the UI stays honest.
 */
internal fun supportsReasoningEffort(service: Service, modelId: String, baseUrl: String = ""): Boolean {
    if (modelId.isBlank()) return false
    if (service.isOnDevice) return false
    if (service == Service.Gemini || service == Service.Anthropic) return false
    if (requiresResponsesApi(service, modelId, baseUrl)) return true
    return service == Service.OpenAI || service == Service.OpenAICompatible
}
