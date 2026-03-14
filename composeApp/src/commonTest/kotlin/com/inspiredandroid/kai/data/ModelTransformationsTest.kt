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
    fun `mapAnthropicModels uses display_name as subtitle`() {
        val models = listOf(
            AnthropicModelsResponseDto.ModelInfo(id = "claude-sonnet-4-20250514", display_name = "Claude Sonnet 4"),
        )
        val result = mapAnthropicModels(models, selectedModelId = "")
        assertEquals("Claude Sonnet 4", result[0].subtitle)
    }

    @Test
    fun `mapAnthropicModels falls back to id when display_name is null`() {
        val models = listOf(
            AnthropicModelsResponseDto.ModelInfo(id = "claude-3-haiku-20240307"),
        )
        val result = mapAnthropicModels(models, selectedModelId = "")
        assertEquals("claude-3-haiku-20240307", result[0].subtitle)
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
    fun `mapGeminiModels sorts by version descending then pro before flash`() {
        val models = listOf(
            GeminiModelsResponseDto.Model(
                name = "models/gemini-1.5-flash",
                supportedGenerationMethods = listOf("generateContent"),
            ),
            GeminiModelsResponseDto.Model(
                name = "models/gemini-2.0-flash",
                supportedGenerationMethods = listOf("generateContent"),
            ),
            GeminiModelsResponseDto.Model(
                name = "models/gemini-2.0-pro",
                supportedGenerationMethods = listOf("generateContent"),
            ),
            GeminiModelsResponseDto.Model(
                name = "models/gemini-1.5-pro",
                supportedGenerationMethods = listOf("generateContent"),
            ),
        )
        val result = mapGeminiModels(models, selectedModelId = "")
        assertEquals("gemini-2.0-pro", result[0].id)
        assertEquals("gemini-2.0-flash", result[1].id)
        assertEquals("gemini-1.5-pro", result[2].id)
        assertEquals("gemini-1.5-flash", result[3].id)
    }

    @Test
    fun `mapGeminiModels uses displayName as subtitle with fallback`() {
        val models = listOf(
            GeminiModelsResponseDto.Model(
                name = "models/gemini-2.0-flash",
                displayName = "Gemini 2.0 Flash",
                supportedGenerationMethods = listOf("generateContent"),
            ),
            GeminiModelsResponseDto.Model(
                name = "models/gemini-1.5-pro",
                displayName = null,
                supportedGenerationMethods = listOf("generateContent"),
            ),
        )
        val result = mapGeminiModels(models, selectedModelId = "")
        val flashModel = result.first { it.id == "gemini-2.0-flash" }
        val proModel = result.first { it.id == "gemini-1.5-pro" }
        assertEquals("Gemini 2.0 Flash", flashModel.subtitle)
        assertEquals("gemini-1.5-pro", proModel.subtitle)
    }

    @Test
    fun `mapGeminiModels preserves description`() {
        val models = listOf(
            GeminiModelsResponseDto.Model(
                name = "models/gemini-2.0-flash",
                description = "Fast and versatile",
                supportedGenerationMethods = listOf("generateContent"),
            ),
        )
        val result = mapGeminiModels(models, selectedModelId = "")
        assertEquals("Fast and versatile", result[0].description)
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

    // --- Gemini comparator ---

    @Test
    fun `extractGeminiVersion parses version from model id`() {
        assertEquals(2.5, extractGeminiVersion("gemini-2.5-pro"))
        assertEquals(2.0, extractGeminiVersion("gemini-2.0-flash"))
        assertEquals(1.5, extractGeminiVersion("gemini-1.5-pro"))
        assertEquals(0.0, extractGeminiVersion("some-other-model"))
    }

    @Test
    fun `getGeminiModelPriority returns correct priorities`() {
        assertEquals(0, getGeminiModelPriority("gemini-2.5-pro"))
        assertEquals(1, getGeminiModelPriority("gemini-2.0-flash"))
        assertEquals(2, getGeminiModelPriority("gemini-2.0-nano"))
        // "pro" in name but also "flash" → flash wins
        assertEquals(1, getGeminiModelPriority("gemini-2.0-flash-pro"))
    }

    @Test
    fun `geminiModelComparator sorts correctly`() {
        val models = listOf(
            SettingsModel(id = "gemini-1.5-flash", subtitle = ""),
            SettingsModel(id = "gemini-2.5-pro", subtitle = ""),
            SettingsModel(id = "gemini-2.5-flash", subtitle = ""),
            SettingsModel(id = "gemini-2.0-pro", subtitle = ""),
        )
        val sorted = models.sortedWith(geminiModelComparator)
        assertEquals("gemini-2.5-pro", sorted[0].id)
        assertEquals("gemini-2.5-flash", sorted[1].id)
        assertEquals("gemini-2.0-pro", sorted[2].id)
        assertEquals("gemini-1.5-flash", sorted[3].id)
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
    fun `mapOpenAICompatibleModels owned_by mapped to subtitle`() {
        val models = listOf(
            OpenAICompatibleModelResponseDto.Model(id = "m1", owned_by = "meta"),
            OpenAICompatibleModelResponseDto.Model(id = "m2", owned_by = null),
        )
        val result = mapOpenAICompatibleModels(models, Service.DeepSeek, selectedModelId = "")
        assertEquals("meta", result.first { it.id == "m1" }.subtitle)
        assertEquals("", result.first { it.id == "m2" }.subtitle)
    }

    @Test
    fun `mapOpenAICompatibleModels includeModelDate false omits description`() {
        val models = listOf(
            OpenAICompatibleModelResponseDto.Model(id = "m1", created = 1700000000),
        )
        val result = mapOpenAICompatibleModels(models, Service.Nvidia, selectedModelId = "")
        assertNull(result[0].description)
    }

    @Test
    fun `mapOpenAICompatibleModels includeModelDate true includes description`() {
        val models = listOf(
            OpenAICompatibleModelResponseDto.Model(id = "m1", created = 1700000000),
        )
        val result = mapOpenAICompatibleModels(models, Service.DeepSeek, selectedModelId = "")
        // Should have a non-null description (the human readable date)
        assertTrue(result[0].description != null)
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
