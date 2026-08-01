package com.inspiredandroid.kai.build.terminal

import kotlin.test.Test
import kotlin.test.assertEquals

class TerminalKeyEncoderTest {

    private val ctrl = TerminalModifiers(ctrl = true)
    private val alt = TerminalModifiers(alt = true)
    private val shift = TerminalModifiers(shift = true)

    @Test
    fun enterIsCarriageReturn() {
        assertEquals("\r", TerminalKeyEncoder.encode(TerminalKey.Enter))
    }

    @Test
    fun altEnterPrefixesEscape() {
        assertEquals("\u001b\r", TerminalKeyEncoder.encode(TerminalKey.Enter, alt))
    }

    @Test
    fun backspaceIsDelNotBackspace() {
        assertEquals("\u007f", TerminalKeyEncoder.encode(TerminalKey.Backspace))
        assertEquals("\b", TerminalKeyEncoder.encode(TerminalKey.Backspace, ctrl))
    }

    @Test
    fun altBackspaceDeletesWord() {
        assertEquals("\u001b\u007f", TerminalKeyEncoder.encode(TerminalKey.Backspace, alt))
    }

    @Test
    fun shiftTabIsCbt() {
        assertEquals("\t", TerminalKeyEncoder.encode(TerminalKey.Tab))
        assertEquals("\u001b[Z", TerminalKeyEncoder.encode(TerminalKey.Tab, shift))
    }

    @Test
    fun escapeIsEsc() {
        assertEquals("\u001b", TerminalKeyEncoder.encode(TerminalKey.Escape))
    }

    @Test
    fun arrowsUseCsiByDefault() {
        assertEquals("\u001b[A", TerminalKeyEncoder.encode(TerminalKey.Up))
        assertEquals("\u001b[B", TerminalKeyEncoder.encode(TerminalKey.Down))
        assertEquals("\u001b[C", TerminalKeyEncoder.encode(TerminalKey.Right))
        assertEquals("\u001b[D", TerminalKeyEncoder.encode(TerminalKey.Left))
    }

    @Test
    fun arrowsUseSs3InApplicationCursorMode() {
        assertEquals(
            "\u001bOA",
            TerminalKeyEncoder.encode(TerminalKey.Up, applicationCursorKeys = true),
        )
        assertEquals(
            "\u001bOD",
            TerminalKeyEncoder.encode(TerminalKey.Left, applicationCursorKeys = true),
        )
    }

    @Test
    fun modifiedArrowsStayCsiEvenInApplicationMode() {
        // xterm emits the parameterized CSI form regardless of DECCKM.
        assertEquals(
            "\u001b[1;5C",
            TerminalKeyEncoder.encode(TerminalKey.Right, ctrl, applicationCursorKeys = true),
        )
    }

    @Test
    fun modifierParamFollowsXterm() {
        assertEquals("\u001b[1;2A", TerminalKeyEncoder.encode(TerminalKey.Up, shift))
        assertEquals("\u001b[1;3A", TerminalKeyEncoder.encode(TerminalKey.Up, alt))
        assertEquals("\u001b[1;5A", TerminalKeyEncoder.encode(TerminalKey.Up, ctrl))
        assertEquals(
            "\u001b[1;8A",
            TerminalKeyEncoder.encode(
                TerminalKey.Up,
                TerminalModifiers(ctrl = true, alt = true, shift = true),
            ),
        )
    }

    @Test
    fun tildeKeysCarryModifiers() {
        assertEquals("\u001b[3~", TerminalKeyEncoder.encode(TerminalKey.Delete))
        assertEquals("\u001b[5~", TerminalKeyEncoder.encode(TerminalKey.PageUp))
        assertEquals("\u001b[6;5~", TerminalKeyEncoder.encode(TerminalKey.PageDown, ctrl))
    }

    @Test
    fun homeAndEndUseCsi() {
        assertEquals("\u001b[H", TerminalKeyEncoder.encode(TerminalKey.Home))
        assertEquals("\u001b[F", TerminalKeyEncoder.encode(TerminalKey.End))
    }

    @Test
    fun ctrlCharsFoldIntoC0Range() {
        assertEquals("\u0003", TerminalKeyEncoder.encodeChar('c', ctrl))
        assertEquals("\u0003", TerminalKeyEncoder.encodeChar('C', ctrl))
        assertEquals("\u0004", TerminalKeyEncoder.encodeChar('d', ctrl))
        assertEquals("\u001a", TerminalKeyEncoder.encodeChar('z', ctrl))
        assertEquals("\u0000", TerminalKeyEncoder.encodeChar(' ', ctrl))
        assertEquals("\u001c", TerminalKeyEncoder.encodeChar('\\', ctrl))
    }

    @Test
    fun plainAndAltCharsPassThrough() {
        assertEquals("a", TerminalKeyEncoder.encodeChar('a'))
        assertEquals("\u001ba", TerminalKeyEncoder.encodeChar('a', alt))
        // No control mapping exists for digits — send the character itself.
        assertEquals("1", TerminalKeyEncoder.encodeChar('1', ctrl))
    }

    @Test
    fun encodeTextPassesPlainTextThrough() {
        assertEquals("ls -la", TerminalKeyEncoder.encodeText("ls -la"))
        assertEquals("", TerminalKeyEncoder.encodeText(""))
    }

    @Test
    fun encodeTextAppliesLatchToFirstCharacterOnly() {
        // A latched modifier stands for one key press, not for the whole word.
        assertEquals("\u0003at", TerminalKeyEncoder.encodeText("cat", ctrl))
        assertEquals("\u001bcat", TerminalKeyEncoder.encodeText("cat", alt))
    }

    @Test
    fun modifiersCombine() {
        assertEquals(
            TerminalModifiers(ctrl = true, shift = true),
            TerminalModifiers(ctrl = true) + TerminalModifiers(shift = true),
        )
        // Merging never clears a modifier that either side had set.
        assertEquals(ctrl, ctrl + TerminalModifiers.None)
        assertEquals(TerminalModifiers.None, TerminalModifiers.None + TerminalModifiers.None)
    }
}
