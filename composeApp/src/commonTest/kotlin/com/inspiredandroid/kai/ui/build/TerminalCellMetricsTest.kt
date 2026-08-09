package com.inspiredandroid.kai.ui.build

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The grid is only as honest as this arithmetic: a row too many is a shell line
 * painted half outside the viewport, and a column too many is every line of a
 * TUI wrapping against a width the PTY was never told about.
 */
class TerminalCellMetricsTest {

    // A 13sp line at 2.75x lands on a fractional pixel height — the case that
    // matters, since that is what truncating a line height used to round away.
    private val metrics = TerminalCellMetrics(advance = 10.9f, firstLine = 36, lineStep = 36)

    @Test
    fun rowsInNeverOverflowsTheViewport() {
        val height = 1000
        val rows = metrics.rowsIn(height)
        assertEquals(27, rows)
        // The last row has to be fully inside: 28 rows would want 1008px.
        assertEquals(true, metrics.firstLine + (rows - 1) * metrics.lineStep <= height)
    }

    @Test
    fun rowsInCountsExactFits() {
        assertEquals(1, metrics.rowsIn(36))
        assertEquals(1, metrics.rowsIn(71))
        assertEquals(2, metrics.rowsIn(72))
    }

    @Test
    fun rowsInIsZeroBelowOneLine() {
        assertEquals(0, metrics.rowsIn(35))
    }

    @Test
    fun cellLookupMatchesTheRowsAndColumnsReported() {
        // A touch anywhere inside the last cell that fits reports that cell, not
        // the one past it — mouse reports and the resize have to agree.
        val rows = metrics.rowsIn(1000)
        val lastRowTop = (metrics.firstLine + (rows - 2) * metrics.lineStep).toFloat()
        assertEquals(rows - 1, metrics.rowAt(lastRowTop))
        assertEquals(0, metrics.rowAt(0f))
        assertEquals(0, metrics.rowAt(metrics.firstLine - 1f))
        assertEquals(1, metrics.rowAt(metrics.firstLine.toFloat()))

        assertEquals(0, metrics.columnAt(0f))
        assertEquals(0, metrics.columnAt(10.8f))
        assertEquals(1, metrics.columnAt(10.9f))
    }
}
