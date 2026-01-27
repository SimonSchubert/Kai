package com.inspiredandroid.kai.network.dtos.gemini

import kotlinx.serialization.Serializable

@Serializable
data class GeminiModelsResponseDto(
    val models: List<Model>,
) {
    @Serializable
    data class Model(
        val name: String,
        val displayName: String? = null,
        val description: String? = null,
        val supportedGenerationMethods: List<String>? = null,
    )
}
