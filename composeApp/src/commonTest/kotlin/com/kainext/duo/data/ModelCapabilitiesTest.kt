package com.kainext.duo.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModelCapabilitiesTest {

    @Test
    fun `deepseek v4 and newer accept images`() {
        val multimodal = listOf(
            "deepseek-v4-flash",
            "deepseek-v4.1-flash",
            "deepseek-v4.1-flash-max",
            "deepseek-v4-pro",
            "deepseek-v4-pro-thinking",
            "deepseek-v4-flash:free",
            "deepseek-v4-flash-free",
            "deepseek-v5",
        )
        for (id in multimodal) {
            assertTrue(modelSupportsImages(id), "$id should accept images")
        }
    }

    @Test
    fun `deepseek v3 and older stay text-only`() {
        val textOnly = listOf(
            "deepseek-chat",
            "deepseek-reasoner",
            "deepseek-v3",
            "deepseek-v3.1",
            "deepseek-v3.2",
            "deepseek-r1",
            "deepseek-coder-v2",
        )
        for (id in textOnly) {
            assertFalse(modelSupportsImages(id), "$id should stay text-only")
        }
    }

    @Test
    fun `deepseek vision family still accepts images`() {
        assertTrue(modelSupportsImages("deepseek-vl2"))
        assertTrue(modelSupportsImages("deepseek-vl-7b-chat"))
    }

    @Test
    fun `aggregator prefixes are stripped before the deepseek check`() {
        assertTrue(modelSupportsImages("openrouter/deepseek/deepseek-v4.1-flash"))
        assertFalse(modelSupportsImages("openrouter/deepseek/deepseek-v3.2"))
    }

    @Test
    fun `non-deepseek models keep the curated text-only list`() {
        assertFalse(modelSupportsImages("glm-4.6"))
        assertTrue(modelSupportsImages("glm-4.6v"))
        assertTrue(modelSupportsImages("some-unknown-model"))
    }

    @Test
    fun `provider declaration wins over the id heuristic`() {
        val service = Service.DeepSeek
        ModelCapabilityRegistry.clear()
        // A provider that says a V3-era id takes images overrides the text-only heuristic.
        ModelCapabilityRegistry.record(
            service,
            listOf(
                com.kainext.duo.network.dtos.openaicompatible.OpenAICompatibleModelResponseDto.Model(
                    id = "deepseek-chat",
                    architecture = com.kainext.duo.network.dtos.openaicompatible.OpenAICompatibleModelResponseDto.Model.Architecture(
                        inputModalities = listOf("text", "image"),
                    ),
                ),
                com.kainext.duo.network.dtos.openaicompatible.OpenAICompatibleModelResponseDto.Model(
                    id = "deepseek-v4.1-flash",
                    architecture = com.kainext.duo.network.dtos.openaicompatible.OpenAICompatibleModelResponseDto.Model.Architecture(
                        inputModalities = listOf("text"),
                    ),
                ),
            ),
        )
        assertTrue(modelSupportsImages("deepseek-chat"))
        assertFalse(modelSupportsImages("deepseek-v4.1-flash"))
        ModelCapabilityRegistry.clear()
    }

    @Test
    fun `flat modality string is parsed for the input side only`() {
        val service = Service.DeepSeek
        ModelCapabilityRegistry.clear()
        ModelCapabilityRegistry.record(
            service,
            listOf(
                com.kainext.duo.network.dtos.openaicompatible.OpenAICompatibleModelResponseDto.Model(
                    id = "deepseek-v4.1-flash",
                    modality = "text+image->text",
                ),
                com.kainext.duo.network.dtos.openaicompatible.OpenAICompatibleModelResponseDto.Model(
                    id = "deepseek-chat",
                    modality = "text->text+image",
                ),
            ),
        )
        assertTrue(modelSupportsImages("deepseek-v4.1-flash"))
        // Image appears only on the output side, so inputs stay text-only.
        assertFalse(modelSupportsImages("deepseek-chat"))
        ModelCapabilityRegistry.clear()
    }

    @Test
    fun `no declaration falls back to the id rules`() {
        ModelCapabilityRegistry.clear()
        assertTrue(modelSupportsImages("deepseek-v4.1-flash"))
        assertFalse(modelSupportsImages("deepseek-v3.2"))
    }
}
