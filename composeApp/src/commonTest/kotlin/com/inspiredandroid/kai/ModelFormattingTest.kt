package com.inspiredandroid.kai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ModelFormattingTest {

    @Test
    fun `formatContextWindow renders millions`() {
        assertEquals("1M", formatContextWindow(1_000_000))
        assertEquals("2M", formatContextWindow(2_000_000))
    }

    @Test
    fun `formatContextWindow renders thousands`() {
        assertEquals("128K", formatContextWindow(128_000))
        assertEquals("200K", formatContextWindow(200_000))
    }

    @Test
    fun `formatContextWindow renders small counts verbatim`() {
        assertEquals("512", formatContextWindow(512))
    }

    @Test
    fun `formatReleaseDate accepts year-month`() {
        assertEquals("Mar 2025", formatReleaseDate("2025-03"))
    }

    @Test
    fun `formatReleaseDate accepts full iso date`() {
        assertEquals("Sep 2025", formatReleaseDate("2025-09-29"))
    }

    @Test
    fun `formatReleaseDate falls back on invalid input`() {
        assertEquals("not-a-date", formatReleaseDate("not-a-date"))
        assertEquals("2025-13", formatReleaseDate("2025-13"))
    }

    @Test
    fun `toIsoDate converts epoch seconds to iso date`() {
        // 1700000000 = 2023-11-14T22:13:20Z
        assertEquals("2023-11-14", 1_700_000_000L.toIsoDate())
    }

    @Test
    fun `toIsoDate returns null for zero or negative epoch`() {
        // Providers that return 0 instead of omitting `created` must not
        // surface as "Jan 1970".
        assertNull(0L.toIsoDate())
        assertNull((-1L).toIsoDate())
    }
}
