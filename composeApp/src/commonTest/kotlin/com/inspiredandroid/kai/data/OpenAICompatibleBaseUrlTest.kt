package com.inspiredandroid.kai.data

import kotlin.test.Test
import kotlin.test.assertEquals

/** A pasted full chat endpoint must not end up as `/chat/completions/chat/completions` (#516). */
class OpenAICompatibleBaseUrlTest {

    private fun normalize(url: String) = Service.normalizeOpenAICompatibleBaseUrl(url)

    @Test
    fun plainBaseUrlIsUnchanged() {
        assertEquals("https://api.example.com/v1", normalize("https://api.example.com/v1"))
        assertEquals("https://api.example.com/v1", normalize("https://api.example.com/v1/"))
    }

    @Test
    fun pastedChatEndpointIsStrippedToTheBase() {
        assertEquals("https://api.example.com/v1", normalize("https://api.example.com/v1/chat/completions"))
        assertEquals("https://api.example.com/v1", normalize(" https://api.example.com/v1/chat/completions/ "))
        assertEquals("https://api.example.com/v1", normalize("https://api.example.com/v1/Chat/Completions"))
    }

    @Test
    fun blankFallsBackToTheDefault() {
        assertEquals(Service.DEFAULT_OPENAI_COMPATIBLE_BASE_URL, normalize(""))
    }
}
