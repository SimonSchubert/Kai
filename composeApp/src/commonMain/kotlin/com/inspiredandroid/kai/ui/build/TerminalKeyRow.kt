package com.inspiredandroid.kai.ui.build

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inspiredandroid.kai.build.terminal.TerminalKey
import com.inspiredandroid.kai.build.terminal.TerminalModifiers
import com.inspiredandroid.kai.ui.handCursor
import com.inspiredandroid.kai.ui.settings.monoStyle

/** Keys the row offers, in thumb order: modifiers, then navigation, then Enter. */
private val KeyCaps = listOf(
    TerminalKey.Escape to "esc",
    TerminalKey.Tab to "tab",
    TerminalKey.Left to "←",
    TerminalKey.Up to "↑",
    TerminalKey.Down to "↓",
    TerminalKey.Right to "→",
)

private val KeyCapHeight = 34.dp
private val KeyCapMinWidth = 44.dp
private val KeyCapFontSize = 13.sp

/**
 * The keys no soft keyboard has. Ctrl/Alt/Shift latch for exactly one press —
 * tap Ctrl then C to interrupt — which is how every mobile terminal handles
 * modifiers that have no physical key to hold down.
 *
 * The latch is owned by the caller so it also applies to characters typed on
 * the soft keyboard, not just to presses from this row.
 */
@Composable
internal fun TerminalKeyRow(
    enabled: Boolean,
    latched: TerminalModifiers,
    onLatchChange: (TerminalModifiers) -> Unit,
    onKey: (TerminalKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF151515))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KeyCap(
            label = "ctrl",
            enabled = enabled,
            active = latched.ctrl,
            onClick = { onLatchChange(latched.copy(ctrl = !latched.ctrl)) },
        )
        KeyCap(
            label = "alt",
            enabled = enabled,
            active = latched.alt,
            onClick = { onLatchChange(latched.copy(alt = !latched.alt)) },
        )
        KeyCap(
            label = "shift",
            enabled = enabled,
            active = latched.shift,
            onClick = { onLatchChange(latched.copy(shift = !latched.shift)) },
        )

        KeyCaps.forEach { (key, label) ->
            KeyCap(label = label, enabled = enabled, onClick = { onKey(key) })
        }

        KeyCap(
            label = "⏎",
            enabled = enabled,
            accent = true,
            onClick = { onKey(TerminalKey.Enter) },
        )
    }
}

@Composable
private fun KeyCap(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    accent: Boolean = false,
) {
    val content = when {
        !enabled -> AnsiPalette[8]
        active || accent -> AnsiPalette[10]
        else -> AnsiPalette[7]
    }
    val container = when {
        active -> AnsiPalette[10].copy(alpha = 0.22f)
        else -> Color(0xFF262626)
    }

    Box(
        modifier = modifier
            .height(KeyCapHeight)
            .defaultMinSize(minWidth = KeyCapMinWidth)
            .background(container, RoundedCornerShape(6.dp))
            .handCursor()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = monoStyle(KeyCapFontSize, content))
    }
}
