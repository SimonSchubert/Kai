package com.inspiredandroid.kai.network.dtos

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
data class SponsorsResponseDto(
    val sponsors: SponsorsData = SponsorsData(),
) {
    @Serializable
    data class SponsorsData(
        val current: List<Sponsor> = emptyList(),
        val past: List<Sponsor> = emptyList(),
    )

    @Immutable
    @Serializable
    data class Sponsor(
        val username: String = "",
        val avatar: String = "",
    )
}
