package com.inspiredandroid.kai.data.providers

import com.inspiredandroid.kai.data.Service
import com.inspiredandroid.kai.ui.chat.History
import com.inspiredandroid.kai.ui.chat.ToolCallInfo
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end guard for the OpenCode/DeepSeek tool-call crash:
 * "An assistant message with 'tool_calls' must be followed by tool messages responding to each
 * 'tool_call_id'."
 *
 * Reproduces the reported scenario where an earlier tool turn was interrupted (a cancel, an app
 * kill, or a provider failing over to a fallback service mid-loop) and left an assistant tool-call
 * row in history with no tool responses behind it. The next request must not resend that orphan.
 */
class BuildOpenAIMessagesPairingTest {

    private fun assertNoDanglingToolCalls(messages: List<com.inspiredandroid.kai.network.dtos.openaicompatible.OpenAICompatibleChatRequestDto.Message>) {
        for ((i, msg) in messages.withIndex()) {
            if (msg.role == "assistant" && !msg.tool_calls.isNullOrEmpty()) {
                val responded = messages.drop(i + 1)
                    .takeWhile { it.role == "tool" }
                    .mapNotNull { it.tool_call_id }
                    .toSet()
                val unanswered = msg.tool_calls.map { it.id }.filter { it !in responded }
                assertTrue(unanswered.isEmpty(), "tool_call_ids without a response: $unanswered")
            }
            if (msg.role == "tool") {
                val prev = messages.getOrNull(i - 1)
                val precededByCalls = (prev?.role == "assistant" && !prev.tool_calls.isNullOrEmpty()) ||
                    prev?.role == "tool"
                assertTrue(precededByCalls, "orphan tool message at index $i")
            }
        }
    }

    @Test
    fun `orphaned assistant tool-call turn from a prior interrupted run is repaired`() {
        // History as it would be left after OpenCode produced tool calls but the loop never
        // appended the results (e.g. it failed over to OpenRouter mid-turn), then the user sends
        // another message.
        val history = listOf(
            History(role = History.Role.USER, content = "make me an image"),
            History(
                role = History.Role.ASSISTANT,
                content = "Sure, generating that now.",
                toolCalls = persistentListOf(
                    ToolCallInfo(id = "call_img", name = "generate_image", arguments = """{"prompt":"cat"}"""),
                ),
                reasoningContent = "the user wants an image",
            ),
            // <-- tool result row is missing here
            History(role = History.Role.USER, content = "try to make tool_calls"),
        )

        val messages = buildOpenAIMessages(Service.OpenCode, history, systemPrompt = "be helpful")

        assertNoDanglingToolCalls(messages)
        // The dangling tool_calls are stripped; the assistant text survives.
        val assistant = messages.single { it.role == "assistant" }
        assertEquals(null, assistant.tool_calls)
        assertTrue(messages.last().role == "user")
    }

    @Test
    fun `a complete tool turn is preserved intact`() {
        val history = listOf(
            History(role = History.Role.USER, content = "make me an image"),
            History(
                role = History.Role.ASSISTANT,
                content = "",
                toolCalls = persistentListOf(
                    ToolCallInfo(id = "call_img", name = "generate_image", arguments = """{"prompt":"cat"}"""),
                ),
                reasoningContent = "the user wants an image",
            ),
            History(role = History.Role.TOOL, content = "saved to /img.png", toolCallId = "call_img", toolName = "generate_image"),
            History(role = History.Role.ASSISTANT, content = "Done, pushed to your device."),
        )

        val messages = buildOpenAIMessages(Service.OpenCode, history, systemPrompt = null)

        assertNoDanglingToolCalls(messages)
        val toolCallTurn = messages.single { it.role == "assistant" && !it.tool_calls.isNullOrEmpty() }
        assertEquals(listOf("call_img"), toolCallTurn.tool_calls?.map { it.id })
        assertEquals(1, messages.count { it.role == "tool" })
    }
}
