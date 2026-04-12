package com.inspiredandroid.kai

import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents.Companion.Format
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char
import kotlin.time.Instant

private val humanReadableDateFormat = Format {
    day()
    char(' ')
    monthName(MonthNames.ENGLISH_FULL)
    char(' ')
    year()
}

fun Long.toHumanReadableDate(): String = Instant.fromEpochSeconds(this).format(humanReadableDateFormat)

/**
 * Convert a Unix epoch-seconds timestamp to an ISO-8601 date string (YYYY-MM-DD),
 * or null for zero/negative values. Some providers return `0` instead of omitting
 * the `created` field, which would otherwise surface as "Jan 1970".
 */
fun Long.toIsoDate(): String? = if (this <= 0L) null else Instant.fromEpochSeconds(this).toString().take(10)

fun formatContextWindow(tokens: Long): String = when {
    tokens >= 1_000_000 -> "${tokens / 1_000_000}M"
    tokens >= 1_000 -> "${tokens / 1_000}K"
    else -> "$tokens"
}

private val shortMonthNames = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

fun formatReleaseDate(iso: String): String {
    val firstDash = iso.indexOf('-')
    if (firstDash < 1) return iso
    val secondDash = iso.indexOf('-', firstDash + 1)
    val year = iso.substring(0, firstDash).toIntOrNull() ?: return iso
    val monthStr = if (secondDash > 0) iso.substring(firstDash + 1, secondDash) else iso.substring(firstDash + 1)
    val month = monthStr.toIntOrNull() ?: return iso
    if (month !in 1..12) return iso
    return "${shortMonthNames[month - 1]} $year"
}

private val ttsKaiUiBlockRegex = Regex("```kai-ui\\s*\\n?[\\s\\S]*?\\n?```")
private val ttsCodeBlockRegex = Regex("```[a-zA-Z]*\\n?")
private val ttsImageRegex = Regex("!\\[([^]]*)]\\([^)]*\\)")
private val ttsLinkRegex = Regex("\\[([^]]*)]\\([^)]*\\)")
private val ttsHeaderRegex = Regex("(?m)^#{1,6}\\s+")
private val ttsHorizontalRuleRegex = Regex("(?m)^[-*_]{3,}\\s*$")
private val ttsBlockquoteRegex = Regex("(?m)^>\\s?")
private val ttsUnorderedListRegex = Regex("(?m)^[ \\t]*[-*+]\\s+(.+?)\\s*$")
private val ttsOrderedListRegex = Regex("(?m)^[ \\t]*\\d+\\.\\s+(.+?)\\s*$")
private val ttsDoublePunctuationRegex = Regex("(?m)([.!?:;])\\.$")
private val ttsStrikethroughRegex = Regex("~~(.*?)~~")
private val ttsBoldAsterisksRegex = Regex("\\*\\*(.+?)\\*\\*")
private val ttsBoldUnderscoresRegex = Regex("__(.+?)__")
private val ttsItalicAsterisksRegex = Regex("\\*(.+?)\\*")
private val ttsItalicUnderscoresRegex = Regex("_(.+?)_")
private val ttsInlineCodeRegex = Regex("`([^`]+)`")
private val ttsMultipleBlankLinesRegex = Regex("\\n{3,}")

fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "${(bytes / 100_000_000).toDouble() / 10} GB"
    bytes >= 1_000_000 -> "${bytes / 1_000_000} MB"
    else -> "${bytes / 1_000} KB"
}

fun String.smartTruncate(maxLength: Int): String {
    if (length <= maxLength) return this
    val keep = (maxLength - 80) / 2
    return take(keep) +
        "\n[... ${length - 2 * keep} characters truncated ...]\n" +
        takeLast(keep)
}

fun String.stripMarkdownForTts(): String = this
    .replace(ttsKaiUiBlockRegex, "")
    .replace(ttsCodeBlockRegex, "")
    .replace(ttsImageRegex, "$1")
    .replace(ttsLinkRegex, "$1")
    .replace(ttsHeaderRegex, "")
    .replace(ttsHorizontalRuleRegex, "")
    .replace(ttsBlockquoteRegex, "")
    .replace(ttsUnorderedListRegex, "$1.")
    .replace(ttsOrderedListRegex, "$1.")
    .replace(ttsDoublePunctuationRegex, "$1")
    .replace(ttsStrikethroughRegex, "$1")
    .replace(ttsBoldAsterisksRegex, "$1")
    .replace(ttsBoldUnderscoresRegex, "$1")
    .replace(ttsItalicAsterisksRegex, "$1")
    .replace(ttsItalicUnderscoresRegex, "$1")
    .replace(ttsInlineCodeRegex, "$1")
    .replace(ttsMultipleBlankLinesRegex, "\n\n")
    .trim()
