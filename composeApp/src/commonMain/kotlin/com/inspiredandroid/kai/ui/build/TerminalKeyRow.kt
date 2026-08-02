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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inspiredandroid.kai.build.terminal.TerminalKey
import com.inspiredandroid.kai.build.terminal.TerminalModifiers
import com.inspiredandroid.kai.ui.handCursor
import com.inspiredandroid.kai.ui.settings.monoStyle
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.kai_build_terminal_key_down_content_description
import kai.composeapp.generated.resources.kai_build_terminal_key_enter_content_description
import kai.composeapp.generated.resources.kai_build_terminal_key_left_content_description
import kai.composeapp.generated.resources.kai_build_terminal_key_right_content_description
import kai.composeapp.generated.resources.kai_build_terminal_key_up_content_description
import org.jetbrains.compose.resources.stringResource

/** Navigation caps, in the order a keyboard lays them out. */
private val ArrowCaps = listOf(
    Triple(TerminalKey.Left, TerminalArrowLeft, Res.string.kai_build_terminal_key_left_content_description),
    Triple(TerminalKey.Up, TerminalArrowUp, Res.string.kai_build_terminal_key_up_content_description),
    Triple(TerminalKey.Down, TerminalArrowDown, Res.string.kai_build_terminal_key_down_content_description),
    Triple(TerminalKey.Right, TerminalArrowRight, Res.string.kai_build_terminal_key_right_content_description),
)

private val KeyCapHeight = 34.dp
private val KeyCapMinWidth = 40.dp

/** Icon caps hold one glyph, so they can be squarer than the lettered ones. */
private val IconKeyCapMinWidth = 38.dp

/** Enter is the row's action key and gets the width to say so. */
private val EnterKeyCapMinWidth = 52.dp
private val KeyCapFontSize = 13.sp
private val KeyCapShape = RoundedCornerShape(8.dp)

/**
 * The keys no soft keyboard has. Ctrl/Alt/Shift latch for exactly one press —
 * tap Ctrl then C to interrupt — which is how every mobile terminal handles
 * modifiers that have no physical key to hold down.
 *
 * The latch is owned by the caller so it also applies to characters typed on
 * the soft keyboard, not just to presses from this row.
 *
 * Eleven caps are wider than a phone, so the row scrolls — which makes the
 * order a ranking. Ctrl, Esc, Tab and the arrows lead because no soft keyboard
 * offers them at all; Alt and Shift trail. Enter is pinned outside the scroll,
 * so the key that ends every command is never the one that has to be found.
 * A hairline between groups gives a thumb a landmark to aim at.
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
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KeyCap(
                label = "ctrl",
                enabled = enabled,
                active = latched.ctrl,
                onClick = { onLatchChange(latched.copy(ctrl = !latched.ctrl)) },
            )
            KeyCap(label = "esc", enabled = enabled, onClick = { onKey(TerminalKey.Escape) })
            KeyCap(label = "tab", enabled = enabled, onClick = { onKey(TerminalKey.Tab) })

            KeyGroupSeparator()

            ArrowCaps.forEach { (key, icon, description) ->
                IconKeyCap(
                    icon = icon,
                    contentDescription = stringResource(description),
                    enabled = enabled,
                    onClick = { onKey(key) },
                )
            }

            KeyGroupSeparator()

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
        }

        IconKeyCap(
            icon = TerminalEnter,
            contentDescription = stringResource(Res.string.kai_build_terminal_key_enter_content_description),
            enabled = enabled,
            accent = true,
            iconSize = TerminalEnterIconSize,
            minWidth = EnterKeyCapMinWidth,
            onClick = { onKey(TerminalKey.Enter) },
        )
    }
}

@Composable
private fun KeyGroupSeparator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(18.dp)
            .width(1.dp)
            .background(Color.White.copy(alpha = 0.10f)),
    )
}

@Composable
private fun KeyCap(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    KeyCapSurface(enabled = enabled, onClick = onClick, modifier = modifier, active = active) { tint ->
        Text(text = label, style = monoStyle(KeyCapFontSize, tint))
    }
}

@Composable
private fun IconKeyCap(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    iconSize: Dp = TerminalKeyIconSize,
    minWidth: Dp = IconKeyCapMinWidth,
) {
    KeyCapSurface(
        enabled = enabled,
        onClick = onClick,
        modifier = modifier,
        accent = accent,
        minWidth = minWidth,
    ) { tint ->
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

/** The cap itself: colors for the three states, and the tint its content draws with. */
@Composable
private fun KeyCapSurface(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    accent: Boolean = false,
    minWidth: Dp = KeyCapMinWidth,
    content: @Composable (tint: Color) -> Unit,
) {
    val tint = when {
        !enabled -> AnsiPalette[8]
        // Enter is a filled key, so its glyph is the bright thing on the fill,
        // not a green line on grey — that read as decoration rather than a key.
        accent -> AnsiPalette[15]
        active -> AnsiPalette[10]
        else -> AnsiPalette[7]
    }
    val container = when {
        accent && enabled -> AnsiPalette[10].copy(alpha = 0.25f)
        active -> AnsiPalette[10].copy(alpha = 0.22f)
        else -> Color(0xFF262626)
    }

    Box(
        modifier = modifier
            .height(KeyCapHeight)
            .defaultMinSize(minWidth = minWidth)
            // Clip before the ripple so pressing a cap lights up the cap, not its bounding box.
            .clip(KeyCapShape)
            .background(container)
            .handCursor()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        content(tint)
    }
}
