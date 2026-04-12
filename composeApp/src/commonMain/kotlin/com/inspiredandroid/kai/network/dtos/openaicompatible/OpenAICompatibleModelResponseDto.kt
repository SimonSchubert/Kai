package com.inspiredandroid.kai.network.dtos.openaicompatible

import kotlinx.serialization.Serializable

@Serializable
data class OpenAICompatibleModelResponseDto(
    val data: List<Model>,
) {
    @Serializable
    data class Model(
        val id: String,
        /** Some providers (notably OpenRouter) include a human-readable name. */
        val name: String? = null,
        val owned_by: String? = null,
        val description: String? = null,
        val isActive: Boolean? = true,
        val created: Long? = null,
        /** Groq-style. */
        val context_window: Long? = null,
        /** OpenRouter-style. */
        val context_length: Long? = null,
        val isSelected: Boolean = false,
        val type: String? = null,
    )
}
