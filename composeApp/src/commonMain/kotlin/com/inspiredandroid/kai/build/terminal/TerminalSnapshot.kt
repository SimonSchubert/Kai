package com.inspiredandroid.kai.build.terminal

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** One cell in the terminal grid. [fg]/[bg] are 0–15 ANSI palette indices. */
@Immutable
data class TerminalCell(
    val char: Char = ' ',
    val fg: Int = 7,
    val bg: Int = 0,
    val bold: Boolean = false,
)

/**
 * Immutable view of the screen for Compose. [revision] bumps on every change so
 * collectors recompose even when dimensions stay the same.
 *
 * [hyperlinks] are URIs extracted from OSC 8 sequences (xterm hyperlinks). TUIs
 * like Grok put login URLs there while showing only "click here" on the grid.
 */
@Immutable
data class TerminalSnapshot(
    val columns: Int,
    val rows: Int,
    /** Row-major cells, size = columns * rows. */
    val cells: List<TerminalCell>,
    val cursorCol: Int,
    val cursorRow: Int,
    val cursorVisible: Boolean,
    /** DECCKM state — decides how the key row encodes arrows. */
    val applicationCursorKeys: Boolean = false,
    val revision: Long,
    val hyperlinks: ImmutableList<String> = persistentListOf(),
) {
    fun cellAt(col: Int, row: Int): TerminalCell {
        if (col !in 0 until columns || row !in 0 until rows) return TerminalCell()
        return cells[row * columns + col]
    }

    companion object {
        fun blank(columns: Int = DEFAULT_COLUMNS, rows: Int = DEFAULT_ROWS) = TerminalSnapshot(
            columns = columns,
            rows = rows,
            cells = List(columns * rows) { TerminalCell() },
            cursorCol = 0,
            cursorRow = 0,
            cursorVisible = true,
            applicationCursorKeys = false,
            revision = 0L,
            hyperlinks = persistentListOf(),
        )
    }
}

const val DEFAULT_COLUMNS = 80
const val DEFAULT_ROWS = 24

const val MIN_COLUMNS = 20
const val MAX_COLUMNS = 300
const val MIN_ROWS = 8
const val MAX_ROWS = 120
