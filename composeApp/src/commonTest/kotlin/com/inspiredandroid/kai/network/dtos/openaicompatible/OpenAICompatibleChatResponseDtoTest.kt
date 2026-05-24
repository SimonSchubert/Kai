package com.inspiredandroid.kai.network.dtos.openaicompatible

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OpenAICompatibleChatResponseDtoTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    private fun decode(responseJson: String) = json.decodeFromString(OpenAICompatibleChatResponseDto.serializer(), responseJson)

    @Test
    fun `content as plain string`() {
        val responseJson = """
            {"choices": [{"message": {"role": "assistant", "content": "Here is the answer."}}]}
        """.trimIndent()
        val message = decode(responseJson).choices[0].message
        assertEquals("Here is the answer.", message?.content)
        assertEquals("Here is the answer.", message?.effectiveContent)
    }

    @Test
    fun `content as array of text blocks is flattened`() {
        val responseJson = """
            {
                "choices": [{
                    "message": {
                        "role": "assistant",
                        "content": [
                            {"type": "text", "text": "Here are "},
                            {"type": "text", "text": "the results."}
                        ]
                    }
                }]
            }
        """.trimIndent()
        val message = decode(responseJson).choices[0].message
        assertEquals("Here are the results.", message?.content)
        assertEquals("Here are the results.", message?.effectiveContent)
    }

    @Test
    fun `content as empty array yields empty string`() {
        val responseJson = """
            {"choices": [{"message": {"role": "assistant", "content": []}}]}
        """.trimIndent()
        val message = decode(responseJson).choices[0].message
        assertEquals("", message?.content)
        // effectiveContent treats blank content as absent and falls back to reasoning (null here).
        assertNull(message?.effectiveContent)
    }

    @Test
    fun `content null when absent`() {
        val responseJson = """
            {"choices": [{"message": {"role": "assistant", "reasoning_content": "thinking"}}]}
        """.trimIndent()
        val message = decode(responseJson).choices[0].message
        assertNull(message?.content)
        assertEquals("thinking", message?.effectiveContent)
    }

    @Test
    fun `content array ignores blocks without text field`() {
        val responseJson = """
            {
                "choices": [{
                    "message": {
                        "content": [
                            {"type": "text", "text": "answer"},
                            {"type": "image_url", "image_url": {"url": "http://example.com/x.png"}}
                        ]
                    }
                }]
            }
        """.trimIndent()
        val message = decode(responseJson).choices[0].message
        assertEquals("answer", message?.content)
    }
}
