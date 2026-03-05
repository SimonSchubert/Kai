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

fun String.stripMarkdownForTts(): String = this
    // Remove code blocks (``` with optional language identifier)
    .replace(Regex("```[a-zA-Z]*\\n?"), "")
    // Remove images, keep alt text
    .replace(Regex("!\\[([^]]*)]\\([^)]*\\)"), "$1")
    // Remove links, keep text
    .replace(Regex("\\[([^]]*)]\\([^)]*\\)"), "$1")
    // Remove headers
    .replace(Regex("(?m)^#{1,6}\\s+"), "")
    // Remove horizontal rules
    .replace(Regex("(?m)^[-*_]{3,}\\s*$"), "")
    // Remove blockquotes
    .replace(Regex("(?m)^>\\s?"), "")
    // Replace list items: strip marker, ensure line ends with punctuation for TTS pause
    .replace(Regex("(?m)^[ \\t]*[-*+]\\s+(.+?)\\s*$"), "$1.")
    .replace(Regex("(?m)^[ \\t]*\\d+\\.\\s+(.+?)\\s*$"), "$1.")
    // Avoid double punctuation from above (e.g. "sentence.." or "question?.")
    .replace(Regex("(?m)([.!?:;])\\.$"), "$1")
    // Remove strikethrough
    .replace(Regex("~~(.*?)~~"), "$1")
    // Remove bold/italic (order matters: bold first, then italic)
    .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
    .replace(Regex("__(.+?)__"), "$1")
    .replace(Regex("\\*(.+?)\\*"), "$1")
    .replace(Regex("_(.+?)_"), "$1")
    // Remove inline code
    .replace(Regex("`([^`]+)`"), "$1")
    // Collapse multiple blank lines
    .replace(Regex("\\n{3,}"), "\n\n")
    .trim()
