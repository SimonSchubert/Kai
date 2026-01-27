package com.inspiredandroid.kai.network.dtos.ollama

import kotlinx.serialization.Serializable

@Serializable
data class OllamaModelsResponseDto(
    val models: List<Model>,
) {
    @Serializable
    data class Model(
        val name: String,
        val model: String,
        val modified_at: String? = null,
        val size: Long? = null,
        val digest: String? = null,
        val details: Details? = null,
    ) {
        @Serializable
        data class Details(
            val parent_model: String? = null,
            val format: String? = null,
            val family: String? = null,
            val families: List<String>? = null,
            val parameter_size: String? = null,
            val quantization_level: String? = null,
        )
    }
}
