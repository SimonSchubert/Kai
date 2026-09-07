package com.inspiredandroid.kai.network

import com.inspiredandroid.kai.data.Service
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * OpenCode Zen rejects requests that arrive without `x-opencode-session`. The id has to be
 * stable for the whole conversation, and no other provider may see the header.
 */
class SessionHeadersTest {

    private val header = "x-opencode-session"

    @Test
    fun openCodeRequestCarriesTheConversationIdAsSessionId() {
        assertEquals(
            mapOf(header to "conv-42"),
            sessionHeadersFor(Service.OpenCode, "conv-42"),
        )
    }

    @Test
    fun sameConversationKeepsTheSameSessionId() {
        assertEquals(
            sessionHeadersFor(Service.OpenCode, "conv-42"),
            sessionHeadersFor(Service.OpenCode, "conv-42"),
        )
    }

    @Test
    fun requestsOutsideAnyConversationStillCarryAStableSessionId() {
        val first = sessionHeadersFor(Service.OpenCode, null)[header]
        val blank = sessionHeadersFor(Service.OpenCode, "  ")[header]
        assertNotNull(first)
        assertTrue(first.isNotBlank())
        assertEquals(first, blank)
    }

    @Test
    fun otherProvidersGetNoSessionHeader() {
        for (service in Service.all.filter { it != Service.OpenCode }) {
            assertEquals(emptyMap(), sessionHeadersFor(service, "conv-42"), "unexpected header for ${service.id}")
        }
    }
}
