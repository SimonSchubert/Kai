package com.inspiredandroid.kai.tools

import io.ktor.http.Url
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FetchUrlHostTest {

    @Test
    fun `loopback private and link-local literals are blocked`() {
        listOf(
            "localhost", "app.localhost", "127.0.0.1", "10.1.2.3", "192.168.0.10", "172.20.0.1",
            "169.254.169.254", "0.0.0.0", "100.64.0.1", "[::1]", "::", "fe80::1", "fd12::1", "fc00::1",
        ).forEach { assertTrue(isBlockedFetchHost(it), it) }
    }

    @Test
    fun `ipv4-mapped ipv6 is judged by its embedded address`() {
        assertTrue(isBlockedFetchHost("[::ffff:127.0.0.1]"))
        assertTrue(isBlockedFetchHost("::ffff:7f00:1"))
        assertTrue(isBlockedFetchHost("::ffff:c0a8:0101"))
        assertFalse(isBlockedFetchHost("::ffff:8.8.8.8"))
    }

    @Test
    fun `numeric shorthand that resolves to an address is blocked`() {
        listOf("2130706433", "127.1", "0x7f.0.0.1", "0x7f000001").forEach { assertTrue(isBlockedFetchHost(it), it) }
    }

    @Test
    fun `public hosts are allowed, including names that look like ipv6 prefixes`() {
        listOf("fdic.gov", "fcc.gov", "example.com", "8.8.8.8", "172.32.0.1", "cafe.de", "2001:4860:4860::8888")
            .forEach { assertFalse(isBlockedFetchHost(it), it) }
    }

    @Test
    fun `redirect targets drop the old query and resolve relative paths`() {
        val from = Url("https://a.example/start?url=x#frag")
        assertEquals("https://b.example/", resolveRedirect(from, "https://b.example/").toString())
        assertEquals("https://a.example/landing?b=2", resolveRedirect(from, "/landing?b=2").toString())
        assertEquals("http://127.0.0.1/secret", resolveRedirect(from, "http://127.0.0.1/secret").toString())
        assertTrue(isBlockedFetchHost(resolveRedirect(from, "http://127.0.0.1/secret").host))
    }
}
