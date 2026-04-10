package com.inspiredandroid.kai.ui.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnsiParserTest {

    private val defaultColor = Color(0xFF112233)

    private val esc = '\u001B'
    private val bel = '\u0007'

    // ---- Plain text passthrough ----

    @Test
    fun `plain text without escapes is returned unchanged`() {
        val result = parseAnsiToAnnotatedString("Hello, world!", defaultColor)
        assertEquals("Hello, world!", result.text)
    }

    @Test
    fun `plain text gets default color span`() {
        val result = parseAnsiToAnnotatedString("Hello", defaultColor)
        // Fast path wraps everything in a default-color span
        val style = result.spanStyles.firstOrNull()
        assertNotNull(style)
        assertEquals(defaultColor, style.item.color)
    }

    @Test
    fun `empty string returns empty AnnotatedString`() {
        val result = parseAnsiToAnnotatedString("", defaultColor)
        assertEquals("", result.text)
    }

    // ---- Basic SGR codes ----

    @Test
    fun `bold style is applied`() {
        val input = "$esc[1mBold$esc[0m"
        val result = parseAnsiToAnnotatedString(input, defaultColor)
        assertEquals("Bold", result.text)
        val boldSpan = result.spanStyles.firstOrNull { it.item.fontWeight == FontWeight.Bold }
        assertNotNull(boldSpan)
        assertEquals(0, boldSpan.start)
        assertEquals(4, boldSpan.end)
    }

    @Test
    fun `dim style applies light weight`() {
        val input = "$esc[2mdim$esc[0m"
        val result = parseAnsiToAnnotatedString(input, defaultColor)
        assertEquals("dim", result.text)
        val dimSpan = result.spanStyles.firstOrNull { it.item.fontWeight == FontWeight.Light }
        assertNotNull(dimSpan)
    }

    @Test
    fun `italic style is applied`() {
        val input = "$esc[3mitalic$esc[0m"
        val result = parseAnsiToAnnotatedString(input, defaultColor)
        assertEquals("italic", result.text)
        val italicSpan = result.spanStyles.firstOrNull { it.item.fontStyle == FontStyle.Italic }
        assertNotNull(italicSpan)
    }

    @Test
    fun `underline applies underline decoration`() {
        val input = "$esc[4munder$esc[0m"
        val result = parseAnsiToAnnotatedString(input, defaultColor)
        assertEquals("under", result.text)
        val span = result.spanStyles.firstOrNull { it.item.textDecoration == TextDecoration.Underline }
        assertNotNull(span)
    }

    @Test
    fun `strikethrough applies linethrough decoration`() {
        val input = "$esc[9mstrike$esc[0m"
        val result = parseAnsiToAnnotatedString(input, defaultColor)
        assertEquals("strike", result.text)
        val span = result.spanStyles.firstOrNull { it.item.textDecoration == TextDecoration.LineThrough }
        assertNotNull(span)
    }

    @Test
    fun `underline and strikethrough combine into a single decoration`() {
        val input = "$esc[4;9mboth$esc[0m"
        val result = parseAnsiToAnnotatedString(input, defaultColor)
        assertEquals("both", result.text)
        val span = result.spanStyles.firstOrNull {
            val deco = it.item.textDecoration
            deco != null && deco != TextDecoration.None &&
                deco.toString().contains("Underline") && deco.toString().contains("LineThrough")
        }
        assertNotNull(span)
    }

    // ---- Standard colors ----

    @Test
    fun `standard foreground red is applied`() {
        val input = "$esc[31mRed$esc[0m"
        val result = parseAnsiToAnnotatedString(input, defaultColor)
        assertEquals("Red", result.text)
        // ansiStandardColors[1] = 0xFFCC0000
        val redSpan = result.spanStyles.firstOrNull { it.item.color == Color(0xFFCC0000) }
        assertNotNull(redSpan)
    }

    @Test
    fun `standard background green is applied`() {
        val input = "$esc[42mGreen$esc[0m"
        val result = parseAnsiToAnnotatedString(input, defaultColor)
        assertEquals("Green", result.text)
        // Background = ansiStandardColors[2] = 0xFF00CC00
        val greenBg = result.spanStyles.firstOrNull { it.item.background == Color(0xFF00CC00) }
        assertNotNull(greenBg)
    }

    @Test
    fun `bright foreground colors use bright palette`() {
        val input = "$esc[91mBrightRed$esc[0m"
        val result = parseAnsiToAnnotatedString(input, defaultColor)
        assertEquals("BrightRed", result.text)
        // ansiBrightColors[1] = 0xFFFF4444
        val span = result.spanStyles.firstOrNull { it.item.color == Color(0xFFFF4444) }
        assertNotNull(span)
    }

    @Test
    fun `bright background colors use bright palette`() {
        val input = "$esc[101mtxt$esc[0m"
        val result = parseAnsiToAnnotatedString(input, defaultColor)
        assertEquals("txt", result.text)
        // ansiBrightColors[1] = 0xFFFF4444 (background)
        val span = result.spanStyles.firstOrNull { it.item.background == Color(0xFFFF4444) }
        assertNotNull(span)
    }

    @Test
    fun `default foreground reset clears fg color`() {
        // Set red, then reset fg
        val input = "$esc[31mRed$esc[39mDefault$esc[0m"
        val result = parseAnsiToAnnotatedString(input, defaultColor)
        assertEquals("RedDefault", result.text)
        // After ESC[39m, the "Default" segment should have the default fallback color
        val defaultSpan = result.spanStyles.firstOrNull { it.start >= 3 && it.item.color == defaultColor }
        assertNotNull(defaultSpan)
    }

    // ---- 256-color mode ----

    @Test
    fun `256 color foreground via 38 5 sets exact color`() {
        // index 196 is the bright red corner of the 6x6x6 cube: r=5, g=0, b=0 → (255, 0, 0)
        val input = "$esc[38;5;196mX$esc[0m"
        val result = parseAnsiToAnnotatedString(input, defaultColor)
        assertEquals("X", result.text)
        // adjusted=180, r=(180/36)*51=255, g=(180/6%6)*51=0, b=0
        val span = result.spanStyles.firstOrNull { it.item.color == Color(255, 0, 0) }
        assertNotNull(span)
    }

    @Test
    fun `256 color foreground in standard range maps to standard palette`() {
        // index 0..7 should map to ansiStandardColors
        val input = "$esc[38;5;1mX$esc[0m"
        val result = parseAnsiToAnnotatedString(input, defaultColor)
        val span = result.spanStyles.firstOrNull { it.item.color == Color(0xFFCC0000) }
        assertNotNull(span)
    }

    @Test
    fun `256 color grayscale range maps to gray colors`() {
        // index 232 → gray = (232-232)*10 + 8 = 8
        val input = "$esc[38;5;232mX$esc[0m"
        val result = parseAnsiToAnnotatedString(input, defaultColor)
        val span = result.spanStyles.firstOrNull { it.item.color == Color(8, 8, 8) }
        assertNotNull(span)
    }

    @Test
    fun `256 color background via 48 5 sets background`() {
        val input = "$esc[48;5;196mX$esc[0m"
        val result = parseAnsiToAnnotatedString(input, defaultColor)
        val span = result.spanStyles.firstOrNull { it.item.background == Color(255, 0, 0) }
        assertNotNull(span)
    }

    // ---- Chained codes and state persistence ----

    @Test
    fun `chained codes apply both styles`() {
        // Bold red
        val input = "$esc[1;31mBoldRed$esc[0m"
        val result = parseAnsiToAnnotatedString(input, defaultColor)
        assertEquals("BoldRed", result.text)
        val span = result.spanStyles.firstOrNull {
            it.item.fontWeight == FontWeight.Bold && it.item.color == Color(0xFFCC0000)
        }
        assertNotNull(span)
    }

    @Test
    fun `style persists across multiple text runs until reset`() {
        // Bold across two pieces of text separated by another bold-no-op SGR
        val input = "$esc[1mAB$esc[31mCD$esc[0m"
        val result = parseAnsiToAnnotatedString(input, defaultColor)
        assertEquals("ABCD", result.text)
        // "AB" should be bold (no color)
        val abSpan = result.spanStyles.firstOrNull { it.start == 0 && it.end == 2 }
        assertNotNull(abSpan)
        assertEquals(FontWeight.Bold, abSpan.item.fontWeight)
        // "CD" should be bold AND red
        val cdSpan = result.spanStyles.firstOrNull { it.start == 2 && it.end == 4 }
        assertNotNull(cdSpan)
        assertEquals(FontWeight.Bold, cdSpan.item.fontWeight)
        assertEquals(Color(0xFFCC0000), cdSpan.item.color)
    }

    @Test
    fun `code 22 turns off bold`() {
        val input = "$esc[1mBold$esc[22mNormal$esc[0m"
        val result = parseAnsiToAnnotatedString(input, defaultColor)
        assertEquals("BoldNormal", result.text)
        val normalSpan = result.spanStyles.firstOrNull { it.start == 4 && it.end == 10 }
        assertNotNull(normalSpan)
        // After code 22 the weight should not be bold
        assertTrue(normalSpan.item.fontWeight != FontWeight.Bold)
    }

    @Test
    fun `code 23 turns off italic`() {
        val input = "$esc[3mItalic$esc[23mNormal$esc[0m"
        val result = parseAnsiToAnnotatedString(input, defaultColor)
        assertEquals("ItalicNormal", result.text)
        val normalSpan = result.spanStyles.firstOrNull { it.start == 6 && it.end == 12 }
        assertNotNull(normalSpan)
        assertTrue(normalSpan.item.fontStyle != FontStyle.Italic)
    }

    @Test
    fun `reset clears all styles`() {
        val input = "$esc[1;31;4mAll$esc[0mPlain"
        val result = parseAnsiToAnnotatedString(input, defaultColor)
        assertEquals("AllPlain", result.text)
        val plainSpan = result.spanStyles.firstOrNull { it.start == 3 && it.end == 8 }
        assertNotNull(plainSpan)
        assertTrue(plainSpan.item.fontWeight != FontWeight.Bold)
        assertTrue(plainSpan.item.textDecoration == null || plainSpan.item.textDecoration == TextDecoration.None)
        // Color should fall back to default
        assertEquals(defaultColor, plainSpan.item.color)
    }

    // ---- Non-SGR sequences ----

    @Test
    fun `non-SGR CSI sequences are stripped`() {
        // Cursor up (ESC[A), erase line (ESC[2K) — should be removed but text preserved
        val input = "Before$esc[2KAfter"
        val result = parseAnsiToAnnotatedString(input, defaultColor)
        assertEquals("BeforeAfter", result.text)
    }

    @Test
    fun `cursor movement CSI is stripped`() {
        val input = "X$esc[10;20HY"
        val result = parseAnsiToAnnotatedString(input, defaultColor)
        assertEquals("XY", result.text)
    }

    @Test
    fun `OSC sequence terminated with BEL is stripped`() {
        val input = "Pre$esc]0;Window Title$bel" + "Post"
        val result = parseAnsiToAnnotatedString(input, defaultColor)
        assertEquals("PrePost", result.text)
    }

    @Test
    fun `OSC sequence terminated with ESC backslash is stripped`() {
        val input = "Pre$esc]0;Title${esc}\\" + "Post"
        val result = parseAnsiToAnnotatedString(input, defaultColor)
        assertEquals("PrePost", result.text)
    }

    // ---- Malformed / edge cases ----

    @Test
    fun `unknown escape after ESC is consumed`() {
        // ESC followed by neither [ nor ] — both bytes are skipped
        val input = "A${esc}xB"
        val result = parseAnsiToAnnotatedString(input, defaultColor)
        assertEquals("AB", result.text)
    }

    @Test
    fun `incomplete CSI at end of string does not crash`() {
        // ESC[ with no params or final byte
        val input = "Text$esc["
        val result = parseAnsiToAnnotatedString(input, defaultColor)
        assertEquals("Text", result.text)
    }

    @Test
    fun `unknown SGR codes are ignored`() {
        // 255 is not a defined SGR code, should pass through without crashing
        val input = "$esc[255mText$esc[0m"
        val result = parseAnsiToAnnotatedString(input, defaultColor)
        assertEquals("Text", result.text)
    }

    @Test
    fun `empty SGR resets state`() {
        // ESC[m is equivalent to ESC[0m
        val input = "$esc[1mBold$esc[mNormal"
        val result = parseAnsiToAnnotatedString(input, defaultColor)
        assertEquals("BoldNormal", result.text)
        val normalSpan = result.spanStyles.firstOrNull { it.start == 4 && it.end == 10 }
        assertNotNull(normalSpan)
        assertTrue(normalSpan.item.fontWeight != FontWeight.Bold)
    }

    @Test
    fun `multiple text runs include each segment`() {
        val input = "$esc[31mRed$esc[32mGreen$esc[34mBlue$esc[0m"
        val result = parseAnsiToAnnotatedString(input, defaultColor)
        assertEquals("RedGreenBlue", result.text)
        // Three colored runs at 0..3, 3..8, 8..12
        assertContains(result.spanStyles.map { it.start to it.end }, 0 to 3)
        assertContains(result.spanStyles.map { it.start to it.end }, 3 to 8)
        assertContains(result.spanStyles.map { it.start to it.end }, 8 to 12)
    }
}
