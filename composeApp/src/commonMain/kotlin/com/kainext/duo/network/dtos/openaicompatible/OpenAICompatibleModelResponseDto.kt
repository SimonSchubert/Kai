package com.kainext.duo.network.dtos.openaicompatible

import kotlinx.serialization.SerialName
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
        /**
         * OpenRouter-style capability block. When a provider publishes the model's accepted
         * input modalities, that declaration is authoritative for whether images may be sent.
         */
        val architecture: Architecture? = null,
        /** OpenRouter also exposes a flat modality string, e.g. "text+image->text". */
        val modality: String? = null,
    ) {
        @Serializable
        data class Architecture(
            /** e.g. ["text", "image"]. */
            @SerialName("input_modalities") val inputModalities: List<String>? = null,
            /** e.g. ["text"]. */
            @SerialName("output_modalities") val outputModalities: List<String>? = null,
        )
    }
}
