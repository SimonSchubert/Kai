package com.inspiredandroid.kai.data

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class CronExpressionTest {

    private val utc = TimeZone.UTC

    private fun instant(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Instant {
        val dt = LocalDateTime(LocalDate(year, month, day), LocalTime(hour, minute, 0, 0))
        val kxInstant = dt.toInstant(utc)
        return Instant.fromEpochMilliseconds(kxInstant.toEpochMilliseconds())
    }

    // ---- Parsing ----

    @Test
    fun `parses star expression`() {
        // "* * * * *" should match every minute
        val cron = CronExpression("* * * * *")
        val from = instant(2026, 1, 1, 12, 0)
        val next = cron.nextAfter(from, utc)
        assertNotNull(next)
        // Next minute is 12:01
        assertEquals(instant(2026, 1, 1, 12, 1), next)
    }

    @Test
    fun `parses step expression`() {
        // Every 5 minutes
        val cron = CronExpression("*/5 * * * *")
        val next = cron.nextAfter(instant(2026, 1, 1, 12, 1), utc)
        assertEquals(instant(2026, 1, 1, 12, 5), next)
    }

    @Test
    fun `parses range expression`() {
        // 9-17 hours, daily 0:00
        val cron = CronExpression("0 9-17 * * *")
        // From 8:30, next is 9:00
        assertEquals(instant(2026, 1, 1, 9, 0), cron.nextAfter(instant(2026, 1, 1, 8, 30), utc))
        // From 17:30, next is 9:00 next day
        assertEquals(instant(2026, 1, 2, 9, 0), cron.nextAfter(instant(2026, 1, 1, 17, 30), utc))
    }

    @Test
    fun `parses comma list expression`() {
        // 0, 15, 30, 45 of every hour
        val cron = CronExpression("0,15,30,45 * * * *")
        assertEquals(instant(2026, 1, 1, 12, 15), cron.nextAfter(instant(2026, 1, 1, 12, 7), utc))
        assertEquals(instant(2026, 1, 1, 12, 45), cron.nextAfter(instant(2026, 1, 1, 12, 31), utc))
        assertEquals(instant(2026, 1, 1, 13, 0), cron.nextAfter(instant(2026, 1, 1, 12, 46), utc))
    }

    @Test
    fun `parses specific value expression`() {
        // 14:30 daily
        val cron = CronExpression("30 14 * * *")
        assertEquals(instant(2026, 1, 1, 14, 30), cron.nextAfter(instant(2026, 1, 1, 0, 0), utc))
    }

    @Test
    fun `rejects expression with wrong field count`() {
        assertFailsWith<IllegalArgumentException> { CronExpression("* * *") }
        assertFailsWith<IllegalArgumentException> { CronExpression("* * * * * *") }
        assertFailsWith<IllegalArgumentException> { CronExpression("") }
    }

    @Test
    fun `rejects malformed step`() {
        assertFailsWith<IllegalArgumentException> { CronExpression("*/abc * * * *") }
    }

    @Test
    fun `rejects non-numeric values`() {
        assertFailsWith<IllegalArgumentException> { CronExpression("foo * * * *") }
    }

    @Test
    fun `accepts whitespace variations`() {
        // Multiple spaces between fields are fine
        val cron = CronExpression("0   9   *   *   *")
        assertEquals(instant(2026, 1, 1, 9, 0), cron.nextAfter(instant(2026, 1, 1, 8, 0), utc))
    }

    // ---- nextAfter ----

    @Test
    fun `nextAfter daily 9am`() {
        val cron = CronExpression("0 9 * * *")
        // Before 9am same day → 9am same day
        assertEquals(instant(2026, 3, 15, 9, 0), cron.nextAfter(instant(2026, 3, 15, 8, 30), utc))
        // After 9am → 9am next day
        assertEquals(instant(2026, 3, 16, 9, 0), cron.nextAfter(instant(2026, 3, 15, 10, 0), utc))
        // Exactly at 9am → next day's 9am (strictly after)
        assertEquals(instant(2026, 3, 16, 9, 0), cron.nextAfter(instant(2026, 3, 15, 9, 0), utc))
    }

    @Test
    fun `nextAfter top of every hour`() {
        val cron = CronExpression("0 * * * *")
        assertEquals(instant(2026, 1, 1, 11, 0), cron.nextAfter(instant(2026, 1, 1, 10, 30), utc))
        assertEquals(instant(2026, 1, 1, 12, 0), cron.nextAfter(instant(2026, 1, 1, 11, 0), utc))
    }

    @Test
    fun `nextAfter specific day of month`() {
        // Midnight on 15th of every month
        val cron = CronExpression("0 0 15 * *")
        // From Jan 1 → Jan 15
        assertEquals(instant(2026, 1, 15, 0, 0), cron.nextAfter(instant(2026, 1, 1, 0, 0), utc))
        // From Jan 16 → Feb 15
        assertEquals(instant(2026, 2, 15, 0, 0), cron.nextAfter(instant(2026, 1, 16, 0, 0), utc))
    }

    @Test
    fun `nextAfter specific day of week`() {
        // Midnight every Sunday (cron 0 = Sunday)
        val cron = CronExpression("0 0 * * 0")
        // 2026-01-01 is Thursday → next Sunday is 2026-01-04
        assertEquals(instant(2026, 1, 4, 0, 0), cron.nextAfter(instant(2026, 1, 1, 0, 0), utc))
    }

    @Test
    fun `nextAfter Monday morning`() {
        // 7:00 every Monday (cron 1 = Monday)
        val cron = CronExpression("0 7 * * 1")
        // From Sunday 2026-01-04 noon → Monday 2026-01-05 7:00
        assertEquals(instant(2026, 1, 5, 7, 0), cron.nextAfter(instant(2026, 1, 4, 12, 0), utc))
    }

    @Test
    fun `nextAfter crosses month boundary`() {
        // Top of every hour
        val cron = CronExpression("0 * * * *")
        assertEquals(instant(2026, 2, 1, 0, 0), cron.nextAfter(instant(2026, 1, 31, 23, 30), utc))
    }

    @Test
    fun `nextAfter crosses year boundary`() {
        // Daily midnight
        val cron = CronExpression("0 0 * * *")
        assertEquals(instant(2027, 1, 1, 0, 0), cron.nextAfter(instant(2026, 12, 31, 23, 0), utc))
    }

    @Test
    fun `nextAfter specific month and day`() {
        // Midnight Jan 1 — annual
        val cron = CronExpression("0 0 1 1 *")
        // From mid-year → next Jan 1
        assertEquals(instant(2027, 1, 1, 0, 0), cron.nextAfter(instant(2026, 7, 15, 12, 0), utc))
    }

    @Test
    fun `nextAfter handles leap year February 29`() {
        // 2028 is a leap year
        val cron = CronExpression("0 0 29 2 *")
        // From Jan 2027 → Feb 29 2028
        assertEquals(instant(2028, 2, 29, 0, 0), cron.nextAfter(instant(2027, 1, 1, 0, 0), utc))
    }

    @Test
    fun `nextAfter returns null when no match within search horizon`() {
        // Feb 30 doesn't exist; the parser accepts day 30 but it can never match in February.
        // Restricting to month=2 day=30 means no match in any year → null after exhausting search.
        val cron = CronExpression("0 0 30 2 *")
        assertNull(cron.nextAfter(instant(2026, 1, 1, 0, 0), utc))
    }

    @Test
    fun `nextAfter with star fields fires next minute`() {
        val cron = CronExpression("* * * * *")
        // From 23:59 → 00:00 next day
        assertEquals(instant(2026, 1, 2, 0, 0), cron.nextAfter(instant(2026, 1, 1, 23, 59), utc))
    }

    @Test
    fun `nextAfter every 30 minutes`() {
        val cron = CronExpression("*/30 * * * *")
        // From 10:01 → 10:30
        assertEquals(instant(2026, 1, 1, 10, 30), cron.nextAfter(instant(2026, 1, 1, 10, 1), utc))
        // From 10:30 → 11:00
        assertEquals(instant(2026, 1, 1, 11, 0), cron.nextAfter(instant(2026, 1, 1, 10, 30), utc))
    }

    @Test
    fun `nextAfter weekday range`() {
        // Top of 9am Mon-Fri (cron 1-5)
        val cron = CronExpression("0 9 * * 1-5")
        // 2026-01-03 is Saturday → next is Mon 2026-01-05 9:00
        assertEquals(instant(2026, 1, 5, 9, 0), cron.nextAfter(instant(2026, 1, 3, 0, 0), utc))
        // 2026-01-04 is Sunday → next is Mon 2026-01-05 9:00
        assertEquals(instant(2026, 1, 5, 9, 0), cron.nextAfter(instant(2026, 1, 4, 12, 0), utc))
    }
}
