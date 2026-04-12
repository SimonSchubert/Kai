package com.inspiredandroid.kai.network.dtos.gemini

import kotlinx.serialization.Serializable

@Serializable
data class GeminiModelsResponseDto(
    val models: List<Model>,
) {
    @Serializable
    data class Model(
        val name: String,
        val version: String? = null,
        val displayName: String? = null,
        val description: String? = null,
        val inputTokenLimit: Long? = null,
        val outputTokenLimit: Long? = null,
        val supportedGenerationMethods: List<String>? = null,
    )
}
