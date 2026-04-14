package com.inspiredandroid.kai.network.dtos.gemini

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class GeminiChatResponseDtoTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    @Test
    fun `extractText filters out parts flagged as thought`() {
        val responseJson = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "The user said \"test\". I should avoid generic chatbot responses.",
                        "thought": true
                      },
                      {
                        "text": "Loud and clear. What's on your mind?"
                      }
                    ],
                    "role": "model"
                  },
                  "finishReason": "STOP",
                  "index": 0
                }
              ]
            }
        """.trimIndent()
        val response = json.decodeFromString(GeminiChatResponseDto.serializer(), responseJson)
        assertEquals("Loud and clear. What's on your mind?", response.extractText())
    }

    @Test
    fun `extractText returns all parts when none are flagged as thought`() {
        val responseJson = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {"text": "First line."},
                      {"text": "Second line."}
                    ],
                    "role": "model"
                  }
                }
              ]
            }
        """.trimIndent()
        val response = json.decodeFromString(GeminiChatResponseDto.serializer(), responseJson)
        assertEquals("First line.\nSecond line.", response.extractText())
    }

    @Test
    fun `extractText returns empty string when only thought parts present`() {
        val responseJson = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {"text": "Internal plan only.", "thought": true}
                    ],
                    "role": "model"
                  }
                }
              ]
            }
        """.trimIndent()
        val response = json.decodeFromString(GeminiChatResponseDto.serializer(), responseJson)
        assertEquals("", response.extractText())
    }

    @Test
    fun `thought defaults to null when absent`() {
        val responseJson = """{"candidates":[{"content":{"parts":[{"text":"Hi"}]}}]}"""
        val response = json.decodeFromString(GeminiChatResponseDto.serializer(), responseJson)
        val part = response.candidates[0].content!!.parts!![0]
        assertEquals(null, part.thought)
        assertEquals(false, part.isThought)
    }
}
