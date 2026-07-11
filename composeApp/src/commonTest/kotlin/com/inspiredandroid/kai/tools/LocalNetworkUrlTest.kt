package com.inspiredandroid.kai.tools

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalNetworkUrlTest {

    @Test
    fun `private ipv4 hosts are local`() {
        assertTrue(isLocalNetworkUrl("http://192.168.1.42:1337/v1"))
        assertTrue(isLocalNetworkUrl("http://10.0.0.5"))
        assertTrue(isLocalNetworkUrl("https://172.16.0.1:8080"))
        assertTrue(isLocalNetworkUrl("http://172.31.255.254/v1"))
        assertTrue(isLocalNetworkUrl("http://169.254.1.1"))
    }

    @Test
    fun `public hosts are not local`() {
        assertFalse(isLocalNetworkUrl("https://api.openai.com/v1"))
        assertFalse(isLocalNetworkUrl("https://8.8.8.8"))
        assertFalse(isLocalNetworkUrl("http://172.32.0.1"))
        assertFalse(isLocalNetworkUrl("http://172.15.0.1"))
        assertFalse(isLocalNetworkUrl("https://example.com:443/path"))
    }

    @Test
    fun `loopback is not gated`() {
        assertFalse(isLocalNetworkUrl("http://localhost:1337"))
        assertFalse(isLocalNetworkUrl("http://127.0.0.1:11434"))
        assertFalse(isLocalNetworkUrl("http://[::1]:8080"))
    }

    @Test
    fun `mdns and bare hostnames are local`() {
        assertTrue(isLocalNetworkUrl("http://jan-server.local:1337"))
        assertTrue(isLocalNetworkUrl("http://fritz-nas:8080/v1"))
    }

    @Test
    fun `ipv6 link-local and unique-local are local`() {
        assertTrue(isLocalNetworkUrl("http://[fe80::1%25en0]:1337"))
        assertTrue(isLocalNetworkUrl("http://[fd00::abcd]:8080"))
        assertFalse(isLocalNetworkUrl("https://[2001:db8::1]:443"))
    }

    @Test
    fun `blank url is not local`() {
        assertFalse(isLocalNetworkUrl(""))
    }
}
