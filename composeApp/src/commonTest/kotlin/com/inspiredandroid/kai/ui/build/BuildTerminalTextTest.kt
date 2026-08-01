package com.inspiredandroid.kai.ui.build

import com.inspiredandroid.kai.build.terminal.TerminalScreen
import com.inspiredandroid.kai.build.terminal.TerminalSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuildTerminalTextTest {

    /** What the grid used to emit: one span, one character, no run merging. */
    private fun flattenPerCell(snap: TerminalSnapshot): String = buildString {
        for (row in 0 until snap.rows) {
            for (col in 0 until snap.columns) {
                val c = snap.cellAt(col, row).char
                append(if (c == Char.MIN_VALUE) ' ' else c)
            }
            if (row < snap.rows - 1) append('\n')
        }
    }

    @Test
    fun textMatchesPerCellFlattening() {
        val screen = TerminalScreen(columns = 20, rows = 4)
        screen.writeText("plain \u001b[31mred\u001b[0m \u001b[1mbold\u001b[22m\r\nsecond line")
        val snap = screen.snapshot()

        assertEquals(flattenPerCell(snap), buildTerminalText(snap).text)
    }

    @Test
    fun collapsesUniformRowsIntoOneSpanEach() {
        val screen = TerminalScreen(columns = 80, rows = 24)
        val snap = screen.snapshot()

        // A span per cell would be 1920 here. A blank screen changes style only at
        // the cursor, so every row should cost a single span (plus the cursor split).
        val spans = buildTerminalText(snap).spanStyles.size
        assertTrue(spans <= snap.rows + 2, "expected about one span per row, got $spans")
    }

    @Test
    fun cursorCellGetsItsOwnInvertedSpan() {
        val screen = TerminalScreen(columns = 10, rows = 1)
        screen.writeText("abcdef")
        val snap = screen.snapshot()

        val cursor = buildTerminalText(snap).spanStyles
            .single { it.start == snap.cursorCol && it.end == snap.cursorCol + 1 }
        assertEquals(AnsiPalette[0], cursor.item.color)
        assertEquals(AnsiPalette[7], cursor.item.background)
    }

    @Test
    fun colorChangeSplitsTheRun() {
        val screen = TerminalScreen(columns = 6, rows = 1)
        screen.writeText("\u001b[31mab\u001b[32mcd")
        val snap = screen.snapshot()
        val spans = buildTerminalText(snap).spanStyles

        val red = spans.single { it.start == 0 }
        assertEquals(AnsiPalette[1], red.item.color)
        assertEquals(2, red.end)

        val green = spans.single { it.start == 2 }
        assertEquals(AnsiPalette[2], green.item.color)
        assertEquals(4, green.end)
    }
}
