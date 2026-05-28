package com.inspiredandroid.kai.data.providers

import com.inspiredandroid.kai.data.Service
import com.inspiredandroid.kai.network.dtos.openaicompatible.OpenAICompatibleChatRequestDto
import com.inspiredandroid.kai.ui.chat.History
import com.inspiredandroid.kai.ui.chat.toGroqMessageDto
import kotlinx.serialization.json.JsonPrimitive

internal fun buildOpenAIMessages(
    service: Service,
    messages: List<History>,
    systemPrompt: String?,
    declaredToolNames: Set<String>? = null,
): List<OpenAICompatibleChatRequestDto.Message> = buildList {
    if (!systemPrompt.isNullOrEmpty()) {
        add(
            OpenAICompatibleChatRequestDto.Message(
                role = "system",
                content = JsonPrimitive(systemPrompt),
            ),
        )
    }
    addAll(
        sanitizeToolMessages(
            messages.map { it.toGroqMessageDto(service.reasoningRequestMode, service.supportsImages) },
            declaredToolNames,
        ),
    )
}

/**
 * Enforces the tool-call pairing invariant that strict OpenAI-compatible providers (DeepSeek via
 * OpenCode Zen, etc.) require:
 *
 * 1. An `assistant` message carrying `tool_calls` must be immediately followed by one `tool`
 *    message answering each `tool_call_id`.
 * 2. A `tool` message must answer a `tool_call_id` from the assistant turn directly before it.
 * 3. Every `tool_calls` entry must reference a tool that is also in the current request's
 *    `tools[]` array — Groq rejects calls to tools that aren't declared on the request.
 *
 * A history can violate this after context trimming drops part of a turn, after a tool run is
 * interrupted (assistant requested calls that were never executed), when a provider returns a
 * malformed/empty id, or when the user toggles a tool off between turns that referenced it.
 * Sending such a sequence triggers a 400:
 * "An assistant message with 'tool_calls' must be followed by tool messages responding to each
 * 'tool_call_id'."
 *
 * This pass walks the messages, drops `tool_calls` whose function name is not in
 * [declaredToolNames], pairs each remaining assistant `tool_calls` turn with the tool responses
 * that follow it, drops any `tool_call` that has no matching response (and any orphan `tool`
 * message), and removes assistant turns left with neither content nor tool calls.
 *
 * Pass [declaredToolNames] = `null` to keep historic call-name fidelity (still enforces pairing).
 * Pass a (possibly empty) set to strip undeclared calls too — an empty set means "this request
 * declares no tools, so every historical tool_call is orphan."
 */
internal fun sanitizeToolMessages(
    messages: List<OpenAICompatibleChatRequestDto.Message>,
    declaredToolNames: Set<String>? = null,
): List<OpenAICompatibleChatRequestDto.Message> {
    val result = ArrayList<OpenAICompatibleChatRequestDto.Message>(messages.size)
    var i = 0
    while (i < messages.size) {
        val msg = messages[i]
        when {
            msg.role == "assistant" && !msg.tool_calls.isNullOrEmpty() -> {
                // Collect the contiguous run of tool responses that belong to this turn.
                var j = i + 1
                val following = ArrayList<OpenAICompatibleChatRequestDto.Message>()
                while (j < messages.size && messages[j].role == "tool") {
                    following.add(messages[j])
                    j++
                }
                // Drop tool_calls referencing tools not declared on this request. A null
                // [declaredToolNames] disables this filter so callers that don't care about
                // declared-tool cross-checking (legacy tests) keep prior behavior.
                val callsInDeclaredSet = if (declaredToolNames == null) {
                    msg.tool_calls
                } else {
                    msg.tool_calls.filter { it.function.name in declaredToolNames }
                }
                val keptCallIds = callsInDeclaredSet.map { it.id }.toSet()
                val matched = following.filter { it.tool_call_id != null && it.tool_call_id in keptCallIds }
                val respondedIds = matched.mapNotNull { it.tool_call_id }.toSet()
                val keptCalls = callsInDeclaredSet.filter { it.id in respondedIds }
                if (keptCalls.isEmpty()) {
                    // Nothing answered the calls: strip them, keeping the turn only if it still
                    // carries text. An assistant message with neither content nor tool_calls is
                    // itself rejected by some providers.
                    if (msg.content != null) result.add(msg.copy(tool_calls = null))
                } else {
                    result.add(msg.copy(tool_calls = keptCalls))
                    result.addAll(matched)
                }
                i = j
            }

            // A tool message reaching this point was not consumed by an assistant block above,
            // so it has no preceding tool_calls to answer — drop the orphan.
            msg.role == "tool" -> i++

            // Drop empty assistant turns that strict providers reject.
            msg.role == "assistant" && msg.content == null && msg.tool_calls.isNullOrEmpty() -> i++

            else -> {
                result.add(msg)
                i++
            }
        }
    }
    return result
}
