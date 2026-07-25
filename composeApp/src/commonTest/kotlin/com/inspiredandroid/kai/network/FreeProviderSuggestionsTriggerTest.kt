package com.inspiredandroid.kai.network

import com.inspiredandroid.kai.data.FreeProviderSuggestion
import com.inspiredandroid.kai.data.Service
import com.inspiredandroid.kai.data.freeProviderSuggestions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FreeProviderSuggestionsTriggerTest {

    @Test
    fun `rate limit with no services shows free provider suggestions`() {
        assertTrue(
            shouldShowFreeProviderSuggestions(
                noConfiguredServices = true,
                exception = OpenAICompatibleRateLimitExceededException(),
            ),
        )
    }

    @Test
    fun `quota exhausted with no services shows free provider suggestions`() {
        assertTrue(
            shouldShowFreeProviderSuggestions(
                noConfiguredServices = true,
                exception = OpenAICompatibleQuotaExhaustedException(),
            ),
        )
    }

    @Test
    fun `rate limit with configured services does not show suggestions`() {
        assertFalse(
            shouldShowFreeProviderSuggestions(
                noConfiguredServices = false,
                exception = OpenAICompatibleRateLimitExceededException(),
            ),
        )
    }

    @Test
    fun `invalid api key with no services does not show suggestions`() {
        assertFalse(
            shouldShowFreeProviderSuggestions(
                noConfiguredServices = true,
                exception = OpenAICompatibleInvalidApiKeyException(),
            ),
        )
    }

    @Test
    fun `isFreeCapacityError only matches capacity failures`() {
        assertTrue(OpenAICompatibleRateLimitExceededException().isFreeCapacityError())
        assertTrue(OpenAICompatibleQuotaExhaustedException().isFreeCapacityError())
        assertFalse(OpenAICompatibleInvalidApiKeyException().isFreeCapacityError())
        assertFalse(OpenAICompatibleEmptyResponseException().isFreeCapacityError())
        assertFalse(AllServicesFailedException().isFreeCapacityError())
    }

    @Test
    fun `kai9000 all free providers failed message shows suggestions`() {
        val exception = OpenAICompatibleProviderErrorException("All free providers failed")
        assertTrue(exception.isFreeCapacityError())
        assertTrue(
            shouldShowFreeProviderSuggestions(
                noConfiguredServices = true,
                exception = exception,
            ),
        )
        assertFalse(
            shouldShowFreeProviderSuggestions(
                noConfiguredServices = false,
                exception = exception,
            ),
        )
    }

    @Test
    fun `unrelated provider error does not show suggestions`() {
        assertFalse(
            OpenAICompatibleProviderErrorException("Internal server error in model runtime")
                .isFreeCapacityError(),
        )
    }

    @Test
    fun `free provider catalog lists top free-usage services with signup urls`() {
        assertEquals(
            listOf(
                Service.Groq.id,
                Service.Cerebras.id,
                Service.Gemini.id,
                Service.OpenRouter.id,
                Service.OllamaCloud.id,
            ),
            freeProviderSuggestions.map { it.service.id },
        )
        freeProviderSuggestions.forEach { suggestion: FreeProviderSuggestion ->
            assertNotNull(suggestion.service.apiKeyUrl)
            assertTrue(suggestion.signupUrl.startsWith("http"))
        }
    }
}
