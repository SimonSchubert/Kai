package com.inspiredandroid.kai.ui.chat.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.inspiredandroid.kai.data.ServiceEntry
import com.inspiredandroid.kai.ui.handCursor
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.vectorResource

@Composable
internal fun ServiceSelector(
    services: ImmutableList<ServiceEntry>,
    onSelectService: (String) -> Unit,
) {
    if (services.isEmpty()) return

    val current = services.first()
    var expanded by remember { mutableStateOf(false) }

    Box {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                .clickable { expanded = true }
                .handCursor(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = vectorResource(current.icon),
                contentDescription = current.serviceName,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (expanded) {
            val spacingPx = with(LocalDensity.current) { 8.dp.roundToPx() }
            Popup(
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = false),
                popupPositionProvider = remember(spacingPx) { AnchorAbovePositionProvider(spacingPx) },
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                    shadowElevation = 8.dp,
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        services.forEach { entry ->
                            val isCurrent = entry.instanceId == current.instanceId
                            ServiceMenuItem(
                                entry = entry,
                                isCurrent = isCurrent,
                                onClick = {
                                    expanded = false
                                    if (!isCurrent) {
                                        onSelectService(entry.instanceId)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceMenuItem(
    entry: ServiceEntry,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    val rowBackground = if (isCurrent) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }
    val textColor = if (isCurrent) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val subTextColor = if (isCurrent) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(rowBackground)
            .clickable(onClick = onClick)
            .handCursor()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .widthIn(min = 200.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = vectorResource(entry.icon),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = textColor,
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = entry.serviceName,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
            )
            if (entry.modelId.isNotEmpty()) {
                Text(
                    text = entry.modelId,
                    style = MaterialTheme.typography.bodySmall,
                    color = subTextColor,
                )
            }
        }
    }
}

private class AnchorAbovePositionProvider(
    private val verticalSpacing: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val x = (anchorBounds.right - popupContentSize.width).coerceIn(0, maxX)
        val above = anchorBounds.top - popupContentSize.height - verticalSpacing
        val y = if (above >= 0) {
            above
        } else {
            val maxY = (windowSize.height - popupContentSize.height).coerceAtLeast(0)
            (anchorBounds.bottom + verticalSpacing).coerceAtMost(maxY)
        }
        return IntOffset(x, y)
    }
}
