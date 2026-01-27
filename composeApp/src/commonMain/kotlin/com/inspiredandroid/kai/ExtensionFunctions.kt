package com.inspiredandroid.kai

import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents.Companion.Format
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char
import kotlin.time.Instant

fun Long.toHumanReadableDate(): String = Instant.fromEpochSeconds(this).format(
    Format {
        day()
        char(' ')
        monthName(MonthNames.ENGLISH_FULL)
        char(' ')
        year()
    },
)
