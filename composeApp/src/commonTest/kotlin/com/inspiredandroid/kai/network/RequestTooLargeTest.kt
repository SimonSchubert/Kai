package com.inspiredandroid.kai.network

import com.inspiredandroid.kai.data.Service
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** A 413 only reads as "image too large" when the provider says nothing better (#491). */
class RequestTooLargeTest {

    @Test
    fun tokenPerMinuteRejectionShowsTheProviderMessage() {
        val detail = "Request too large for model `llama-3.3-70b-versatile` on tokens per minute (TPM): Limit 12000, Requested 15000"
        val e = requestTooLargeException(Service.Groq, detail)
        assertIs<OpenAICompatibleGenericException>(e)
        assertEquals("${Service.Groq.displayName}: $detail", e.message)
    }

    @Test
    fun missingDetailFallsBackToImageTooLarge() {
        assertIs<OpenAICompatibleRequestTooLargeException>(requestTooLargeException(Service.Groq, null))
        assertIs<OpenAICompatibleRequestTooLargeException>(requestTooLargeException(Service.Groq, " "))
    }

    @Test
    fun imageRelatedDetailKeepsImageTooLarge() {
        assertIs<OpenAICompatibleRequestTooLargeException>(requestTooLargeException(Service.Groq, "Image exceeds 4MB limit"))
    }
}
