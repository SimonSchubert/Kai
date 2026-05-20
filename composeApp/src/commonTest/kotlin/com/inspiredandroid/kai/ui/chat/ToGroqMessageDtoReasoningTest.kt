package com.inspiredandroid.kai.ui.chat

import com.inspiredandroid.kai.data.ReasoningRequestMode
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Guards the per-service gating of `reasoning_content` on outgoing assistant messages.
 *
 * Groq and Cerebras return HTTP 400 when this field is included; providers like DeepSeek
 * thinking-mode, Fireworks, LongCat, MiniMax, Moonshot, Venice, Z.AI and OpenCode Zen
 * require it for multi-turn tool calling with reasoning models.
 */
class ToGroqMessageDtoReasoningTest {

    private fun assistantWithToolCallAndReasoning() = History(
        role = History.Role.ASSISTANT,
        content = "",
        isThinking = true,
        toolCalls = persistentListOf(
            ToolCallInfo(id = "call_1", name = "search", arguments = """{"q":"hi"}"""),
        ),
        reasoningContent = "let me think about this",
    )

    @Test
    fun `NONE mode strips reasoning_content from assistant tool-call message`() {
        val dto = assistantWithToolCallAndReasoning().toGroqMessageDto(ReasoningRequestMode.NONE)

        assertEquals("assistant", dto.role)
        assertEquals(1, dto.tool_calls?.size)
        assertNull(dto.reasoningContent, "Groq/Cerebras reject this field — must not be emitted")
    }

    @Test
    fun `REASONING_CONTENT mode preserves reasoning_content on assistant tool-call message`() {
        val dto = assistantWithToolCallAndReasoning().toGroqMessageDto(ReasoningRequestMode.REASONING_CONTENT)

        assertEquals("assistant", dto.role)
        assertEquals(1, dto.tool_calls?.size)
        assertEquals("let me think about this", dto.reasoningContent)
    }

    @Test
    fun `default mode is NONE`() {
        val dto = assistantWithToolCallAndReasoning().toGroqMessageDto()
        assertNull(dto.reasoningContent)
    }

    @Test
    fun `non-tool-call assistant messages never carry reasoning_content regardless of mode`() {
        val plain = History(
            role = History.Role.ASSISTANT,
            content = "hello",
            reasoningContent = "thinking…",
        )

        assertNull(plain.toGroqMessageDto(ReasoningRequestMode.NONE).reasoningContent)
        assertNull(plain.toGroqMessageDto(ReasoningRequestMode.REASONING_CONTENT).reasoningContent)
    }
}
