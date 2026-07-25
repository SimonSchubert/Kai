package com.inspiredandroid.kai.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FreeTierModelsTest {

    @Test
    fun `openrouter free ids are marked free`() {
        assertTrue(FreeTierModels.isFreeTier(Service.OpenRouter, "openai/gpt-oss-20b:free"))
        assertTrue(FreeTierModels.isFreeTier(Service.OpenRouter, "google/gemma-4-31b-it:free"))
        assertTrue(FreeTierModels.isFreeTier(Service.OpenRouter, "openrouter/free"))
    }

    @Test
    fun `openrouter free match is case insensitive`() {
        assertTrue(FreeTierModels.isFreeTier(Service.OpenRouter, "OpenAI/GPT-OSS-20B:Free"))
    }

    @Test
    fun `openrouter paid ids are not free`() {
        assertFalse(FreeTierModels.isFreeTier(Service.OpenRouter, "anthropic/claude-opus-4"))
        assertFalse(FreeTierModels.isFreeTier(Service.OpenRouter, "openai/gpt-4o"))
    }

    @Test
    fun `openrouter free id is not free on other services`() {
        assertFalse(FreeTierModels.isFreeTier(Service.OpenAI, "openai/gpt-oss-20b:free"))
        assertFalse(FreeTierModels.isFreeTier(Service.OllamaCloud, "openai/gpt-oss-20b:free"))
    }

    @Test
    fun `ollama cloud free ids are marked free`() {
        assertTrue(FreeTierModels.isFreeTier(Service.OllamaCloud, "gpt-oss:20b"))
        assertTrue(FreeTierModels.isFreeTier(Service.OllamaCloud, "gemma4:31b"))
        assertTrue(FreeTierModels.isFreeTier(Service.OllamaCloud, "nemotron-3-nano:30b"))
    }

    @Test
    fun `ollama cloud aliases with cloud suffix match free list`() {
        assertTrue(FreeTierModels.isFreeTier(Service.OllamaCloud, "gpt-oss:20b-cloud"))
        assertTrue(FreeTierModels.isFreeTier(Service.OllamaCloud, "gemma4:cloud"))
        assertTrue(FreeTierModels.isFreeTier(Service.OllamaCloud, "gemma4:31b-cloud"))
    }

    @Test
    fun `ollama heavy models are not free`() {
        assertFalse(FreeTierModels.isFreeTier(Service.OllamaCloud, "deepseek-v4-pro"))
        assertFalse(FreeTierModels.isFreeTier(Service.OllamaCloud, "kimi-k2.6"))
        assertFalse(FreeTierModels.isFreeTier(Service.OllamaCloud, "minimax-m3"))
    }

    @Test
    fun `services without a free catalog never mark free`() {
        assertFalse(FreeTierModels.isFreeTier(Service.Anthropic, "claude-sonnet-4-5"))
        assertFalse(FreeTierModels.isFreeTier(Service.Groq, "llama-3.3-70b-versatile"))
        assertFalse(FreeTierModels.isFreeTier(Service.Gemini, "gemini-2.5-flash"))
    }

    @Test
    fun `normalizeOllamaId strips cloud suffixes`() {
        assertEquals("gpt-oss:20b", FreeTierModels.normalizeOllamaId("gpt-oss:20b-cloud"))
        assertEquals("gemma4", FreeTierModels.normalizeOllamaId("gemma4:cloud"))
        assertEquals("gemma4:31b", FreeTierModels.normalizeOllamaId("gemma4:31b-cloud"))
        assertEquals("gpt-oss:20b", FreeTierModels.normalizeOllamaId("gpt-oss:20b"))
    }
}
