package com.inspiredandroid.kai.data.providers

import com.inspiredandroid.kai.network.dtos.openaicompatible.OpenAICompatibleChatRequestDto.FunctionCall
import com.inspiredandroid.kai.network.dtos.openaicompatible.OpenAICompatibleChatRequestDto.Message
import com.inspiredandroid.kai.network.dtos.openaicompatible.OpenAICompatibleChatRequestDto.ToolCall
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Guards the tool-call pairing invariant required by strict OpenAI-compatible providers
 * (DeepSeek via OpenCode Zen). A violation produces a 400:
 * "An assistant message with 'tool_calls' must be followed by tool messages responding to each
 * 'tool_call_id'."
 */
class SanitizeToolMessagesTest {

    private fun user(text: String) = Message(role = "user", content = JsonPrimitive(text))

    private fun assistantWithCalls(content: String?, vararg ids: String) = Message(
        role = "assistant",
        content = content?.let { JsonPrimitive(it) },
        tool_calls = ids.map { ToolCall(id = it, function = FunctionCall(name = "search", arguments = "{}")) },
    )

    private fun toolResult(callId: String?) = Message(
        role = "tool",
        content = JsonPrimitive("result"),
        tool_call_id = callId,
    )

    @Test
    fun `balanced sequence is left unchanged`() {
        val input = listOf(
            user("hi"),
            assistantWithCalls(null, "call_1", "call_2"),
            toolResult("call_1"),
            toolResult("call_2"),
            Message(role = "assistant", content = JsonPrimitive("done")),
        )

        assertEquals(input, sanitizeToolMessages(input))
    }

    @Test
    fun `assistant tool_calls with no responses keeps text and drops the calls`() {
        val input = listOf(
            user("hi"),
            assistantWithCalls("let me check", "call_1"),
        )

        val result = sanitizeToolMessages(input)

        assertEquals(2, result.size)
        assertEquals("assistant", result[1].role)
        assertNull(result[1].tool_calls, "Unanswered tool_calls must be stripped")
        assertEquals(JsonPrimitive("let me check"), result[1].content)
    }

    @Test
    fun `assistant tool_calls with neither responses nor text is dropped entirely`() {
        val input = listOf(
            user("hi"),
            assistantWithCalls(null, "call_1"),
        )

        val result = sanitizeToolMessages(input)

        assertEquals(listOf(user("hi")), result)
    }

    @Test
    fun `orphan tool message without preceding tool_calls is dropped`() {
        val input = listOf(
            user("hi"),
            toolResult("call_1"),
            Message(role = "assistant", content = JsonPrimitive("answer")),
        )

        val result = sanitizeToolMessages(input)

        assertTrue(result.none { it.role == "tool" })
        assertEquals(2, result.size)
    }

    @Test
    fun `partial responses keep only the answered calls`() {
        val input = listOf(
            user("hi"),
            assistantWithCalls(null, "call_1", "call_2"),
            toolResult("call_1"),
        )

        val result = sanitizeToolMessages(input)

        assertEquals(3, result.size)
        assertEquals(listOf("call_1"), result[1].tool_calls?.map { it.id })
        assertEquals("tool", result[2].role)
        assertEquals("call_1", result[2].tool_call_id)
    }

    @Test
    fun `tool response with null id is dropped as an orphan`() {
        val input = listOf(
            user("hi"),
            assistantWithCalls(null, "call_1"),
            toolResult(null),
        )

        val result = sanitizeToolMessages(input)

        // call_1 went unanswered (the response carried no id), so the calls are stripped and the
        // text-less assistant turn is removed, leaving only the user message.
        assertEquals(listOf(user("hi")), result)
    }

    @Test
    fun `leading orphan tools from trimming are dropped while later turns survive`() {
        // Simulates a context-trim that cut into the middle of an earlier tool turn.
        val input = listOf(
            toolResult("old_call"),
            user("next question"),
            assistantWithCalls(null, "call_1"),
            toolResult("call_1"),
            Message(role = "assistant", content = JsonPrimitive("done")),
        )

        val result = sanitizeToolMessages(input)

        assertEquals("user", result.first().role)
        assertEquals(4, result.size)
        assertEquals(listOf("call_1"), result[1].tool_calls?.map { it.id })
    }

    @Test
    fun `tool_call to a tool not in declaredToolNames is dropped along with its response`() {
        // Repros Bug B: the user disabled `search` between turns, so the assistant's prior
        // tool_call to it is orphan relative to the current request's tools array.
        val input = listOf(
            user("hi"),
            assistantWithCalls("checking", "call_1"),
            toolResult("call_1"),
            user("anything else?"),
        )

        val result = sanitizeToolMessages(input, declaredToolNames = setOf("calculator"))

        // The assistant turn keeps its text but loses tool_calls; the tool response is dropped.
        assertEquals(3, result.size)
        assertEquals("assistant", result[1].role)
        assertNull(result[1].tool_calls)
        assertEquals(JsonPrimitive("checking"), result[1].content)
        assertTrue(result.none { it.role == "tool" })
    }

    @Test
    fun `tool_call whose name is in declaredToolNames is preserved`() {
        val input = listOf(
            user("hi"),
            assistantWithCalls(null, "call_1"),
            toolResult("call_1"),
        )

        val result = sanitizeToolMessages(input, declaredToolNames = setOf("search"))

        assertEquals(3, result.size)
        assertEquals(listOf("call_1"), result[1].tool_calls?.map { it.id })
        assertEquals("call_1", result[2].tool_call_id)
    }

    @Test
    fun `empty declaredToolNames strips every historic tool_call`() {
        val input = listOf(
            user("hi"),
            assistantWithCalls("text", "call_1"),
            toolResult("call_1"),
        )

        val result = sanitizeToolMessages(input, declaredToolNames = emptySet())

        // Tool_calls and the paired response are gone; assistant text survives.
        assertEquals(2, result.size)
        assertNull(result[1].tool_calls)
        assertEquals(JsonPrimitive("text"), result[1].content)
    }

    @Test
    fun `null declaredToolNames preserves historic tool_calls regardless of name`() {
        // Legacy behavior: callers that don't supply a set get pairing checks only.
        val input = listOf(
            user("hi"),
            assistantWithCalls(null, "call_1"),
            toolResult("call_1"),
        )

        val result = sanitizeToolMessages(input, declaredToolNames = null)

        assertEquals(3, result.size)
        assertEquals(listOf("call_1"), result[1].tool_calls?.map { it.id })
    }
}
