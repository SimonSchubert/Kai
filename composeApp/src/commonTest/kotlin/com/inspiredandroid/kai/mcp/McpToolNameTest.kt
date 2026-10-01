package com.inspiredandroid.kai.mcp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class McpToolNameTest {

    @Test
    fun `free names are kept as-is`() {
        assertEquals("search", uniqueMcpToolName("github", "search", setOf("web_search")))
    }

    @Test
    fun `clashes are prefixed with the server id`() {
        assertEquals("github_search", uniqueMcpToolName("github", "search", setOf("search")))
        assertEquals("github_search_2", uniqueMcpToolName("github", "search", setOf("search", "github_search")))
    }

    @Test
    fun `renamed tools stay within provider name rules`() {
        val name = uniqueMcpToolName("my server.v2", "x".repeat(80), setOf("x".repeat(80)))
        assertTrue(name.length <= 64, name)
        assertTrue(name.all { it.isLetterOrDigit() || it == '_' || it == '-' }, name)
    }
}
