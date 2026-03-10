package com.inspiredandroid.kai.network.dtos.anthropic

import kotlinx.serialization.Serializable

@Serializable
data class AnthropicModelsResponseDto(
    val data: List<ModelInfo>,
) {
    @Serializable
    data class ModelInfo(
        val id: String,
        val display_name: String? = null,
        val created_at: String? = null,
        val type: String? = null,
    )
}
