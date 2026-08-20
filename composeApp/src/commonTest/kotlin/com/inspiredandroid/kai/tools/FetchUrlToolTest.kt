package com.inspiredandroid.kai.tools

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FetchUrlToolTest {

    @Test
    fun `isBlockedHost blocks localhost and loopback addresses`() {
        assertTrue(FetchUrlTool.isBlockedHost("localhost"))
        assertTrue(FetchUrlTool.isBlockedHost("test.localhost"))
        assertTrue(FetchUrlTool.isBlockedHost("127.0.0.1"))
        assertTrue(FetchUrlTool.isBlockedHost("127.127.127.127"))
        assertTrue(FetchUrlTool.isBlockedHost("::1"))
        assertTrue(FetchUrlTool.isBlockedHost("[::1]"))
        assertTrue(FetchUrlTool.isBlockedHost("0:0:0:0:0:0:0:1"))
    }

    @Test
    fun `isBlockedHost blocks private IPv4 addresses`() {
        assertTrue(FetchUrlTool.isBlockedHost("10.0.0.1"))
        assertTrue(FetchUrlTool.isBlockedHost("10.255.255.255"))
        assertTrue(FetchUrlTool.isBlockedHost("172.16.0.0"))
        assertTrue(FetchUrlTool.isBlockedHost("172.31.255.255"))
        assertTrue(FetchUrlTool.isBlockedHost("192.168.0.0"))
        assertTrue(FetchUrlTool.isBlockedHost("192.168.255.255"))
    }

    @Test
    fun `isBlockedHost blocks link-local and unique-local IPv6 addresses`() {
        assertTrue(FetchUrlTool.isBlockedHost("fe80::1"))
        assertTrue(FetchUrlTool.isBlockedHost("[fe80::1]"))
        assertTrue(FetchUrlTool.isBlockedHost("fc00::"))
        assertTrue(FetchUrlTool.isBlockedHost("fd00::"))
    }

    @Test
    fun `isBlockedHost blocks unspecified addresses and link-local IPv4`() {
        assertTrue(FetchUrlTool.isBlockedHost(""))
        assertTrue(FetchUrlTool.isBlockedHost("0.0.0.0"))
        assertTrue(FetchUrlTool.isBlockedHost("169.254.169.254"))
    }

    @Test
    fun `isBlockedHost allows public hosts and IPs`() {
        assertFalse(FetchUrlTool.isBlockedHost("google.com"))
        assertFalse(FetchUrlTool.isBlockedHost("example.org"))
        assertFalse(FetchUrlTool.isBlockedHost("8.8.8.8"))
        assertFalse(FetchUrlTool.isBlockedHost("1.1.1.1"))
        assertFalse(FetchUrlTool.isBlockedHost("172.15.255.255")) // Just outside private range
        assertFalse(FetchUrlTool.isBlockedHost("172.32.0.0")) // Just outside private range
        assertFalse(FetchUrlTool.isBlockedHost("192.167.255.255")) // Just outside private range
        assertFalse(FetchUrlTool.isBlockedHost("192.169.0.0")) // Just outside private range
    }
}
