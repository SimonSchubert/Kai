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
