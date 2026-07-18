package com.inspiredandroid.kai.data

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
 * True if the model accepts image input. Defaults to `true` for unknown models — most modern
 * flagship models are multimodal, and stripping images from a model that supports them is a
 * worse failure than leaving the rare unknown text-only model to reject them.
 *
 * DeepSeek is matched by family prefix: its chat API has no vision models at all, so this also
 * covers DeepSeek reached through an aggregator and any future DeepSeek id. GLM is matched
 * exactly via [TEXT_ONLY_IMAGE_MODELS] because its vision variants share a prefix with the text ones.
 */
internal fun modelSupportsImages(modelId: String): Boolean {
    val key = modelId.substringAfterLast('/').lowercase()
    // DeepSeek's chat models are all text-only; the lone vision family is DeepSeek-VL, which
    // carries "vl" in the id. Match the text family by prefix so this also covers DeepSeek via
    // aggregators and future ids, while leaving DeepSeek-VL recognised as multimodal.
    if (key.startsWith("deepseek") && !key.contains("vl")) return false
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
