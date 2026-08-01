package com.inspiredandroid.kai.build.terminal

import kotlinx.collections.immutable.toImmutableList

/**
 * Mutable character-cell screen buffer + cursor. Fed by [VtParser]; snapshotted
 * for Compose.
 *
 * Call [resize] when the host viewport changes; TUI apps react via SIGWINCH after
 * the PTY winsize is updated separately.
 */
class TerminalScreen(
    columns: Int = DEFAULT_COLUMNS,
    rows: Int = DEFAULT_ROWS,
) {
    var columns: Int = columns.coerceAtLeast(1)
        private set
    var rows: Int = rows.coerceAtLeast(1)
        private set

    private var chars = CharArray(this.columns * this.rows) { ' ' }
    private var fg = ByteArray(this.columns * this.rows) { 7 }
    private var bg = ByteArray(this.columns * this.rows) { 0 }
    private var bold = BooleanArray(this.columns * this.rows)

    var cursorCol: Int = 0
        private set
    var cursorRow: Int = 0
        private set
    var cursorVisible: Boolean = true
        private set

    /**
     * DECCKM (private mode 1). While set, apps expect arrow keys as `ESC O A`
     * rather than `ESC [ A`; sending the wrong form prints junk instead of moving.
     */
    var applicationCursorKeys: Boolean = false
        private set

    var currentFg: Int = 7
        private set
    var currentBg: Int = 0
        private set
    var currentBold: Boolean = false
        private set

    /**
     * OSC 8 / stream / open-url URIs. Keyed by scheme+host+path so Claude’s
     * many OAuth authorize variants (different redirect_uri) collapse to one
     * entry; value is the best (usually longest) full URL for that path.
     */
    private val hyperlinks = LinkedHashMap<String, String>()

    private var revision: Long = 0L
    private val parser = VtParser(this)

    fun write(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset) {
        if (length <= 0) return
        val text = bytes.decodeToString(offset, offset + length, throwOnInvalidSequence = false)
        if (text.isNotEmpty()) {
            parser.feed(text)
            revision++
        }
    }

    fun writeText(text: String) {
        if (text.isEmpty()) return
        parser.feed(text)
        revision++
    }

    /**
     * Change geometry. Overlapping cells are copied; the rest is blank.
     * Cursor is clamped. TUI apps typically repaint entirely after SIGWINCH.
     */
    fun resize(newColumns: Int, newRows: Int) {
        val nc = newColumns.coerceIn(MIN_COLUMNS, MAX_COLUMNS)
        val nr = newRows.coerceIn(MIN_ROWS, MAX_ROWS)
        if (nc == columns && nr == rows) return

        val newChars = CharArray(nc * nr) { ' ' }
        val newFg = ByteArray(nc * nr) { 7 }
        val newBg = ByteArray(nc * nr) { 0 }
        val newBold = BooleanArray(nc * nr)

        val copyCols = minOf(columns, nc)
        val copyRows = minOf(rows, nr)
        for (row in 0 until copyRows) {
            for (col in 0 until copyCols) {
                val oi = row * columns + col
                val ni = row * nc + col
                newChars[ni] = chars[oi]
                newFg[ni] = fg[oi]
                newBg[ni] = bg[oi]
                newBold[ni] = bold[oi]
            }
        }

        columns = nc
        rows = nr
        chars = newChars
        fg = newFg
        bg = newBg
        bold = newBold
        cursorCol = cursorCol.coerceIn(0, columns - 1)
        cursorRow = cursorRow.coerceIn(0, rows - 1)
        revision++
    }

    fun clear() {
        for (i in chars.indices) {
            chars[i] = ' '
            fg[i] = 7
            bg[i] = 0
            bold[i] = false
        }
        cursorCol = 0
        cursorRow = 0
        currentFg = 7
        currentBg = 0
        currentBold = false
        cursorVisible = true
        // Closest thing we have to a full reset: a fresh shell has DECCKM off,
        // and stale application-mode arrows would break the plain prompt.
        applicationCursorKeys = false
        hyperlinks.clear()
        revision++
    }

    fun snapshot(): TerminalSnapshot {
        val cells = ArrayList<TerminalCell>(columns * rows)
        for (i in chars.indices) {
            cells.add(
                TerminalCell(
                    char = chars[i],
                    fg = fg[i].toInt() and 0xFF,
                    bg = bg[i].toInt() and 0xFF,
                    bold = bold[i],
                ),
            )
        }
        return TerminalSnapshot(
            columns = columns,
            rows = rows,
            cells = cells,
            cursorCol = cursorCol.coerceIn(0, (columns - 1).coerceAtLeast(0)),
            cursorRow = cursorRow.coerceIn(0, (rows - 1).coerceAtLeast(0)),
            cursorVisible = cursorVisible,
            applicationCursorKeys = applicationCursorKeys,
            revision = revision,
            // Encounter order; only the best URL per path is kept.
            hyperlinks = hyperlinks.values.toImmutableList(),
        )
    }

    // --- called from VtParser ------------------------------------------------

    /**
     * Record a hyperlink from OSC 8 / OSC 52 / raw stream. Empty or non-http
     * values are ignored. Same origin+path collapses to one entry (latest /
     * longest wins) so OAuth flows that re-print the authorize URL with
     * different query params do not flood the bar.
     *
     * Returns true when the displayed set changed.
     */
    internal fun noteHyperlink(uri: String): Boolean {
        val trimmed = uri.trim().trimEnd('\u0000', '\r', '\n', ' ')
        if (trimmed.isEmpty()) return false
        // Clipboard may include surrounding text; pull out first URL if needed.
        var url = URL_REGEX.find(trimmed)?.value ?: trimmed
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false
        // Drop trailing punctuation TUIs sometimes leave attached.
        url = url.trimEnd('.', ',', ';', ':', ')', ']', '"', '\'')
        val key = hyperlinkKey(url)
        if (key.isEmpty()) return false
        val existing = hyperlinks[key]
        // Prefer the longer form: full OAuth query beats a line-wrapped stump.
        if (existing != null && existing.length >= url.length) return false
        // Re-insert so the path stays "most recently improved" in encounter order.
        if (existing != null) hyperlinks.remove(key)
        hyperlinks[key] = url
        while (hyperlinks.size > MAX_HYPERLINKS) {
            val oldest = hyperlinks.keys.first()
            hyperlinks.remove(oldest)
        }
        revision++
        return true
    }

    /** Drop captured hyperlinks without wiping the cell grid (used after display TTL). */
    fun clearHyperlinks() {
        if (hyperlinks.isEmpty()) return
        hyperlinks.clear()
        revision++
    }

    /**
     * Scan arbitrary text (raw PTY chunk, OSC 52 payload, etc.) for http(s) URLs.
     * Returns true when at least one new link was recorded.
     */
    internal fun noteTextUrls(text: String): Boolean {
        var added = false
        URL_REGEX.findAll(text).forEach { if (noteHyperlink(it.value)) added = true }
        return added
    }

    private companion object {
        /** Enough for one auth URL + a docs link; keeps Claude setup from flooding. */
        const val MAX_HYPERLINKS = 3

        // Stop at whitespace, quotes, ANSI ESC, or common terminal delimiters.
        val URL_REGEX = Regex("""https?://[^\s\u001b"'<>)\]]+""")

        /** scheme://host/path — query and fragment ignored so OAuth variants collapse. */
        fun hyperlinkKey(url: String): String {
            val noFrag = url.substringBefore('#')
            val base = noFrag.substringBefore('?')
            return base.trimEnd('/')
        }
    }

    internal fun putChar(ch: Char) {
        when (ch) {
            '\n' -> {
                lineFeed()
                return
            }
            '\r' -> {
                cursorCol = 0
                return
            }
            '\b' -> {
                if (cursorCol > 0) cursorCol--
                return
            }
            '\t' -> {
                val next = ((cursorCol / 8) + 1) * 8
                cursorCol = next.coerceAtMost(columns - 1)
                return
            }
            '\u0007' -> return
        }
        if (ch.code < 32 && ch != '\u001b') return

        if (cursorCol >= columns) {
            cursorCol = 0
            lineFeed()
        }
        val i = index(cursorCol, cursorRow)
        chars[i] = ch
        fg[i] = currentFg.toByte()
        bg[i] = currentBg.toByte()
        bold[i] = currentBold
        cursorCol++
    }

    internal fun lineFeed() {
        if (cursorRow < rows - 1) {
            cursorRow++
        } else {
            scrollUp()
        }
        // xterm default: LF does not imply CR; many apps send CRLF. Keep col.
    }

    internal fun reverseIndex() {
        if (cursorRow > 0) {
            cursorRow--
        } else {
            scrollDown()
        }
    }

    internal fun setCursor(col: Int, row: Int) {
        cursorCol = col.coerceIn(0, columns - 1)
        cursorRow = row.coerceIn(0, rows - 1)
    }

    internal fun moveCursor(dCol: Int, dRow: Int) {
        setCursor(cursorCol + dCol, cursorRow + dRow)
    }

    internal fun setCursorVisible(visible: Boolean) {
        cursorVisible = visible
    }

    internal fun setApplicationCursorKeys(enable: Boolean) {
        applicationCursorKeys = enable
    }

    internal fun setSgr(params: List<Int>) {
        if (params.isEmpty()) {
            resetSgr()
            return
        }
        var i = 0
        while (i < params.size) {
            when (val p = params[i]) {
                0 -> resetSgr()
                1 -> currentBold = true
                22 -> currentBold = false
                39 -> currentFg = 7
                49 -> currentBg = 0
                in 30..37 -> currentFg = p - 30
                in 90..97 -> currentFg = p - 90 + 8
                in 40..47 -> currentBg = p - 40
                in 100..107 -> currentBg = p - 100 + 8
                38 -> {
                    if (i + 1 < params.size && params[i + 1] == 5 && i + 2 < params.size) {
                        currentFg = map256(params[i + 2])
                        i += 2
                    } else if (i + 1 < params.size && params[i + 1] == 2 && i + 4 < params.size) {
                        currentFg = rgbTo16(params[i + 2], params[i + 3], params[i + 4])
                        i += 4
                    }
                }
                48 -> {
                    if (i + 1 < params.size && params[i + 1] == 5 && i + 2 < params.size) {
                        currentBg = map256(params[i + 2])
                        i += 2
                    } else if (i + 1 < params.size && params[i + 1] == 2 && i + 4 < params.size) {
                        currentBg = rgbTo16(params[i + 2], params[i + 3], params[i + 4])
                        i += 4
                    }
                }
            }
            i++
        }
    }

    internal fun eraseInDisplay(mode: Int) {
        when (mode) {
            0 -> clearRange(index(cursorCol, cursorRow), columns * rows)
            1 -> clearRange(0, index(cursorCol, cursorRow) + 1)
            2, 3 -> clearRange(0, columns * rows)
        }
    }

    internal fun eraseInLine(mode: Int) {
        val rowStart = cursorRow * columns
        when (mode) {
            0 -> clearRange(rowStart + cursorCol, rowStart + columns)
            1 -> clearRange(rowStart, rowStart + cursorCol + 1)
            2 -> clearRange(rowStart, rowStart + columns)
        }
    }

    internal fun eraseChars(count: Int) {
        val n = count.coerceAtLeast(1)
        val start = index(cursorCol, cursorRow)
        val end = (start + n).coerceAtMost((cursorRow + 1) * columns)
        clearRange(start, end)
    }

    internal fun deleteChars(count: Int) {
        val n = count.coerceAtLeast(1)
        val rowStart = cursorRow * columns
        val rowEnd = rowStart + columns
        val from = rowStart + cursorCol
        val shiftEnd = (from + n).coerceAtMost(rowEnd)
        var dest = from
        var src = shiftEnd
        while (src < rowEnd) {
            chars[dest] = chars[src]
            fg[dest] = fg[src]
            bg[dest] = bg[src]
            bold[dest] = bold[src]
            dest++
            src++
        }
        clearRange(dest, rowEnd)
    }

    internal fun insertLines(count: Int) {
        val n = count.coerceAtLeast(1).coerceAtMost(rows - cursorRow)
        for (row in (rows - 1) downTo cursorRow + n) {
            copyRow(row - n, row)
        }
        for (row in cursorRow until cursorRow + n) {
            clearRow(row)
        }
    }

    internal fun deleteLines(count: Int) {
        val n = count.coerceAtLeast(1).coerceAtMost(rows - cursorRow)
        for (row in cursorRow until rows - n) {
            copyRow(row + n, row)
        }
        for (row in rows - n until rows) {
            clearRow(row)
        }
    }

    private fun resetSgr() {
        currentFg = 7
        currentBg = 0
        currentBold = false
    }

    private fun index(col: Int, row: Int) = row * columns + col

    private fun clearRange(start: Int, end: Int) {
        val s = start.coerceIn(0, chars.size)
        val e = end.coerceIn(0, chars.size)
        for (i in s until e) {
            chars[i] = ' '
            fg[i] = 7
            bg[i] = 0
            bold[i] = false
        }
    }

    private fun clearRow(row: Int) {
        clearRange(row * columns, (row + 1) * columns)
    }

    private fun copyRow(fromRow: Int, toRow: Int) {
        val from = fromRow * columns
        val to = toRow * columns
        for (c in 0 until columns) {
            chars[to + c] = chars[from + c]
            fg[to + c] = fg[from + c]
            bg[to + c] = bg[from + c]
            bold[to + c] = bold[from + c]
        }
    }

    private fun scrollUp() {
        for (row in 0 until rows - 1) {
            copyRow(row + 1, row)
        }
        clearRow(rows - 1)
    }

    private fun scrollDown() {
        for (row in rows - 1 downTo 1) {
            copyRow(row - 1, row)
        }
        clearRow(0)
    }

    private fun map256(n: Int): Int {
        if (n < 0) return 7
        if (n < 16) return n
        if (n < 232) {
            val v = n - 16
            val r = v / 36
            val g = (v / 6) % 6
            val b = v % 6
            return rgbTo16(r * 51, g * 51, b * 51)
        }
        val gray = (n - 232) * 10 + 8
        return rgbTo16(gray, gray, gray)
    }

    private fun rgbTo16(r: Int, g: Int, b: Int): Int {
        val bright = if (r + g + b > 320) 8 else 0
        val ri = if (r > 80) 1 else 0
        val gi = if (g > 80) 1 else 0
        val bi = if (b > 80) 1 else 0
        return bright + ri + gi * 2 + bi * 4
    }
}
