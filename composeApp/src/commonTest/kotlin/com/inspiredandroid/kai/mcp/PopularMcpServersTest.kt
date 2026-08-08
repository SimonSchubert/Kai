package com.inspiredandroid.kai.mcp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PopularMcpServersTest {

    @Test
    fun `jina popular entry requires auth and has no default secret headers`() {
        val jina = popularMcpServers.single { it.name == "Jina AI" }
        assertTrue(jina.requiresAuth)
        assertTrue(jina.headers.isEmpty())
    }

    @Test
    fun `authorizationHeaderValue prefixes Bearer when missing`() {
        assertEquals("Bearer jina_abc", authorizationHeaderValue("jina_abc"))
        assertEquals("Bearer jina_abc", authorizationHeaderValue("  jina_abc  "))
        assertEquals("Bearer jina_abc", authorizationHeaderValue("Bearer jina_abc"))
        // Already has a Bearer prefix (any casing) — leave value as-is
        assertEquals("bearer jina_abc", authorizationHeaderValue("bearer jina_abc"))
        assertEquals("", authorizationHeaderValue("   "))
    }

    @Test
    fun `mergeMissingHeaders keeps existing Authorization`() {
        val existing = mapOf("Authorization" to "Bearer user-key", "X-Custom" to "1")
        val defaults = mapOf("Authorization" to "Bearer default-key", "X-Other" to "2")
        val merged = mergeMissingHeaders(existing, defaults)
        assertEquals("Bearer user-key", merged["Authorization"])
        assertEquals("1", merged["X-Custom"])
        assertEquals("2", merged["X-Other"])
    }

    @Test
    fun `mergeMissingHeaders is case insensitive for existing keys`() {
        val existing = mapOf("authorization" to "Bearer user-key")
        val defaults = mapOf("Authorization" to "Bearer default-key")
        val merged = mergeMissingHeaders(existing, defaults)
        assertEquals(1, merged.size)
        assertEquals("Bearer user-key", merged["authorization"])
    }

    @Test
    fun `mergeMissingHeaders adds missing defaults`() {
        val existing = emptyMap<String, String>()
        val defaults = mapOf("Authorization" to "Bearer default-key")
        assertEquals(defaults, mergeMissingHeaders(existing, defaults))
    }

    @Test
    fun `matchesPopularMcpUrl treats jina v1 and sse as same host`() {
        assertTrue(matchesPopularMcpUrl("https://mcp.jina.ai/v1", "https://mcp.jina.ai/v1"))
        assertTrue(matchesPopularMcpUrl("https://mcp.jina.ai/sse", "https://mcp.jina.ai/v1"))
        assertTrue(matchesPopularMcpUrl("https://mcp.jina.ai/v1/", "https://mcp.jina.ai/v1"))
        assertFalse(matchesPopularMcpUrl("https://search.parallel.ai/mcp", "https://mcp.jina.ai/v1"))
        assertFalse(matchesPopularMcpUrl("https://example.com/mcp", "https://mcp.context7.com/mcp"))
    }

    @Test
    fun `applyPopularDefaultHeaders is no-op for jina without default headers`() {
        val servers = listOf(
            McpServerConfig(id = "jina_ai", name = "Jina AI", url = "https://mcp.jina.ai/v1"),
        )
        val updated = applyPopularDefaultHeaders(servers)
        assertSame(servers, updated)
        assertTrue(updated[0].headers.isEmpty())
    }

    @Test
    fun `applyPopularDefaultHeaders is no-op when nothing to migrate`() {
        val servers = listOf(
            McpServerConfig(id = "ctx", name = "Context7", url = "https://mcp.context7.com/mcp"),
        )
        val updated = applyPopularDefaultHeaders(servers)
        assertSame(servers, updated)
    }
}
