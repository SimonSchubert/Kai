package com.inspiredandroid.kai.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inspiredandroid.kai.ui.handCursor

/**
 * Chip with full control over appearance — no hidden minimum-interactive-size
 * padding from Material's FilterChip. 38.dp tall at the default font scale, and
 * taller when the label needs the room.
 */
@Composable
fun KaiChip(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val containerColor = if (selected) colorScheme.secondaryContainer else colorScheme.surfaceContainer
    val contentColor = when {
        !enabled -> colorScheme.onSurface.copy(alpha = 0.38f)
        selected -> colorScheme.onSecondaryContainer
        else -> colorScheme.onSurfaceVariant
    }
    val borderColor = when {
        !enabled -> colorScheme.outline.copy(alpha = 0.38f)
        selected -> colorScheme.secondaryContainer
        else -> colorScheme.outline
    }
    val shape = RoundedCornerShape(8.dp)
    val border = BorderStroke(1.dp, borderColor)
    val sizeModifier = modifier.defaultMinSize(minHeight = 38.dp)

    val chipContent: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = 48.dp)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                content()
            }
        }
    }

    if (onClick != null) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = sizeModifier.handCursor(),
            shape = shape,
            color = containerColor,
            contentColor = contentColor,
            border = border,
            content = chipContent,
        )
    } else {
        Surface(
            modifier = sizeModifier,
            shape = shape,
            color = containerColor,
            contentColor = contentColor,
            border = border,
            content = chipContent,
        )
    }
}
