package com.inspiredandroid.kai.data

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Minimal cron parser for 5-field expressions: minute hour day-of-month month day-of-week.
 * Supports: star, star/n (step), specific values, comma-separated lists, ranges.
 */
@OptIn(ExperimentalTime::class)
private val whitespaceRegex = Regex("\\s+")

class CronExpression(expression: String) {

    private val minutes: Set<Int>
    private val hours: Set<Int>
    private val daysOfMonth: Set<Int>
    private val months: Set<Int>
    private val daysOfWeek: Set<Int> // 0=Sunday .. 6=Saturday (cron standard)

    init {
        val parts = expression.trim().split(whitespaceRegex)
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
    fun nextAfter(after: Instant, timeZone: TimeZone = TimeZone.currentSystemDefault()): Instant? {
        val afterKx = Instant.fromEpochMilliseconds(after.toEpochMilliseconds())
        var dt = afterKx.toLocalDateTime(timeZone)
        // Start from the next minute
        dt = LocalDateTime(dt.date, LocalTime(dt.hour, dt.minute, 0, 0))
        dt = advanceMinute(dt, timeZone)

        // Search limit: ~2 years of minutes (enough for any cron)
        val maxIterations = 525960 // 365 * 2 * 24 * 60
        var iterations = 0

        while (iterations < maxIterations) {
            iterations++

            if (dt.date.month.ordinal + 1 !in months) {
                dt = nextMonth(dt) ?: return null
                continue
            }

            if (dt.date.day !in daysOfMonth || toCronDayOfWeek(dt) !in daysOfWeek) {
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

            return Instant.fromEpochMilliseconds(dt.toInstant(timeZone).toEpochMilliseconds())
        }
        return null
    }

    private fun advanceMinute(dt: LocalDateTime, tz: TimeZone): LocalDateTime {
        val instant = dt.toInstant(tz)
        val next = Instant.fromEpochMilliseconds(instant.toEpochMilliseconds() + 60_000L)
        return next.toLocalDateTime(tz)
    }

    private fun nextHour(dt: LocalDateTime, tz: TimeZone): LocalDateTime = LocalDateTime(dt.date, LocalTime(dt.hour, 0, 0, 0))
        .let {
            val instant = it.toInstant(tz)
            Instant.fromEpochMilliseconds(instant.toEpochMilliseconds() + 3_600_000L)
                .toLocalDateTime(tz)
        }

    private fun nextDay(dt: LocalDateTime, tz: TimeZone): LocalDateTime = LocalDateTime(dt.date, LocalTime(0, 0, 0, 0))
        .let {
            val instant = it.toInstant(tz)
            Instant.fromEpochMilliseconds(instant.toEpochMilliseconds() + 86_400_000L)
                .toLocalDateTime(tz)
        }

    private fun nextMonth(dt: LocalDateTime): LocalDateTime? {
        var year = dt.year
        var month = dt.date.month.ordinal + 2 // ordinal is 0-based, +1 for 1-based, +1 for next month
        if (month > 12) {
            month = 1
            year++
        }
        if (year > dt.year + 2) return null
        return LocalDateTime(LocalDate(year, month, 1), LocalTime(0, 0, 0, 0))
    }

    /** Convert kotlinx.datetime DayOfWeek (MONDAY=1..SUNDAY=7) to cron convention (0=Sunday..6=Saturday) */
    private fun toCronDayOfWeek(dt: LocalDateTime): Int = when (dt.dayOfWeek) {
        DayOfWeek.SUNDAY -> 0
        DayOfWeek.MONDAY -> 1
        DayOfWeek.TUESDAY -> 2
        DayOfWeek.WEDNESDAY -> 3
        DayOfWeek.THURSDAY -> 4
        DayOfWeek.FRIDAY -> 5
        DayOfWeek.SATURDAY -> 6
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
