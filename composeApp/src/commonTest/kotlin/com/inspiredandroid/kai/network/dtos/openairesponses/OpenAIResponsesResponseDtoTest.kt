package com.inspiredandroid.kai.network.dtos.openairesponses

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Parsing guard for the `/v1/responses` output array (issue #469). */
class OpenAIResponsesResponseDtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun parse(body: String) = json.decodeFromString(OpenAIResponsesResponseDto.serializer(), body)

    @Test
    fun `reads answer text out of the message item`() {
        val dto = parse(
            """
            {
              "id": "resp_1",
              "status": "completed",
              "output": [
                {"id": "rs_1", "type": "reasoning", "summary": []},
                {"id": "msg_1", "type": "message", "role": "assistant",
                 "content": [{"type": "output_text", "text": "42", "annotations": []}]}
              ]
            }
            """.trimIndent(),
        )

        assertEquals("42", dto.outputText)
        assertNull(dto.reasoningSummary)
        assertTrue(dto.functionCalls.isEmpty())
    }

    @Test
    fun `reads tool calls and reasoning summaries from a tool turn`() {
        val dto = parse(
            """
            {
              "output": [
                {"id": "rs_1", "type": "reasoning",
                 "summary": [{"type": "summary_text", "text": "Need the clock."}],
                 "encrypted_content": "gAAAA"},
                {"id": "fc_1", "type": "function_call", "call_id": "call_abc",
                 "name": "get_local_time", "arguments": "{}", "status": "completed"}
              ]
            }
            """.trimIndent(),
        )

        assertNull(dto.outputText)
        assertEquals("Need the clock.", dto.reasoningSummary)
        assertEquals(1, dto.functionCalls.size)
        assertEquals("call_abc", dto.functionCalls[0].callId)
        assertEquals("get_local_time", dto.functionCalls[0].name)
        assertEquals("{}", dto.functionCalls[0].arguments)
    }

    @Test
    fun `concatenates parallel tool calls and multi-part text`() {
        val dto = parse(
            """
            {
              "output": [
                {"type": "message", "role": "assistant",
                 "content": [{"type": "output_text", "text": "a"}, {"type": "output_text", "text": "b"}]},
                {"type": "function_call", "call_id": "call_1", "name": "x", "arguments": "{}"},
                {"type": "function_call", "call_id": "call_2", "name": "y", "arguments": "{}"}
              ]
            }
            """.trimIndent(),
        )

        assertEquals("ab", dto.outputText)
        assertEquals(listOf("call_1", "call_2"), dto.functionCalls.map { it.callId })
    }

    @Test
    fun `surfaces a failed response instead of parsing it as empty`() {
        val dto = parse(
            """
            {"status": "failed", "output": [], "error": {"code": "server_error", "message": "upstream failure"}}
            """.trimIndent(),
        )

        assertNull(dto.outputText)
        assertEquals("upstream failure", dto.error?.message)
    }
}
