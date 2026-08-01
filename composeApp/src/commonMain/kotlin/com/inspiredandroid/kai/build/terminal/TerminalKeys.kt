package com.inspiredandroid.kai.build.terminal

import androidx.compose.runtime.Immutable

/**
 * Keys a terminal needs but a phone's soft keyboard either has no cap for
 * (Esc, Ctrl, arrows) or delivers as an editing command instead of bytes
 * (Enter, Backspace).
 */
enum class TerminalKey {
    Enter,
    Escape,
    Tab,
    Backspace,
    Delete,
    Up,
    Down,
    Left,
    Right,
    Home,
    End,
    PageUp,
    PageDown,
}

/** Modifier latches: set by tapping Ctrl/Alt/Shift, consumed by the next key. */
@Immutable
data class TerminalModifiers(
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false,
) {
    val any: Boolean get() = ctrl || alt || shift

    /** Combines a latch with whatever the hardware reported for the same press. */
    operator fun plus(other: TerminalModifiers) = TerminalModifiers(
        ctrl = ctrl || other.ctrl,
        alt = alt || other.alt,
        shift = shift || other.shift,
    )

    companion object {
        val None = TerminalModifiers()
    }
}

/**
 * Turns key presses into the bytes a PTY expects — the encoding readline and
 * every TUI decode.
 *
 * Two things routinely go wrong without this: Enter is carriage return (`\r`),
 * not line feed, because apps in raw mode see the byte the tty driver would
 * otherwise have translated; and arrow keys switch from CSI to SS3 form once an
 * app turns on DECCKM.
 */
object TerminalKeyEncoder {

    private const val ESC = "\u001b"

    fun encode(
        key: TerminalKey,
        modifiers: TerminalModifiers = TerminalModifiers.None,
        applicationCursorKeys: Boolean = false,
    ): String = when (key) {
        TerminalKey.Enter -> withAlt("\r", modifiers)
        TerminalKey.Escape -> ESC
        // Shift+Tab is its own sequence (CBT), not a modified Tab.
        TerminalKey.Tab -> if (modifiers.shift) "$ESC[Z" else withAlt("\t", modifiers)
        // DEL, not BS — the terminal convention readline expects.
        TerminalKey.Backspace -> when {
            modifiers.ctrl -> "\b"
            else -> withAlt("\u007f", modifiers)
        }

        TerminalKey.Up -> cursor('A', modifiers, applicationCursorKeys)
        TerminalKey.Down -> cursor('B', modifiers, applicationCursorKeys)
        TerminalKey.Right -> cursor('C', modifiers, applicationCursorKeys)
        TerminalKey.Left -> cursor('D', modifiers, applicationCursorKeys)
        TerminalKey.Home -> cursor('H', modifiers, applicationCursorKeys)
        TerminalKey.End -> cursor('F', modifiers, applicationCursorKeys)

        TerminalKey.Delete -> tilde(3, modifiers)
        TerminalKey.PageUp -> tilde(5, modifiers)
        TerminalKey.PageDown -> tilde(6, modifiers)
    }

    /**
     * Encodes a typed character. Ctrl folds it into the C0 range (Ctrl+C → 0x03),
     * Alt prefixes it with ESC — the "meta sends escape" convention.
     */
    fun encodeChar(char: Char, modifiers: TerminalModifiers = TerminalModifiers.None): String {
        val base = if (modifiers.ctrl) controlChar(char)?.toString() ?: char.toString() else char.toString()
        return withAlt(base, modifiers)
    }

    /**
     * Encodes typed text. Keyboards that ignore the null input type hand over
     * whole words at once, so a modifier latch applies to the first character —
     * a latch stands for one key press, not for the rest of the word.
     */
    fun encodeText(text: String, modifiers: TerminalModifiers = TerminalModifiers.None): String {
        if (!modifiers.any || text.isEmpty()) return text
        return encodeChar(text[0], modifiers) + text.substring(1)
    }

    private fun controlChar(char: Char): Char? = when (char) {
        in 'a'..'z' -> (char.code and 0x1F).toChar()
        in 'A'..'Z' -> (char.code and 0x1F).toChar()
        '@', ' ' -> '\u0000'
        '[' -> '\u001b'
        '\\' -> '\u001c'
        ']' -> '\u001d'
        '^' -> '\u001e'
        '_' -> '\u001f'
        '?' -> '\u007f'
        else -> null
    }

    private fun withAlt(sequence: String, modifiers: TerminalModifiers): String =
        if (modifiers.alt) "$ESC$sequence" else sequence

    /**
     * Cursor and Home/End keys. Modified presses always use the CSI form with a
     * parameter, even in application mode — that is what xterm emits.
     */
    private fun cursor(final: Char, modifiers: TerminalModifiers, applicationCursorKeys: Boolean): String = when {
        modifiers.any -> "$ESC[1;${modifierParam(modifiers)}$final"
        applicationCursorKeys -> "${ESC}O$final"
        else -> "$ESC[$final"
    }

    private fun tilde(number: Int, modifiers: TerminalModifiers): String = if (modifiers.any) {
        "$ESC[$number;${modifierParam(modifiers)}~"
    } else {
        "$ESC[$number~"
    }

    /** xterm's modifier encoding: 1 + shift(1) + alt(2) + ctrl(4). */
    private fun modifierParam(modifiers: TerminalModifiers): Int {
        var param = 1
        if (modifiers.shift) param += 1
        if (modifiers.alt) param += 2
        if (modifiers.ctrl) param += 4
        return param
    }
}
