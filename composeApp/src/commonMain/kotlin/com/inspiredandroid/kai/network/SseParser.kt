package com.inspiredandroid.kai.network

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line

/**
 * Minimal SSE (Server-Sent Events) parser that reads lines from a
 * [ByteReadChannel] and invokes [onEvent] for each complete event.
 *
 * Handles the "data:" and "event:" fields. Multi-line data fields
 * are joined with newlines before dispatch.
 */
class SseParser(
    private val channel: ByteReadChannel,
    private val onEvent: suspend (event: String?, data: String) -> Unit,
) {
    private var currentEvent: String? = null
    private var dataBuffer = StringBuilder()

    /** Read the channel to exhaustion, dispatching events as they arrive. */
    suspend fun parse() {
        try {
            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                if (line.isEmpty()) {
                    // Empty line = event boundary
                    dispatch()
                } else if (line.startsWith(":")) {
                    // Comment — skip (used as keepalive by some providers)
                    continue
                } else if (line.startsWith("event:")) {
                    currentEvent = line.removePrefix("event:").trim()
                } else if (line.startsWith("data:")) {
                    val payload = line.removePrefix("data:").trim()
                    if (dataBuffer.isNotEmpty()) dataBuffer.append('\n')
                    dataBuffer.append(payload)
                }
                // Ignore other fields (id:, retry:, etc.)
            }
            // Flush any remaining event (connection closed without trailing blank line)
            dispatch()
        } catch (_: Exception) {
            // Channel closed or read interrupted — dispatch whatever we have
            dispatch()
        }
    }

    private suspend fun dispatch() {
        val data = dataBuffer.toString()
        dataBuffer = StringBuilder()
        if (data.isNotEmpty()) {
            onEvent(currentEvent, data)
        }
        currentEvent = null
    }
}
