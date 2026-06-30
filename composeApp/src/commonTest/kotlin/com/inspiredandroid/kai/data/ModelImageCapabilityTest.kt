package com.inspiredandroid.kai.data

import com.inspiredandroid.kai.data.providers.buildOpenAIMessages
import com.inspiredandroid.kai.ui.chat.History
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Per-model image capability: text-only models on a service that otherwise accepts images
 * (e.g. Z.AI hosts text GLM models alongside the multimodal GLM-V variants) must have image
 * attachments stripped, while the vision variants keep them. Sending image content-parts to a
 * text-only model returns a hard 400 that poisons the rest of the chat.
 */
class ModelImageCapabilityTest {

    @Test
    fun `text-only models are flagged, vision variants are not`() {
        // DeepSeek — whole API is text-only (matched by family prefix).
        assertFalse(modelSupportsImages("deepseek-chat"))
        assertFalse(modelSupportsImages("deepseek-reasoner"))
        assertFalse(modelSupportsImages("deepseek-v4-flash"))
        assertFalse(modelSupportsImages("deepseek-r1-distill-qwen-32b"))
        // GLM text models.
        assertFalse(modelSupportsImages("glm-4.6"))
        assertFalse(modelSupportsImages("glm-5.2"))
        assertFalse(modelSupportsImages("glm-4.7"))
        // GLM vision variants keep images.
        assertTrue(modelSupportsImages("glm-4.6v"))
        assertTrue(modelSupportsImages("glm-4.5v"))
        assertTrue(modelSupportsImages("glm-4v-plus"))
        assertTrue(modelSupportsImages("glm-5v-turbo"))
    }

    @Test
    fun `provider prefixes are stripped and matching is case-insensitive`() {
        assertFalse(modelSupportsImages("z-ai/glm-4.6"))
        assertFalse(modelSupportsImages("deepseek/DeepSeek-Chat"))
        assertFalse(modelSupportsImages("openrouter/deepseek/deepseek-v3.2"))
    }

    @Test
    fun `unknown and known multimodal models default to supported`() {
        assertTrue(modelSupportsImages("gpt-4o"))
        assertTrue(modelSupportsImages("some-brand-new-model"))
        assertTrue(modelSupportsImages(""))
    }

    @Test
    fun `DeepSeek-VL is recognised as multimodal despite the deepseek prefix`() {
        assertTrue(modelSupportsImages("deepseek-vl2"))
        assertTrue(modelSupportsImages("deepseek-ai/deepseek-vl2-tiny"))
    }

    private fun userWithImage() = History(
        role = History.Role.USER,
        content = "what's in this picture?",
        attachments = persistentListOf(
            Attachment(data = "BASE64IMAGEDATA", mimeType = "image/png", fileName = "cat.png"),
        ),
    )

    @Test
    fun `buildOpenAIMessages drops images for a text-only model on an image-capable service`() {
        val messages = buildOpenAIMessages(
            service = Service.Zai,
            messages = listOf(userWithImage()),
            systemPrompt = null,
            modelId = "glm-4.6",
        )
        assertEquals(JsonPrimitive("what's in this picture?"), messages.single().content)
    }

    @Test
    fun `buildOpenAIMessages keeps images for a vision model on the same service`() {
        val messages = buildOpenAIMessages(
            service = Service.Zai,
            messages = listOf(userWithImage()),
            systemPrompt = null,
            modelId = "glm-4.6v",
        )
        assertTrue(messages.single().content is JsonArray)
    }
}
