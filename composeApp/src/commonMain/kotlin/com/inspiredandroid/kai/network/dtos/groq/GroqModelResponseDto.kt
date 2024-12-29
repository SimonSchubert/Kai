package com.inspiredandroid.kai.network.dtos.groq

import kotlinx.serialization.Serializable

@Serializable
data class GroqModelResponseDto(
    val data: List<Model>,
) {
    @Serializable
    data class Model(
        val id: String,
        val owned_by: String? = null,
        val isActive: Boolean? = true,
        val context_window: Long? = null,
        val isSelected: Boolean = false,
    )
}
