package com.inspiredandroid.kai.network.dtos.openaicompatible

import kotlinx.serialization.Serializable

@Serializable
data class OpenAICompatibleModelResponseDto(
    val data: List<Model>,
) {
    @Serializable
    data class Model(
        val id: String,
        val owned_by: String? = null,
        val isActive: Boolean? = true,
        val created: Long? = null,
        val context_window: Long? = null,
        val isSelected: Boolean = false,
    )
}
