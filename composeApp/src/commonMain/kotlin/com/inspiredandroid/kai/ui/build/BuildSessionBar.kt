package com.inspiredandroid.kai.ui.build

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inspiredandroid.kai.build.BuildAgent
import com.inspiredandroid.kai.build.BuildAgents
import com.inspiredandroid.kai.build.BuildTerminalSession
import com.inspiredandroid.kai.ui.handCursor
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.kai_build_back_content_description
import kai.composeapp.generated.resources.kai_build_files_tab
import kai.composeapp.generated.resources.kai_build_session_close_content_description
import kai.composeapp.generated.resources.kai_build_session_new_content_description
import kai.composeapp.generated.resources.kai_build_session_shell
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource

/**
 * The terminal screen's only chrome: back out of the project, switch between the
 * project's live shells, and start another one. Replaces a separate title bar so
 * the cell grid keeps the vertical space.
 */
@Composable
internal fun BuildSessionBar(
    sessions: ImmutableList<BuildTerminalSession>,
    activeSessionId: String?,
    installedAgents: ImmutableList<BuildAgent>,
    filesSelected: Boolean,
    onBack: () -> Unit,
    onSelectSession: (String) -> Unit,
    onCloseSession: (String) -> Unit,
    onSelectFiles: () -> Unit,
    onNewSession: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showNewMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(40.dp).handCursor()) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(Res.string.kai_build_back_content_description),
            )
        }

        Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            sessions.forEach { session ->
                SessionTab(
                    session = session,
                    numbered = sessions.size > 1,
                    selected = !filesSelected && session.id == activeSessionId,
                    onSelect = { onSelectSession(session.id) },
                    onClose = { onCloseSession(session.id) },
                )
            }
            // Pinned: the project's files are always there, unlike a session.
            TabPill(
                label = stringResource(Res.string.kai_build_files_tab),
                selected = filesSelected,
                onSelect = onSelectFiles,
            )
        }

        Box {
            IconButton(
                onClick = { showNewMenu = true },
                modifier = Modifier.size(40.dp).handCursor(),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(Res.string.kai_build_session_new_content_description),
                )
            }
            DropdownMenu(expanded = showNewMenu, onDismissRequest = { showNewMenu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.kai_build_session_shell)) },
                    onClick = {
                        showNewMenu = false
                        onNewSession(null)
                    },
                    modifier = Modifier.handCursor(),
                )
                installedAgents.forEach { agent ->
                    DropdownMenuItem(
                        text = { Text(agent.title) },
                        onClick = {
                            showNewMenu = false
                            onNewSession(agent.id)
                        },
                        modifier = Modifier.handCursor(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionTab(
    session: BuildTerminalSession,
    numbered: Boolean,
    selected: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
) {
    val base = BuildAgents.get(session.agentId)?.title ?: stringResource(Res.string.kai_build_session_shell)
    val label = if (numbered) "$base ${session.number}" else base
    val contentColor = when {
        // A finished shell keeps its output readable but reads as inactive.
        !session.busy -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    TabPill(
        label = label,
        selected = selected,
        onSelect = onSelect,
        contentColor = contentColor,
    ) {
        IconButton(onClick = onClose, modifier = Modifier.size(28.dp).handCursor()) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(
                    Res.string.kai_build_session_close_content_description,
                ),
                tint = contentColor,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/** The strip's pill shape. [trailing] is shown only while selected — that is where a tab's close button goes. */
@Composable
private fun TabPill(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    contentColor: Color = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    },
    trailing: @Composable (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(50)
    Surface(
        modifier = Modifier.clip(shape).clickable(onClick = onSelect).handCursor(),
        shape = shape,
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent,
    ) {
        Row(
            modifier = Modifier.padding(
                start = 12.dp,
                end = if (selected && trailing != null) 2.dp else 12.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            if (selected && trailing != null) trailing()
        }
    }
}
