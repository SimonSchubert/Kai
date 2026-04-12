package com.inspiredandroid.kai.ui.chat.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
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

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            properties = PopupProperties(focusable = false),
            shape = RoundedCornerShape(16.dp),
        ) {
            services.forEach { entry ->
                val isCurrent = entry.instanceId == current.instanceId
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(
                            imageVector = vectorResource(entry.icon),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (isCurrent) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    },
                    text = {
                        Column {
                            Text(
                                text = entry.serviceName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isCurrent) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                            if (entry.modelId.isNotEmpty()) {
                                Text(
                                    text = entry.modelId,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isCurrent) {
                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        if (!isCurrent) {
                            onSelectService(entry.instanceId)
                        }
                    },
                    modifier = Modifier
                        .handCursor()
                        .then(
                            if (isCurrent) {
                                Modifier
                                    .padding(horizontal = 4.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(12.dp),
                                    )
                            } else {
                                Modifier
                            },
                        ),
                )
            }
        }
    }
}
