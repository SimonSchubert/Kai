package com.inspiredandroid.kai.ui.dynamicui

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

@Immutable
@Serializable
sealed interface UiAction

@Immutable
@Serializable
@SerialName("callback")
data class CallbackAction(
    val event: String,
    val data: Map<String, JsonPrimitive>? = null,
    val collectFrom: List<String>? = null,
) : UiAction {
    /** Returns data values coerced to strings (handles booleans/numbers from LLMs). */
    val dataAsStrings: Map<String, String>?
        get() = data?.mapValues { it.value.content }
}

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
