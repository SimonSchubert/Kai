package com.inspiredandroid.kai.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SandboxUriHandlerTest {

    @Test
    fun `file scheme link maps to sandbox path`() {
        assertEquals("/root/climber_action.gif", toSandboxPath("file:///root/climber_action.gif"))
    }

    @Test
    fun `bare absolute path maps to itself`() {
        assertEquals("/root/out.png", toSandboxPath("/root/out.png"))
    }

    @Test
    fun `percent escapes are decoded`() {
        assertEquals("/root/my file.gif", toSandboxPath("file:///root/my%20file.gif"))
    }

    @Test
    fun `http and https links are not sandbox paths`() {
        assertNull(toSandboxPath("https://example.com/a.gif"))
        assertNull(toSandboxPath("http://example.com"))
    }

    @Test
    fun `mailto and other schemes are not sandbox paths`() {
        assertNull(toSandboxPath("mailto:someone@example.com"))
        assertNull(toSandboxPath("tel:12345"))
    }

    @Test
    fun `relative link is not a sandbox path`() {
        assertNull(toSandboxPath("foo/bar.gif"))
    }
}
