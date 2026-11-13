package com.kainext.duo.data

import androidx.compose.runtime.Immutable
import org.jetbrains.compose.resources.DrawableResource

@Immutable
data class ServiceEntry(
    val instanceId: String,
    val serviceId: String,
    val serviceName: String,
    val modelId: String,
    val icon: DrawableResource,
    /** Context window of [modelId] in tokens, when known — drives the context-usage ratio in chat. */
    val contextWindow: Long? = null,
    /** Reasoning effort currently saved for [modelId]; shown in the in-chat model picker. */
    val thinkingEffort: ThinkingEffort = ThinkingEffort.MEDIUM,
    /** True when the provider/model actually accepts a reasoning-effort knob. */
    val supportsThinkingEffort: Boolean = false,
)
