package com.inspiredandroid.kai.build.terminal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TerminalScreenTest {

    @Test
    fun writesPlainText() {
        val screen = TerminalScreen(columns = 20, rows = 5)
        screen.writeText("hi")
        val snap = screen.snapshot()
        assertEquals('h', snap.cellAt(0, 0).char)
        assertEquals('i', snap.cellAt(1, 0).char)
        assertEquals(2, snap.cursorCol)
        assertEquals(0, snap.cursorRow)
    }

    @Test
    fun cupMovesCursor() {
        val screen = TerminalScreen(columns = 20, rows = 5)
        // ESC [ 3 ; 5 H  -> row 3, col 5 (1-based)
        screen.writeText("\u001b[3;5H*")
        val snap = screen.snapshot()
        assertEquals('*', snap.cellAt(4, 2).char)
        assertEquals(5, snap.cursorCol)
        assertEquals(2, snap.cursorRow)
    }

    @Test
    fun eraseInDisplayClears() {
        val screen = TerminalScreen(columns = 10, rows = 3)
        screen.writeText("abc")
        screen.writeText("\u001b[2J\u001b[H")
        val snap = screen.snapshot()
        assertEquals(' ', snap.cellAt(0, 0).char)
        assertEquals(0, snap.cursorCol)
        assertEquals(0, snap.cursorRow)
    }

    @Test
    fun sgrSetsBoldAndColor() {
        val screen = TerminalScreen(columns = 10, rows = 3)
        screen.writeText("\u001b[1;31mX\u001b[0m")
        val snap = screen.snapshot()
        assertEquals('X', snap.cellAt(0, 0).char)
        assertTrue(snap.cellAt(0, 0).bold)
        assertEquals(1, snap.cellAt(0, 0).fg) // red
    }

    @Test
    fun crAndLf() {
        val screen = TerminalScreen(columns = 10, rows = 4)
        screen.writeText("ab\r\ncd")
        val snap = screen.snapshot()
        assertEquals('a', snap.cellAt(0, 0).char)
        assertEquals('b', snap.cellAt(1, 0).char)
        assertEquals('c', snap.cellAt(0, 1).char)
        assertEquals('d', snap.cellAt(1, 1).char)
    }

    @Test
    fun altScreenEnterClears() {
        val screen = TerminalScreen(columns = 10, rows = 3)
        screen.writeText("keep")
        // ESC [ ? 1049 h  — enter alt screen (we clear + home)
        screen.writeText("\u001b[?1049hX")
        val snap = screen.snapshot()
        assertEquals('X', snap.cellAt(0, 0).char)
        assertEquals(' ', snap.cellAt(1, 0).char)
    }

    @Test
    fun osc8HyperlinkIsCaptured() {
        val screen = TerminalScreen(columns = 40, rows = 10)
        // OSC 8 ; ; https://example.com/login BEL + visible label + OSC 8 end
        screen.writeText(
            "\u001b]8;;https://example.com/login?x=1\u0007click here\u001b]8;;\u0007",
        )
        val snap = screen.snapshot()
        assertEquals(1, snap.hyperlinks.size)
        assertEquals("https://example.com/login?x=1", snap.hyperlinks[0])
        // Visible cells still show the label text
        assertEquals('c', snap.cellAt(0, 0).char)
    }

    @Test
    fun osc52ClipboardUrlIsCaptured() {
        val screen = TerminalScreen(columns = 40, rows = 10)
        // "https://auth.example/x" in standard base64
        val b64 = "aHR0cHM6Ly9hdXRoLmV4YW1wbGUveA=="
        screen.writeText("\u001b]52;c;$b64\u0007")
        val snap = screen.snapshot()
        assertEquals(1, snap.hyperlinks.size)
        assertEquals("https://auth.example/x", snap.hyperlinks[0])
    }

    @Test
    fun rawStreamUrlScan() {
        val screen = TerminalScreen(columns = 40, rows = 10)
        screen.noteTextUrls("go to https://x.ai/device?code=ABC and finish")
        assertEquals("https://x.ai/device?code=ABC", screen.snapshot().hyperlinks[0])
    }

    @Test
    fun clearHyperlinksLeavesCellsIntact() {
        val screen = TerminalScreen(columns = 20, rows = 5)
        screen.writeText("hello")
        screen.noteHyperlink("https://example.com/a")
        assertEquals(1, screen.snapshot().hyperlinks.size)
        screen.clearHyperlinks()
        val snap = screen.snapshot()
        assertTrue(snap.hyperlinks.isEmpty())
        assertEquals('h', snap.cellAt(0, 0).char)
    }

    @Test
    fun oauthVariantsCollapseToOnePath() {
        val screen = TerminalScreen(columns = 40, rows = 5)
        screen.noteHyperlink(
            "https://claude.com/cai/oauth/authorize?code=true&redirect_uri=http://localhost:1234",
        )
        screen.noteHyperlink(
            "https://claude.com/cai/oauth/authorize?code=true&redirect_uri=https://platform.claude.com/cb",
        )
        // Shorter stump must not replace the full URL.
        screen.noteHyperlink("https://claude.com/cai/oauth/authorize?code=true")
        val snap = screen.snapshot()
        assertEquals(1, snap.hyperlinks.size)
        assertTrue(snap.hyperlinks[0].contains("platform.claude.com"))
    }

    @Test
    fun hyperlinkCapKeepsMostRecentPaths() {
        val screen = TerminalScreen(columns = 40, rows = 5)
        screen.noteHyperlink("https://a.example/one")
        screen.noteHyperlink("https://a.example/two")
        screen.noteHyperlink("https://a.example/three")
        screen.noteHyperlink("https://a.example/four")
        val snap = screen.snapshot()
        assertEquals(3, snap.hyperlinks.size)
        assertEquals("https://a.example/two", snap.hyperlinks[0])
        assertEquals("https://a.example/four", snap.hyperlinks[2])
    }

    @Test
    fun resizePreservesOverlap() {
        // resize() clamps to MIN/MAX; stay within that range.
        val screen = TerminalScreen(columns = 40, rows = 20)
        screen.writeText("hello")
        screen.resize(30, 16)
        val snap = screen.snapshot()
        assertEquals(30, snap.columns)
        assertEquals(16, snap.rows)
        assertEquals('h', snap.cellAt(0, 0).char)
        assertEquals('e', snap.cellAt(1, 0).char)
        assertEquals('o', snap.cellAt(4, 0).char)
    }
}
