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
    "gemma-4-e2b",
    "gemma-4-e4b",
    "phi3:mini",
    "tinyllama",
    "stablelm",
    "codellama",
    "deepseek-coder:1.3b",
    "deepseek-coder:6.7b",
)

/** True if the model can handle tool_use / tool_result round-trips. */
internal fun supportsTools(modelId: String): Boolean {
    val lower = modelId.lowercase()
    return LIMITED_MODELS.none { lower.startsWith(it) }
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
