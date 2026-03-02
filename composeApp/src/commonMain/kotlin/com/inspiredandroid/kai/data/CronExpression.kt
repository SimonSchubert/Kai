package com.inspiredandroid.kai.data

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime

/**
 * Minimal cron parser for 5-field expressions: minute hour day-of-month month day-of-week.
 * Supports: star, star/n (step), specific values, comma-separated lists, ranges.
 */
@OptIn(ExperimentalTime::class)
class CronExpression(expression: String) {

    private val minutes: Set<Int>
    private val hours: Set<Int>
    private val daysOfMonth: Set<Int>
    private val months: Set<Int>
    private val daysOfWeek: Set<Int> // 0=Sunday .. 6=Saturday (cron standard)

    init {
        val parts = expression.trim().split("\\s+".toRegex())
        require(parts.size == 5) { "Cron expression must have 5 fields, got ${parts.size}: $expression" }
        minutes = parseField(parts[0], 0, 59)
        hours = parseField(parts[1], 0, 23)
        daysOfMonth = parseField(parts[2], 1, 31)
        months = parseField(parts[3], 1, 12)
        daysOfWeek = parseField(parts[4], 0, 6)
    }

    /**
     * Computes the next execution time strictly after [after].
     * Searches up to ~2 years ahead, returns null if no match found.
     */
    fun nextAfter(after: kotlin.time.Instant, timeZone: TimeZone = TimeZone.currentSystemDefault()): kotlin.time.Instant? {
        val afterKx = kotlinx.datetime.Instant.fromEpochMilliseconds(after.toEpochMilliseconds())
        var dt = afterKx.toLocalDateTime(timeZone)
        // Start from the next minute
        @Suppress("DEPRECATION")
        dt = LocalDateTime(dt.year, dt.month, dt.dayOfMonth, dt.hour, dt.minute, 0, 0)
        dt = advanceMinute(dt, timeZone)

        // Search limit: ~2 years of minutes (enough for any cron)
        val maxIterations = 525960 // 365 * 2 * 24 * 60
        var iterations = 0

        while (iterations < maxIterations) {
            iterations++

            @Suppress("DEPRECATION")
            if (dt.monthNumber !in months) {
                dt = nextMonth(dt) ?: return null
                continue
            }

            @Suppress("DEPRECATION")
            if (dt.dayOfMonth !in daysOfMonth || toCronDayOfWeek(dt) !in daysOfWeek) {
                dt = nextDay(dt, timeZone)
                continue
            }

            if (dt.hour !in hours) {
                dt = nextHour(dt, timeZone)
                continue
            }

            if (dt.minute !in minutes) {
                dt = advanceMinute(dt, timeZone)
                continue
            }

            return kotlin.time.Instant.fromEpochMilliseconds(dt.toInstant(timeZone).toEpochMilliseconds())
        }
        return null
    }

    private fun advanceMinute(dt: LocalDateTime, tz: TimeZone): LocalDateTime {
        val instant = dt.toInstant(tz)
        val next = kotlinx.datetime.Instant.fromEpochMilliseconds(instant.toEpochMilliseconds() + 60_000L)
        return next.toLocalDateTime(tz)
    }

    @Suppress("DEPRECATION")
    private fun nextHour(dt: LocalDateTime, tz: TimeZone): LocalDateTime = LocalDateTime(dt.year, dt.month, dt.dayOfMonth, dt.hour, 0, 0, 0)
        .let {
            val instant = it.toInstant(tz)
            kotlinx.datetime.Instant.fromEpochMilliseconds(instant.toEpochMilliseconds() + 3_600_000L)
                .toLocalDateTime(tz)
        }

    @Suppress("DEPRECATION")
    private fun nextDay(dt: LocalDateTime, tz: TimeZone): LocalDateTime = LocalDateTime(dt.year, dt.month, dt.dayOfMonth, 0, 0, 0, 0)
        .let {
            val instant = it.toInstant(tz)
            kotlinx.datetime.Instant.fromEpochMilliseconds(instant.toEpochMilliseconds() + 86_400_000L)
                .toLocalDateTime(tz)
        }

    @Suppress("DEPRECATION")
    private fun nextMonth(dt: LocalDateTime): LocalDateTime? {
        var year = dt.year
        var month = dt.monthNumber + 1
        if (month > 12) {
            month = 1
            year++
        }
        if (year > dt.year + 2) return null
        return LocalDateTime(year, month, 1, 0, 0, 0, 0)
    }

    /** Convert kotlinx.datetime DayOfWeek (MONDAY=1..SUNDAY=7) to cron convention (0=Sunday..6=Saturday) */
    private fun toCronDayOfWeek(dt: LocalDateTime): Int = when (dt.dayOfWeek) {
        kotlinx.datetime.DayOfWeek.SUNDAY -> 0
        kotlinx.datetime.DayOfWeek.MONDAY -> 1
        kotlinx.datetime.DayOfWeek.TUESDAY -> 2
        kotlinx.datetime.DayOfWeek.WEDNESDAY -> 3
        kotlinx.datetime.DayOfWeek.THURSDAY -> 4
        kotlinx.datetime.DayOfWeek.FRIDAY -> 5
        kotlinx.datetime.DayOfWeek.SATURDAY -> 6
    }

    companion object {
        fun parseField(field: String, min: Int, max: Int): Set<Int> {
            val result = mutableSetOf<Int>()
            for (part in field.split(",")) {
                when {
                    part == "*" -> result.addAll(min..max)

                    part.startsWith("*/") -> {
                        val step = part.substringAfter("*/").toIntOrNull()
                            ?: throw IllegalArgumentException("Invalid step in cron field: $part")
                        var i = min
                        while (i <= max) {
                            result.add(i)
                            i += step
                        }
                    }

                    part.contains("-") -> {
                        val (start, end) = part.split("-").map { it.toInt() }
                        result.addAll(start.coerceIn(min, max)..end.coerceIn(min, max))
                    }

                    else -> {
                        val value = part.toIntOrNull()
                            ?: throw IllegalArgumentException("Invalid cron field value: $part")
                        if (value in min..max) result.add(value)
                    }
                }
            }
            return result
        }
    }
}
