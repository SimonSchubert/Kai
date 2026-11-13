package com.kainext.duo.ui.chat.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kainext.duo.ui.handCursor
import com.kainext.duo.ui.rememberCopyToClipboard
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.bot_message_copy_content_description
import kai.composeapp.generated.resources.tool_card_collapse
import kai.composeapp.generated.resources.tool_card_expand
import kai.composeapp.generated.resources.tool_card_output
import org.jetbrains.compose.resources.stringResource

/** Tool names whose output reads as a terminal transcript rather than prose. */
internal val SHELL_TOOL_NAMES = setOf(
    "execute_shell_command",
    "android_shell",
)

/**
 * An inline, non-modal card for a completed tool call, embedded in the chat stream.
 *
 * This is deliberately *not* a dialog or bottom sheet: the result belongs to the conversation, so
 * it scrolls with it. Shell output is shown in a monospace block with a terminal label; other
 * tools collapse to a one-line summary until tapped.
 */
@Composable
internal fun ToolResultCard(
    toolName: String,
    content: String,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
) {
    val isShell = toolName in SHELL_TOOL_NAMES
    // Shell output is the interesting part of a device-control run, so it opens by default.
    // Everything else starts collapsed to keep the transcript readable.
    var expanded by remember(content, toolName) { mutableStateOf(initiallyExpanded || isShell) }
    val copyToClipboard = rememberCopyToClipboard()

    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .handCursor()
                    .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Terminal,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = toolName,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(28.dp),
                ) {
                    Text(
                        text = stringResource(
                            if (expanded) Res.string.tool_card_collapse else Res.string.tool_card_expand,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                IconButton(
                    onClick = { copyToClipboard(content) },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = stringResource(Res.string.bot_message_copy_content_description),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }

            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                if (isShell) {
                    val scroll = rememberScrollState()
                    Box(
                        Modifier
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                            .horizontalScroll(scroll)
                            .padding(12.dp),
                    ) {
                        Text(
                            text = content.trim(),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(Res.string.tool_card_output),
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Text(
                            text = content.trim(),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 12,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}
