package com.inspiredandroid.kai.network

import com.inspiredandroid.kai.ui.chat.ToolCallInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parses OpenAI-compatible SSE streaming chunks into [StreamEvent]s.
 *
 * Each chunk is a JSON line with:
 *   {"choices":[{"index":0,"delta":{"content":"..."},"finish_reason":null}]}
 *
 * Tool calls arrive as incremental deltas:
 *   delta.tool_calls[0] = {index:0, id:"...", function:{name:"...", arguments:"..."}}
 *
 * The stream terminates with a "data: [DONE]" sentinel.
 */
class OpenAICompatibleStreamParser(
    private val onEvent: suspend (StreamEvent) -> Unit,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // Track in-progress tool calls by index so we can accumulate delta fragments.
    private data class ToolCallBuf(
        var id: String? = null,
        var name: String? = null,
        val arguments: StringBuilder = StringBuilder(),
    )

    private val toolCallBufs = mutableMapOf<Int, ToolCallBuf>()

    /** Process one SSE data payload. */
    suspend fun onData(data: String) {
        if (data == "[DONE]") {
            flushToolCalls()
            onEvent(StreamEvent.Finished(null))
            return
        }

        val chunk = try {
            json.parseToJsonElement(data).jsonObject
        } catch (_: Exception) {
            return
        }

        val choices = chunk["choices"]?.jsonArray ?: return
        for (choice in choices) {
            val obj = choice.jsonObject
            val delta = obj["delta"]?.jsonObject ?: continue
            val finishReason = obj["finish_reason"]?.jsonPrimitive?.content

            // Text content
            val content = delta["content"]?.jsonPrimitive?.content
            if (!content.isNullOrEmpty()) {
                onEvent(StreamEvent.Token(content))
            }

            // Tool calls (incremental)
            val toolCalls = delta["tool_calls"]?.jsonArray
            if (toolCalls != null) {
                for (tc in toolCalls) {
                    val tcObj = tc.jsonObject
                    val index = tcObj["index"]?.jsonPrimitive?.int ?: continue
                    val buf = toolCallBufs.getOrPut(index) { ToolCallBuf() }

                    tcObj["id"]?.jsonPrimitive?.content?.let { buf.id = it }
                    val fn = tcObj["function"]?.jsonObject
                    fn?.get("name")?.jsonPrimitive?.content?.let { buf.name = it }
                    fn?.get("arguments")?.jsonPrimitive?.content?.let { buf.arguments.append(it) }
                }
            }

            // If finish_reason is set and we have tool calls, flush them
            if (!finishReason.isNullOrEmpty() && finishReason != "stop") {
                flushToolCalls()
            }
        }
    }

    private suspend fun flushToolCalls() {
        if (toolCallBufs.isEmpty()) return
        val calls = toolCallBufs.values.mapNotNull { buf ->
            val name = buf.name ?: return@mapNotNull null
            ToolCallInfo(
                id = buf.id ?: "stream-${name.hashCode()}",
                name = name,
                arguments = buf.arguments.toString(),
            )
        }
        toolCallBufs.clear()
        if (calls.isNotEmpty()) {
            onEvent(StreamEvent.ToolCalls(calls))
        }
    }

    /** Reset state for a new stream. */
    fun reset() {
        toolCallBufs.clear()
    }
}
