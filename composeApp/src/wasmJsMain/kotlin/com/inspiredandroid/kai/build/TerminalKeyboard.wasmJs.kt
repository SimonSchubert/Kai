package com.inspiredandroid.kai.build

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.inspiredandroid.kai.build.terminal.TerminalKey
import com.inspiredandroid.kai.build.terminal.TerminalModifiers

/** Kai Build is Android-only; there is no environment to type into here. */
actual val supportsRawTerminalInput: Boolean = false

@Composable
actual fun PlatformTerminalKeyboard(
    showKeyboardRequest: Int,
    onKey: (TerminalKey, TerminalModifiers) -> Unit,
    onText: (String, TerminalModifiers) -> Unit,
    modifier: Modifier,
) = Unit
