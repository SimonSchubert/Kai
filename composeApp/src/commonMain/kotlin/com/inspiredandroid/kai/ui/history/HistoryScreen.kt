package com.inspiredandroid.kai.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inspiredandroid.kai.BackIcon
import com.inspiredandroid.kai.data.Conversation
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.history_delete_all
import kai.composeapp.generated.resources.history_delete_all_confirm
import kai.composeapp.generated.resources.history_delete_all_message
import kai.composeapp.generated.resources.history_empty
import kai.composeapp.generated.resources.history_title
import kai.composeapp.generated.resources.ic_delete_forever
import kai.composeapp.generated.resources.no
import kai.composeapp.generated.resources.yes
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = koinViewModel(),
    onNavigateBack: () -> Unit,
    onSelectConversation: (String) -> Unit,
) {
    val uiState by viewModel.state.collectAsState()

    HistoryScreenContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onSelectConversation = onSelectConversation,
        onDeleteConversation = viewModel::deleteConversation,
        onShowDeleteAllDialog = viewModel::showDeleteAllDialog,
        onHideDeleteAllDialog = viewModel::hideDeleteAllDialog,
        onDeleteAllConversations = viewModel::deleteAllConversations,
    )
}

@Composable
fun HistoryScreenContent(
    uiState: HistoryUiState,
    onNavigateBack: () -> Unit = {},
    onSelectConversation: (String) -> Unit = {},
    onDeleteConversation: (String) -> Unit = {},
    onShowDeleteAllDialog: () -> Unit = {},
    onHideDeleteAllDialog: () -> Unit = {},
    onDeleteAllConversations: () -> Unit = {},
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TopBar(
                onNavigateBack = onNavigateBack,
                onDeleteAll = onShowDeleteAllDialog,
                hasConversations = uiState.conversations.isNotEmpty(),
            )

            if (uiState.conversations.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    items(
                        items = uiState.conversations,
                        key = { it.id },
                    ) { conversation ->
                        ConversationCard(
                            conversation = conversation,
                            onClick = { onSelectConversation(conversation.id) },
                            onDelete = { onDeleteConversation(conversation.id) },
                        )
                    }
                    item {
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    if (uiState.showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = onHideDeleteAllDialog,
            title = { Text(stringResource(Res.string.history_delete_all_confirm)) },
            text = { Text(stringResource(Res.string.history_delete_all_message)) },
            confirmButton = {
                TextButton(onClick = onDeleteAllConversations) {
                    Text(stringResource(Res.string.yes))
                }
            },
            dismissButton = {
                TextButton(onClick = onHideDeleteAllDialog) {
                    Text(stringResource(Res.string.no))
                }
            },
        )
    }
}

@Composable
private fun TopBar(
    onNavigateBack: () -> Unit,
    onDeleteAll: () -> Unit,
    hasConversations: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
            onClick = onNavigateBack,
        ) {
            Icon(
                imageVector = BackIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }

        Spacer(Modifier.weight(1f))

        if (hasConversations) {
            IconButton(
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                onClick = onDeleteAll,
            ) {
                Icon(
                    imageVector = vectorResource(Res.drawable.ic_delete_forever),
                    contentDescription = stringResource(Res.string.history_delete_all),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(Res.string.history_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ConversationCard(
    conversation: Conversation,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .widthIn(max = 500.dp)
            .fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onClick() }
                    .pointerHoverIcon(PointerIcon.Hand)
                    .padding(16.dp),
            ) {
                Text(
                    text = conversation.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(Modifier.height(4.dp))

                val lastMessage = conversation.messages.lastOrNull()
                if (lastMessage != null) {
                    Text(
                        text = stripMarkdown(lastMessage.content),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    text = formatDate(conversation.updatedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            IconButton(
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                onClick = onDelete,
            ) {
                Icon(
                    imageVector = vectorResource(Res.drawable.ic_delete_forever),
                    contentDescription = stringResource(Res.string.history_delete_all),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    // Simple date formatting - shows relative time or date
    val now = Clock.System.now().toEpochMilliseconds()
    val diff = now - timestamp

    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> "${days / 7}w ago"
    }
}

private fun stripMarkdown(text: String): String = text
    .filterNot { it in "*#`>_~" }
    .replace(Regex("\\s+"), " ")
    .trim()
