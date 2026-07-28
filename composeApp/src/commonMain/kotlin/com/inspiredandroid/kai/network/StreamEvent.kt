package com.inspiredandroid.kai.network

import com.inspiredandroid.kai.ui.chat.ToolCallInfo

/**
 * Events emitted during a streaming chat completion. The caller drives the
 * stream by collecting these events and updating the UI incrementally.
 */
sealed class StreamEvent {
    /** A text token from the assistant. */
    data class Token(val text: String) : StreamEvent()

    /** A completed tool call extracted from the stream. */
    data class ToolCalls(val calls: List<ToolCallInfo>) : StreamEvent()

    /** The stream completed successfully with the given stop reason. */
    data class Finished(val stopReason: String? = null) : StreamEvent()

    /** The stream terminated with an error. */
    data class Error(val throwable: Throwable) : StreamEvent()
}

/** Accumulates streaming state into a completed [StreamResult]. */
class StreamAccumulator {
    val textBuilder = StringBuilder()
    val toolCalls = mutableListOf<ToolCallInfo>()

    fun appendToken(text: String) {
        textBuilder.append(text)
    }

    fun appendToolCalls(calls: List<ToolCallInfo>) {
        toolCalls.addAll(calls)
    }

    fun build(): StreamResult = StreamResult(
        text = textBuilder.toString(),
        toolCalls = toolCalls.toList(),
    )
}

data class StreamResult(
    val text: String,
    val toolCalls: List<ToolCallInfo>,
)
