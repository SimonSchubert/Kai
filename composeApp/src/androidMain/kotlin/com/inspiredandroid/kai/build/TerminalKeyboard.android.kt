package com.inspiredandroid.kai.build

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.inspiredandroid.kai.build.terminal.TerminalKey
import com.inspiredandroid.kai.build.terminal.TerminalModifiers

actual val supportsRawTerminalInput: Boolean = true

@Composable
actual fun PlatformTerminalKeyboard(
    showKeyboardRequest: Int,
    onKey: (TerminalKey, TerminalModifiers) -> Unit,
    onText: (String, TerminalModifiers) -> Unit,
    modifier: Modifier,
) {
    var view by remember { mutableStateOf<TerminalInputView?>(null) }

    LaunchedEffect(showKeyboardRequest, view) {
        if (showKeyboardRequest > 0) view?.showKeyboard()
    }

    AndroidView(
        modifier = modifier,
        factory = { context -> TerminalInputView(context).also { view = it } },
        update = { input ->
            input.onKey = onKey
            input.onText = onText
        },
        onRelease = { view = null },
    )
}
