package com.inspiredandroid.kai.build.terminal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TerminalMouseEncoderTest {

    private val sgr = TerminalMouseState(
        tracking = TerminalMouseTracking.Click,
        encoding = TerminalMouseEncoding.Sgr,
    )
    private val x10 = TerminalMouseState(
        tracking = TerminalMouseTracking.Click,
        encoding = TerminalMouseEncoding.X10,
    )

    @Test
    fun sgrClickSendsPressThenRelease() {
        // Cell (11, 4) is column 12, row 5 on the wire.
        assertEquals(
            "\u001b[<0;12;5M\u001b[<0;12;5m",
            TerminalMouseEncoder.click(col = 11, row = 4, state = sgr),
        )
    }

    @Test
    fun sgrWheelHasNoRelease() {
        assertEquals("\u001b[<64;1;1M", TerminalMouseEncoder.wheel(up = true, col = 0, row = 0, state = sgr))
        assertEquals("\u001b[<65;1;1M", TerminalMouseEncoder.wheel(up = false, col = 0, row = 0, state = sgr))
    }

    @Test
    fun sgrHandlesCoordinatesBeyondX10Range() {
        assertEquals(
            "\u001b[<0;201;101M\u001b[<0;201;101m",
            TerminalMouseEncoder.click(col = 200, row = 100, state = sgr),
        )
    }

    @Test
    fun x10ClickOffsetsBy32() {
        // button 0 -> ' ', col 0 -> '!', row 0 -> '!'; release is button 3 -> '#'.
        assertEquals(
            "\u001b[M !!\u001b[M#!!",
            TerminalMouseEncoder.click(col = 0, row = 0, state = x10),
        )
    }

    @Test
    fun x10RefusesCoordinatesItCannotSpell() {
        // Column 95 would need byte 128, which UTF-8 would widen to two bytes.
        assertNull(TerminalMouseEncoder.click(col = 95, row = 0, state = x10))
        assertNull(TerminalMouseEncoder.click(col = 0, row = 95, state = x10))
        // 94 is the last column whose coordinate byte still fits in one byte.
        assertNotNull(TerminalMouseEncoder.click(col = 94, row = 94, state = x10))
    }

    @Test
    fun reportsNothingWhileTrackingIsOff() {
        val off = TerminalMouseState()
        assertNull(TerminalMouseEncoder.click(col = 1, row = 1, state = off))
        assertNull(TerminalMouseEncoder.wheel(up = true, col = 1, row = 1, state = off))
    }
}
