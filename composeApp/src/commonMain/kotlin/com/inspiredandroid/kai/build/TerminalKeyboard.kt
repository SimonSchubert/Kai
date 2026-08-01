package com.inspiredandroid.kai.build

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.inspiredandroid.kai.build.terminal.TerminalKey
import com.inspiredandroid.kai.build.terminal.TerminalModifiers

/**
 * Whether this platform can deliver individual key presses to the terminal.
 * Where it cannot, the screen stays on the line composer.
 */
expect val supportsRawTerminalInput: Boolean

/**
 * Invisible input sink for the terminal. It holds keyboard focus and reports
 * what the user pressed — a named key or committed text, plus any modifiers the
 * hardware reported — leaving the byte encoding to common code.
 *
 * It is deliberately tiny rather than laid over the grid: anything covering the
 * grid would hide the terminal contents from accessibility services. Taps are
 * handled by the grid itself and arrive here as [showKeyboardRequest], which
 * summons the soft keyboard whenever the value changes.
 */
@Composable
expect fun PlatformTerminalKeyboard(
    showKeyboardRequest: Int,
    onKey: (TerminalKey, TerminalModifiers) -> Unit,
    onText: (String, TerminalModifiers) -> Unit,
    modifier: Modifier,
)
