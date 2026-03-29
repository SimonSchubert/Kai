package com.inspiredandroid.kai.ui.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

private val ansiStandardColors = listOf(
    Color(0xFF000000), // 0 Black
    Color(0xFFCC0000), // 1 Red
    Color(0xFF00CC00), // 2 Green
    Color(0xFFCCCC00), // 3 Yellow
    Color(0xFF5577FF), // 4 Blue
    Color(0xFFCC00CC), // 5 Magenta
    Color(0xFF00CCCC), // 6 Cyan
    Color(0xFFCCCCCC), // 7 White
)

private val ansiBrightColors = listOf(
    Color(0xFF666666), // 8 Bright Black
    Color(0xFFFF4444), // 9 Bright Red
    Color(0xFF44FF44), // 10 Bright Green
    Color(0xFFFFFF44), // 11 Bright Yellow
    Color(0xFF6CB6FF), // 12 Bright Blue
    Color(0xFFFF44FF), // 13 Bright Magenta
    Color(0xFF44FFFF), // 14 Bright Cyan
    Color(0xFFFFFFFF), // 15 Bright White
)

private fun ansi256Color(index: Int): Color? = when {
    index in 0..7 -> ansiStandardColors[index]

    index in 8..15 -> ansiBrightColors[index - 8]

    index in 16..231 -> {
        val adjusted = index - 16
        val r = (adjusted / 36) * 51
        val g = ((adjusted / 6) % 6) * 51
        val b = (adjusted % 6) * 51
        Color(r, g, b)
    }

    index in 232..255 -> {
        val gray = (index - 232) * 10 + 8
        Color(gray, gray, gray)
    }

    else -> null
}

private data class AnsiState(
    val fg: Color? = null,
    val bg: Color? = null,
    val bold: Boolean = false,
    val dim: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
)

private fun AnsiState.toSpanStyle(defaultColor: Color): SpanStyle = SpanStyle(
    color = fg ?: defaultColor,
    background = bg ?: Color.Unspecified,
    fontWeight = if (bold) {
        FontWeight.Bold
    } else if (dim) {
        FontWeight.Light
    } else {
        null
    },
    fontStyle = if (italic) FontStyle.Italic else null,
    textDecoration = when {
        underline && strikethrough -> TextDecoration.Underline + TextDecoration.LineThrough
        underline -> TextDecoration.Underline
        strikethrough -> TextDecoration.LineThrough
        else -> null
    },
)

fun parseAnsiToAnnotatedString(
    text: String,
    defaultColor: Color,
): AnnotatedString {
    if (!text.contains('\u001B')) {
        return AnnotatedString(text, SpanStyle(color = defaultColor))
    }

    val builder = AnnotatedString.Builder()
    var state = AnsiState()
    var i = 0
    val len = text.length
    val buffer = StringBuilder()

    fun flushBuffer() {
        if (buffer.isNotEmpty()) {
            builder.withStyle(state.toSpanStyle(defaultColor)) {
                append(buffer)
            }
            buffer.clear()
        }
    }

    while (i < len) {
        if (text[i] == '\u001B' && i + 1 < len) {
            val next = text[i + 1]
            when {
                // CSI sequence: ESC[...
                next == '[' -> {
                    flushBuffer()
                    i += 2
                    // Read parameters and final byte
                    val paramStart = i
                    while (i < len && text[i] in '\u0020'..'\u003F') i++
                    val params = if (i > paramStart) text.substring(paramStart, i) else ""
                    // Read final byte
                    if (i < len && text[i] in '\u0040'..'\u007E') {
                        val finalByte = text[i]
                        i++
                        if (finalByte == 'm') {
                            // SGR sequence - apply styling
                            state = applySgr(state, params)
                        }
                        // All other CSI sequences (cursor movement, erase, etc.) are stripped
                    }
                }

                // OSC sequence: ESC]...BEL or ESC]...ESC\
                next == ']' -> {
                    flushBuffer()
                    i += 2
                    while (i < len) {
                        if (text[i] == '\u0007') { // BEL
                            i++
                            break
                        }
                        if (text[i] == '\u001B' && i + 1 < len && text[i + 1] == '\\') {
                            i += 2
                            break
                        }
                        i++
                    }
                }

                else -> {
                    // Unknown escape - skip ESC and next char
                    i += 2
                }
            }
        } else {
            buffer.append(text[i])
            i++
        }
    }
    flushBuffer()
    return builder.toAnnotatedString()
}

private fun applySgr(current: AnsiState, params: String): AnsiState {
    if (params.isEmpty() || params == "0") return AnsiState()

    val codes = params.split(';').mapNotNull { it.toIntOrNull() }
    var state = current
    var idx = 0

    while (idx < codes.size) {
        when (val code = codes[idx]) {
            0 -> state = AnsiState()

            1 -> state = state.copy(bold = true, dim = false)

            2 -> state = state.copy(dim = true, bold = false)

            3 -> state = state.copy(italic = true)

            4 -> state = state.copy(underline = true)

            9 -> state = state.copy(strikethrough = true)

            22 -> state = state.copy(bold = false, dim = false)

            23 -> state = state.copy(italic = false)

            24 -> state = state.copy(underline = false)

            29 -> state = state.copy(strikethrough = false)

            in 30..37 -> state = state.copy(fg = ansiStandardColors[code - 30])

            38 -> {
                // Extended foreground color
                if (idx + 1 < codes.size && codes[idx + 1] == 5 && idx + 2 < codes.size) {
                    state = state.copy(fg = ansi256Color(codes[idx + 2]))
                    idx += 2
                }
            }

            39 -> state = state.copy(fg = null)

            // Default foreground
            in 40..47 -> state = state.copy(bg = ansiStandardColors[code - 40])

            48 -> {
                // Extended background color
                if (idx + 1 < codes.size && codes[idx + 1] == 5 && idx + 2 < codes.size) {
                    state = state.copy(bg = ansi256Color(codes[idx + 2]))
                    idx += 2
                }
            }

            49 -> state = state.copy(bg = null)

            // Default background
            in 90..97 -> state = state.copy(fg = ansiBrightColors[code - 90])

            in 100..107 -> state = state.copy(bg = ansiBrightColors[code - 100])
        }
        idx++
    }
    return state
}
