package com.inspiredandroid.kai.data

import com.inspiredandroid.kai.network.dtos.anthropic.AnthropicModelsResponseDto
import com.inspiredandroid.kai.network.dtos.gemini.GeminiModelsResponseDto
import com.inspiredandroid.kai.network.dtos.openaicompatible.OpenAICompatibleModelResponseDto
import com.inspiredandroid.kai.ui.settings.SettingsModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModelTransformationsTest {

    // --- Anthropic ---

    @Test
    fun `mapAnthropicModels maps API display_name to displayName field`() {
        val models = listOf(
            AnthropicModelsResponseDto.ModelInfo(id = "made-up-model", display_name = "Made Up Model"),
        )
        val result = mapAnthropicModels(models, selectedModelId = "")
        assertEquals("Made Up Model", result[0].displayName)
        assertEquals("", result[0].subtitle)
    }

    @Test
    fun `mapAnthropicModels falls back to catalog displayName when API omits it`() {
        val models = listOf(
            AnthropicModelsResponseDto.ModelInfo(id = "claude-sonnet-4-5-20250929"),
        )
        val result = mapAnthropicModels(models, selectedModelId = "")
        assertEquals("Claude Sonnet 4.5", result[0].displayName)
    }

    @Test
    fun `mapAnthropicModels leaves displayName null when display_name and catalog both missing`() {
        val models = listOf(
            AnthropicModelsResponseDto.ModelInfo(id = "made-up-no-catalog-entry"),
        )
        val result = mapAnthropicModels(models, selectedModelId = "")
        assertNull(result[0].displayName)
    }

    @Test
    fun `mapAnthropicModels catalog releaseDate wins over API created_at`() {
        val models = listOf(
            AnthropicModelsResponseDto.ModelInfo(
                id = "claude-sonnet-4-5-20250929",
                created_at = "2099-01-01T00:00:00Z",
            ),
        )
        val result = mapAnthropicModels(models, selectedModelId = "")
        assertEquals("2025-09", result[0].releaseDate)
    }

    @Test
    fun `mapAnthropicModels uses API created_at only when catalog has no date`() {
        val models = listOf(
            AnthropicModelsResponseDto.ModelInfo(
                id = "claude-made-up-no-catalog",
                created_at = "2025-03-14T10:00:00Z",
            ),
        )
        val result = mapAnthropicModels(models, selectedModelId = "")
        assertNull(result[0].releaseDate) // uncatalogued model, no API fallback for Anthropic
    }

    @Test
    fun `mapAnthropicModels fills contextWindow from catalog for known models`() {
        val models = listOf(
            AnthropicModelsResponseDto.ModelInfo(id = "claude-sonnet-4-5-20250929"),
        )
        val result = mapAnthropicModels(models, selectedModelId = "")
        assertEquals(1_000_000, result[0].contextWindow)
    }

    @Test
    fun `mapAnthropicModels marks selected model`() {
        val models = listOf(
            AnthropicModelsResponseDto.ModelInfo(id = "claude-sonnet-4-20250514"),
            AnthropicModelsResponseDto.ModelInfo(id = "claude-3-opus-20240229"),
        )
        val result = mapAnthropicModels(models, selectedModelId = "claude-3-opus-20240229")
        assertFalse(result[0].isSelected)
        assertTrue(result[1].isSelected)
    }

    @Test
    fun `mapAnthropicModels empty list`() {
        val result = mapAnthropicModels(emptyList(), selectedModelId = "anything")
        assertTrue(result.isEmpty())
    }

    // --- Gemini ---

    @Test
    fun `mapGeminiModels filters out non-generateContent models`() {
        val models = listOf(
            GeminiModelsResponseDto.Model(
                name = "models/gemini-2.0-flash",
                supportedGenerationMethods = listOf("generateContent", "countTokens"),
            ),
            GeminiModelsResponseDto.Model(
                name = "models/text-embedding-004",
                supportedGenerationMethods = listOf("embedContent"),
            ),
            GeminiModelsResponseDto.Model(
                name = "models/gemini-no-methods",
                supportedGenerationMethods = null,
            ),
        )
        val result = mapGeminiModels(models, selectedModelId = "")
        assertEquals(1, result.size)
        assertEquals("gemini-2.0-flash", result[0].id)
    }

    @Test
    fun `mapGeminiModels removes models prefix`() {
        val models = listOf(
            GeminiModelsResponseDto.Model(
                name = "models/gemini-2.5-pro",
                supportedGenerationMethods = listOf("generateContent"),
            ),
        )
        val result = mapGeminiModels(models, selectedModelId = "")
        assertEquals("gemini-2.5-pro", result[0].id)
    }

    @Test
    fun `mapGeminiModels catalog displayName wins over API displayName`() {
        val models = listOf(
            GeminiModelsResponseDto.Model(
                name = "models/gemini-2.0-flash",
                displayName = "Custom Gemini Name",
                supportedGenerationMethods = listOf("generateContent"),
            ),
        )
        val result = mapGeminiModels(models, selectedModelId = "")
        // Catalog has "Gemini 2.0 Flash" — takes priority over API name
        assertEquals("Gemini 2.0 Flash", result[0].displayName)
    }

    @Test
    fun `mapGeminiModels falls back to catalog displayName when API omits it`() {
        val models = listOf(
            GeminiModelsResponseDto.Model(
                name = "models/gemini-1.5-pro",
                displayName = null,
                supportedGenerationMethods = listOf("generateContent"),
            ),
        )
        val result = mapGeminiModels(models, selectedModelId = "")
        assertEquals("Gemini 1.5 Pro", result[0].displayName)
    }

    @Test
    fun `mapGeminiModels uses inputTokenLimit as context window`() {
        val models = listOf(
            GeminiModelsResponseDto.Model(
                name = "models/gemini-unknown-model",
                inputTokenLimit = 524_288,
                supportedGenerationMethods = listOf("generateContent"),
            ),
        )
        val result = mapGeminiModels(models, selectedModelId = "")
        assertEquals(524_288L, result[0].contextWindow)
    }

    @Test
    fun `mapGeminiModels catalog contextWindow wins over API inputTokenLimit`() {
        val models = listOf(
            GeminiModelsResponseDto.Model(
                name = "models/gemini-2.5-pro",
                inputTokenLimit = 999_999,
                supportedGenerationMethods = listOf("generateContent"),
            ),
        )
        val result = mapGeminiModels(models, selectedModelId = "")
        // Catalog has 2_000_000 for gemini-2.5-pro — takes priority
        assertEquals(2_000_000L, result[0].contextWindow)
    }

    @Test
    fun `mapGeminiModels catalog fills releaseDate since API does not expose it`() {
        val models = listOf(
            GeminiModelsResponseDto.Model(
                name = "models/gemini-2.5-pro",
                supportedGenerationMethods = listOf("generateContent"),
            ),
        )
        val result = mapGeminiModels(models, selectedModelId = "")
        assertEquals("2025-03", result[0].releaseDate)
    }

    @Test
    fun `mapGeminiModels excludes nano banana and other non-chat variants`() {
        val models = listOf(
            GeminiModelsResponseDto.Model(
                name = "models/gemini-2.5-flash",
                supportedGenerationMethods = listOf("generateContent"),
            ),
            GeminiModelsResponseDto.Model(
                name = "models/gemini-2.5-flash-image",
                supportedGenerationMethods = listOf("generateContent"),
            ),
            GeminiModelsResponseDto.Model(
                name = "models/gemini-2.5-flash-image-preview",
                supportedGenerationMethods = listOf("generateContent"),
            ),
            GeminiModelsResponseDto.Model(
                name = "models/imagen-3",
                supportedGenerationMethods = listOf("generateContent"),
            ),
            GeminiModelsResponseDto.Model(
                name = "models/gemini-2.5-flash-tts",
                supportedGenerationMethods = listOf("generateContent"),
            ),
            GeminiModelsResponseDto.Model(
                name = "models/gemini-2.5-flash-native-audio-dialog",
                supportedGenerationMethods = listOf("generateContent"),
            ),
            GeminiModelsResponseDto.Model(
                name = "models/gemini-live-2.5-flash",
                supportedGenerationMethods = listOf("generateContent"),
            ),
            GeminiModelsResponseDto.Model(
                name = "models/veo-3",
                supportedGenerationMethods = listOf("generateContent"),
            ),
            GeminiModelsResponseDto.Model(
                name = "models/gemini-embedding-001",
                supportedGenerationMethods = listOf("generateContent"),
            ),
        )
        val result = mapGeminiModels(models, selectedModelId = "")
        val ids = result.map { it.id }
        assertEquals(listOf("gemini-2.5-flash"), ids)
    }

    @Test
    fun `mapGeminiModels marks selected model`() {
        val models = listOf(
            GeminiModelsResponseDto.Model(
                name = "models/gemini-2.0-flash",
                supportedGenerationMethods = listOf("generateContent"),
            ),
            GeminiModelsResponseDto.Model(
                name = "models/gemini-1.5-pro",
                supportedGenerationMethods = listOf("generateContent"),
            ),
        )
        val result = mapGeminiModels(models, selectedModelId = "gemini-1.5-pro")
        val flash = result.first { it.id == "gemini-2.0-flash" }
        val pro = result.first { it.id == "gemini-1.5-pro" }
        assertFalse(flash.isSelected)
        assertTrue(pro.isSelected)
    }

    // --- Unified newest-first sort ---

    @Test
    fun `newestFirstComparator sorts by release date then context window`() {
        val models = listOf(
            SettingsModel(id = "older-small", subtitle = "", releaseDate = "2024-01", contextWindow = 8_000),
            SettingsModel(id = "newest-small", subtitle = "", releaseDate = "2025-11", contextWindow = 8_000),
            SettingsModel(id = "newest-large", subtitle = "", releaseDate = "2025-11", contextWindow = 200_000),
            SettingsModel(id = "no-date", subtitle = ""),
        )
        val sorted = models.sortedWith(newestFirstComparator)
        // same date -> larger ctx wins
        assertEquals("newest-large", sorted[0].id)
        assertEquals("newest-small", sorted[1].id)
        assertEquals("older-small", sorted[2].id)
        // null date goes last
        assertEquals("no-date", sorted[3].id)
    }

    @Test
    fun `mapGeminiModels sorts newest first across versions`() {
        val models = listOf(
            GeminiModelsResponseDto.Model(
                name = "models/gemini-1.5-pro",
                supportedGenerationMethods = listOf("generateContent"),
            ),
            GeminiModelsResponseDto.Model(
                name = "models/gemini-2.5-pro",
                supportedGenerationMethods = listOf("generateContent"),
            ),
            GeminiModelsResponseDto.Model(
                name = "models/gemini-2.0-flash",
                supportedGenerationMethods = listOf("generateContent"),
            ),
        )
        val result = mapGeminiModels(models, selectedModelId = "")
        assertEquals("gemini-2.5-pro", result[0].id) // 2025-03
        assertEquals("gemini-2.0-flash", result[1].id) // 2025-02
        assertEquals("gemini-1.5-pro", result[2].id) // 2024-05
    }

    // --- OpenAI-Compatible ---

    @Test
    fun `mapOpenAICompatibleModels filterActiveStrictly keeps only isActive true`() {
        val models = listOf(
            OpenAICompatibleModelResponseDto.Model(id = "m1", isActive = true),
            OpenAICompatibleModelResponseDto.Model(id = "m2", isActive = null),
            OpenAICompatibleModelResponseDto.Model(id = "m3", isActive = false),
        )
        val result = mapOpenAICompatibleModels(models, Service.Groq, selectedModelId = "")
        assertEquals(1, result.size)
        assertEquals("m1", result[0].id)
    }

    @Test
    fun `mapOpenAICompatibleModels non-strict active filtering keeps true and null`() {
        val models = listOf(
            OpenAICompatibleModelResponseDto.Model(id = "m1", isActive = true),
            OpenAICompatibleModelResponseDto.Model(id = "m2", isActive = null),
            OpenAICompatibleModelResponseDto.Model(id = "m3", isActive = false),
        )
        val result = mapOpenAICompatibleModels(models, Service.DeepSeek, selectedModelId = "")
        assertEquals(2, result.size)
        assertTrue(result.any { it.id == "m1" })
        assertTrue(result.any { it.id == "m2" })
    }

    @Test
    fun `mapOpenAICompatibleModels filterByModelType keeps only chat type`() {
        val models = listOf(
            OpenAICompatibleModelResponseDto.Model(id = "m1", type = "chat"),
            OpenAICompatibleModelResponseDto.Model(id = "m2", type = "code"),
            OpenAICompatibleModelResponseDto.Model(id = "m3", type = null),
        )
        val result = mapOpenAICompatibleModels(models, Service.Together, selectedModelId = "")
        assertEquals(1, result.size)
        assertEquals("m1", result[0].id)
    }

    @Test
    fun `mapOpenAICompatibleModels filterByModelType skipped when no types present`() {
        val models = listOf(
            OpenAICompatibleModelResponseDto.Model(id = "m1"),
            OpenAICompatibleModelResponseDto.Model(id = "m2"),
        )
        val result = mapOpenAICompatibleModels(models, Service.Together, selectedModelId = "")
        assertEquals(2, result.size)
    }

    @Test
    fun `mapOpenAICompatibleModels OpenAI prefix filtering`() {
        val models = listOf(
            OpenAICompatibleModelResponseDto.Model(id = "gpt-4"),
            OpenAICompatibleModelResponseDto.Model(id = "o1-preview"),
            OpenAICompatibleModelResponseDto.Model(id = "o3-mini"),
            OpenAICompatibleModelResponseDto.Model(id = "chatgpt-4o"),
            OpenAICompatibleModelResponseDto.Model(id = "dall-e-3"),
            OpenAICompatibleModelResponseDto.Model(id = "whisper-1"),
            OpenAICompatibleModelResponseDto.Model(id = "text-embedding-ada-002"),
        )
        val result = mapOpenAICompatibleModels(models, Service.OpenAI, selectedModelId = "")
        val ids = result.map { it.id }
        assertTrue("gpt-4" in ids)
        assertTrue("o1-preview" in ids)
        assertTrue("o3-mini" in ids)
        assertTrue("chatgpt-4o" in ids)
        assertFalse("dall-e-3" in ids)
        assertFalse("whisper-1" in ids)
        assertFalse("text-embedding-ada-002" in ids)
    }

    @Test
    fun `mapOpenAICompatibleModels deduplication`() {
        val models = listOf(
            OpenAICompatibleModelResponseDto.Model(id = "model-a"),
            OpenAICompatibleModelResponseDto.Model(id = "model-a"),
            OpenAICompatibleModelResponseDto.Model(id = "model-b"),
        )
        val result = mapOpenAICompatibleModels(models, Service.DeepSeek, selectedModelId = "")
        assertEquals(2, result.size)
    }

    @Test
    fun `mapOpenAICompatibleModels sortModelsById sorts alphabetically`() {
        val models = listOf(
            OpenAICompatibleModelResponseDto.Model(id = "zebra-model"),
            OpenAICompatibleModelResponseDto.Model(id = "alpha-model"),
            OpenAICompatibleModelResponseDto.Model(id = "mid-model"),
        )
        val result = mapOpenAICompatibleModels(models, Service.Nvidia, selectedModelId = "")
        assertEquals("alpha-model", result[0].id)
        assertEquals("mid-model", result[1].id)
        assertEquals("zebra-model", result[2].id)
    }

    @Test
    fun `mapOpenAICompatibleModels sorts by context_window descending when not sortModelsById`() {
        val models = listOf(
            OpenAICompatibleModelResponseDto.Model(id = "small", context_window = 4096),
            OpenAICompatibleModelResponseDto.Model(id = "large", context_window = 131072),
            OpenAICompatibleModelResponseDto.Model(id = "medium", context_window = 32768),
        )
        val result = mapOpenAICompatibleModels(models, Service.DeepSeek, selectedModelId = "")
        assertEquals("large", result[0].id)
        assertEquals("medium", result[1].id)
        assertEquals("small", result[2].id)
    }

    @Test
    fun `mapOpenAICompatibleModels propagates context_window to SettingsModel`() {
        val models = listOf(
            OpenAICompatibleModelResponseDto.Model(id = "m1", context_window = 131_072),
        )
        val result = mapOpenAICompatibleModels(models, Service.DeepSeek, selectedModelId = "")
        assertEquals(131_072L, result[0].contextWindow)
    }

    @Test
    fun `mapOpenAICompatibleModels catalog wins over OpenRouter context_length and name`() {
        val models = listOf(
            OpenAICompatibleModelResponseDto.Model(
                id = "anthropic/claude-3.5-sonnet",
                name = "Anthropic: Claude 3.5 Sonnet",
                context_length = 200_000,
            ),
        )
        val result = mapOpenAICompatibleModels(models, Service.OpenRouter, selectedModelId = "")
        // Catalog has "Claude 3.5 Sonnet" and 200_000 — takes priority over API
        assertEquals("Claude 3.5 Sonnet", result[0].displayName)
        assertEquals(200_000L, result[0].contextWindow)
    }

    @Test
    fun `mapOpenAICompatibleModels propagates created epoch as ISO releaseDate`() {
        val models = listOf(
            // 1700000000 = 2023-11-14T22:13:20Z
            OpenAICompatibleModelResponseDto.Model(id = "m1", created = 1_700_000_000),
        )
        val result = mapOpenAICompatibleModels(models, Service.DeepSeek, selectedModelId = "")
        assertEquals("2023-11-14", result[0].releaseDate)
    }

    @Test
    fun `mapOpenAICompatibleModels catalog fills missing context_window for known models`() {
        val models = listOf(
            OpenAICompatibleModelResponseDto.Model(id = "gpt-4o", context_window = null),
        )
        val result = mapOpenAICompatibleModels(models, Service.OpenAI, selectedModelId = "")
        assertEquals(128_000L, result[0].contextWindow)
    }

    @Test
    fun `mapOpenAICompatibleModels catalog context_window wins over API`() {
        val models = listOf(
            OpenAICompatibleModelResponseDto.Model(id = "gpt-4o", context_window = 999_999),
        )
        val result = mapOpenAICompatibleModels(models, Service.OpenAI, selectedModelId = "")
        // Catalog has 128_000 for gpt-4o — takes priority
        assertEquals(128_000L, result[0].contextWindow)
    }

    @Test
    fun `mapOpenAICompatibleModels marks selected model`() {
        val models = listOf(
            OpenAICompatibleModelResponseDto.Model(id = "m1"),
            OpenAICompatibleModelResponseDto.Model(id = "m2"),
        )
        val result = mapOpenAICompatibleModels(models, Service.DeepSeek, selectedModelId = "m2")
        assertFalse(result.first { it.id == "m1" }.isSelected)
        assertTrue(result.first { it.id == "m2" }.isSelected)
    }

    @Test
    fun `mapOpenAICompatibleModels empty list`() {
        val result = mapOpenAICompatibleModels(emptyList(), Service.DeepSeek, selectedModelId = "")
        assertTrue(result.isEmpty())
    }
}
