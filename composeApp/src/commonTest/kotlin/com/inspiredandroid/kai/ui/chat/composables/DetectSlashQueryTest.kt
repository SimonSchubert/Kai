package com.inspiredandroid.kai.ui.chat.composables

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DetectSlashQueryTest {

    @Test
    fun `returns token while typing slash command`() {
        assertEquals("su", detectSlashQuery("/su", cursor = 3))
    }

    @Test
    fun `lowercases the query`() {
        assertEquals("sum", detectSlashQuery("/SUM", cursor = 4))
    }

    @Test
    fun `returns empty string right after slash`() {
        assertEquals("", detectSlashQuery("/", cursor = 1))
    }

    @Test
    fun `null when slash is not leading`() {
        assertNull(detectSlashQuery("hello /foo", cursor = 10))
    }

    @Test
    fun `null when cursor is past the first space`() {
        assertNull(detectSlashQuery("/foo bar", cursor = 6))
    }

    @Test
    fun `returns full token when cursor sits within the token before a space`() {
        // The whole first token is returned regardless of where in it the cursor sits.
        assertEquals("foo", detectSlashQuery("/foo bar", cursor = 3))
        assertEquals("foo", detectSlashQuery("/foo bar", cursor = 4))
    }

    @Test
    fun `null for plain text`() {
        assertNull(detectSlashQuery("just a message", cursor = 4))
    }
}
