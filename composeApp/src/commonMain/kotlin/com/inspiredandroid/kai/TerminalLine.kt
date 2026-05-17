package com.inspiredandroid.kai

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Immutable
@Serializable
sealed interface TerminalLine {
    val text: String

    fun withText(newText: String): TerminalLine = when (this) {
        is Command -> Command(newText)
        is Output -> Output(newText)
        is Error -> Error(newText)
    }

    @Serializable
    @SerialName("command")
    data class Command(override val text: String) : TerminalLine

    @Serializable
    @SerialName("output")
    data class Output(override val text: String) : TerminalLine

    @Serializable
    @SerialName("error")
    data class Error(override val text: String) : TerminalLine
}
