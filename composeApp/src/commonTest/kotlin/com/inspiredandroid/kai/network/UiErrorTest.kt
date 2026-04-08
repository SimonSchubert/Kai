package com.inspiredandroid.kai.network

import com.inspiredandroid.kai.inference.InferenceTimeoutException
import com.inspiredandroid.kai.inference.InsufficientMemoryException
import com.inspiredandroid.kai.inference.NoModelDownloadedException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class UiErrorTest {

    @Test
    fun `InsufficientMemoryException maps to resource`() {
        val error = InsufficientMemoryException().toUiError()
        assertIs<UiError.Resource>(error)
    }

    @Test
    fun `InferenceTimeoutException maps to resource`() {
        val error = InferenceTimeoutException().toUiError()
        assertIs<UiError.Resource>(error)
    }

    @Test
    fun `NoModelDownloadedException maps to resource`() {
        val error = NoModelDownloadedException().toUiError()
        assertIs<UiError.Resource>(error)
    }

    @Test
    fun `IllegalStateException with message surfaces as UiError Text`() {
        val error = IllegalStateException("Engine not initialized").toUiError()
        assertIs<UiError.Text>(error)
        assertEquals("Engine not initialized", error.message)
    }

    @Test
    fun `exception with null message falls back to error_unknown resource`() {
        val error = RuntimeException().toUiError()
        assertIs<UiError.Resource>(error)
    }

    @Test
    fun `exception with blank message falls back to error_unknown resource`() {
        val error = RuntimeException("  ").toUiError()
        assertIs<UiError.Resource>(error)
    }

    @Test
    fun `known API exceptions still map to their specific resources`() {
        assertIs<UiError.Resource>(GeminiInvalidApiKeyException().toUiError())
        assertIs<UiError.Resource>(GeminiRateLimitExceededException().toUiError())
        assertIs<UiError.Resource>(OpenAICompatibleInvalidApiKeyException().toUiError())
        assertIs<UiError.Resource>(OpenAICompatibleEmptyResponseException().toUiError())
    }

    @Test
    fun `GenericNetworkException surfaces message as Text`() {
        val error = GenericNetworkException("Connection timed out").toUiError()
        assertIs<UiError.Text>(error)
        assertEquals("Connection timed out", error.message)
    }
}
