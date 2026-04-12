package com.inspiredandroid.kai.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ModelCatalogTest {

    @Test
    fun `exact id match returns entry`() {
        val info = ModelCatalog.lookup("gpt-4o")
        assertNotNull(info)
        assertEquals("GPT-4o", info.displayName)
        assertEquals(128_000, info.contextWindow)
        assertEquals("2024-08", info.releaseDate)
    }

    @Test
    fun `provider prefix is stripped before match`() {
        val strippedAnthropic = ModelCatalog.lookup("anthropic/claude-3.5-sonnet")
        assertNotNull(strippedAnthropic)
        assertEquals("Claude 3.5 Sonnet", strippedAnthropic.displayName)

        val strippedXai = ModelCatalog.lookup("x-ai/grok-4")
        assertNotNull(strippedXai)
        assertEquals("Grok 4", strippedXai.displayName)

        val strippedGoogle = ModelCatalog.lookup("google/gemini-2.5-pro")
        assertNotNull(strippedGoogle)
        assertEquals("Gemini 2.5 Pro", strippedGoogle.displayName)
    }

    @Test
    fun `lookup is case insensitive`() {
        val mixed = ModelCatalog.lookup("Claude-3.5-Sonnet")
        assertNotNull(mixed)
        assertEquals("Claude 3.5 Sonnet", mixed.displayName)
    }

    @Test
    fun `separator variants must each be present as exact keys`() {
        val dashed = ModelCatalog.lookup("claude-3-5-sonnet")
        val dotted = ModelCatalog.lookup("claude-3.5-sonnet")
        assertNotNull(dashed)
        assertNotNull(dotted)
        assertEquals(dashed, dotted)
    }

    @Test
    fun `anthropic date-stamped id has its own entry`() {
        val dated = ModelCatalog.lookup("claude-sonnet-4-5-20250929")
        val bare = ModelCatalog.lookup("claude-sonnet-4-5")
        assertNotNull(dated)
        assertNotNull(bare)
        assertEquals(dated, bare)
    }

    @Test
    fun `openai date-stamped id has its own entry`() {
        val dated = ModelCatalog.lookup("gpt-4o-2024-08-06")
        assertNotNull(dated)
        assertEquals("GPT-4o", dated.displayName)
    }

    @Test
    fun `unknown variant does not walk to family base`() {
        // Previously the walker would have matched this to `kimi-k2`.
        // With exact-match only, unknown variants return null.
        assertNull(ModelCatalog.lookup("kimi-k2-imaginary-variant"))
    }

    @Test
    fun `open weight models include parameter count`() {
        val llama = ModelCatalog.lookup("llama-3.3-70b-instruct")
        assertNotNull(llama)
        assertEquals("70B", llama.parameterCount)

        val deepseekR1 = ModelCatalog.lookup("deepseek-r1")
        assertNotNull(deepseekR1)
        assertEquals("671B", deepseekR1.parameterCount)

        val mixtral = ModelCatalog.lookup("mixtral-8x22b")
        assertNotNull(mixtral)
        assertEquals("8x22B", mixtral.parameterCount)
    }

    @Test
    fun `closed models have null parameter count`() {
        val gpt = ModelCatalog.lookup("gpt-4o")
        assertNotNull(gpt)
        assertNull(gpt.parameterCount)

        val claude = ModelCatalog.lookup("claude-sonnet-4-5")
        assertNotNull(claude)
        assertNull(claude.parameterCount)
    }

    @Test
    fun `ollama-style colon ids have explicit entries`() {
        val gemma = ModelCatalog.lookup("gemma3:27b")
        assertNotNull(gemma)
        assertEquals("Gemma 3 27B", gemma.displayName)
        assertEquals("27B", gemma.parameterCount)
    }

    @Test
    fun `preview models carry release dates`() {
        val preview = ModelCatalog.lookup("gemini-2.5-flash-preview-05-20")
        assertNotNull(preview)
        assertEquals("Gemini 2.5 Flash (Preview 05-20)", preview.displayName)
        assertEquals("2025-05", preview.releaseDate)
    }

    @Test
    fun `unknown model returns null`() {
        assertNull(ModelCatalog.lookup("totally-made-up-model"))
    }
}
