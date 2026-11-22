package com.inspiredandroid.kai.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModelCapabilitiesTest {

    @Test
    fun `modelSupportsImages accepts deepseek-flash`() {
        assertTrue(modelSupportsImages("deepseek-flash"))
    }

    @Test
    fun `modelSupportsImages accepts deepseek v4 point 1 flash through an aggregator prefix`() {
        assertTrue(modelSupportsImages("deepseek/deepseek-v4.1-flash"))
    }

    @Test
    fun `modelSupportsImages ignores case for deepseek-flash`() {
        assertTrue(modelSupportsImages("DeepSeek-Flash"))
    }

    @Test
    fun `modelSupportsImages keeps other deepseek text models text-only`() {
        assertFalse(modelSupportsImages("deepseek-chat"))
        assertFalse(modelSupportsImages("deepseek-reasoner"))
        assertFalse(modelSupportsImages("deepseek-v4-pro"))
        assertFalse(modelSupportsImages("deepseek/deepseek-v4-flash"))
    }

    @Test
    fun `modelSupportsImages keeps deepseek-vl multimodal`() {
        assertTrue(modelSupportsImages("deepseek-vl2"))
    }

    @Test
    fun `modelSupportsImages keeps glm text models text-only and defaults unknown models to true`() {
        assertFalse(modelSupportsImages("glm-4.6"))
        assertTrue(modelSupportsImages("some-unknown-model"))
    }
}
