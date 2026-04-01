package com.inspiredandroid.kai.ui.dynamicui

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Immutable
@Serializable
sealed interface UiAction

@Immutable
@Serializable
@SerialName("callback")
data class CallbackAction(
    val event: String,
    val data: Map<String, String>? = null,
    val collectFrom: List<String>? = null,
) : UiAction

@Immutable
@Serializable
@SerialName("toggle")
data class ToggleAction(
    val targetId: String,
) : UiAction

@Immutable
@Serializable
@SerialName("open_url")
data class OpenUrlAction(
    val url: String,
) : UiAction
