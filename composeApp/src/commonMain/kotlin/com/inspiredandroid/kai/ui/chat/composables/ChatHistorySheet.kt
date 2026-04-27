@file:OptIn(ExperimentalMaterial3Api::class)

package com.inspiredandroid.kai.ui.chat.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterEnd
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inspiredandroid.kai.ui.chat.ChatActions
import com.inspiredandroid.kai.ui.chat.ConversationSummary
import com.inspiredandroid.kai.ui.components.VerticalScrollbarForList
import com.inspiredandroid.kai.ui.components.animatedGradientBorder
import com.inspiredandroid.kai.ui.handCursor
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.chat_history_delete_content_description
import kai.composeapp.generated.resources.chat_history_empty
import kai.composeapp.generated.resources.chat_history_heartbeat_label
import kai.composeapp.generated.resources.chat_history_title
import kai.composeapp.generated.resources.ic_history
import kotlinx.collections.immutable.ImmutableList
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents.Companion.Format
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

private val dateFormat = Format {
    day()
    char(' ')
    monthName(MonthNames.ENGLISH_ABBREVIATED)
    char(' ')
    year()
}

@Composable
internal fun ChatHistorySheet(
    conversations: ImmutableList<ConversationSummary>,
    currentConversationId: String?,
    actions: ChatActions,
    onDismiss: () -> Unit,
    onConversationSelected: () -> Unit = {},
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column {
            Text(
                modifier = Modifier.padding(horizontal = 16.dp),
                text = stringResource(Res.string.chat_history_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(12.dp))

            if (conversations.isEmpty()) {
                Text(
                    text = stringResource(Res.string.chat_history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                )
            } else {
                val historyListState = rememberLazyListState()
                Box {
                    LazyColumn(
                        state = historyListState,
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(conversations, key = { it.id }) { conversation ->
                            val isActive = conversation.id == currentConversationId
                            val borderModifier = if (conversation.isInteractive) {
                                Modifier.animatedGradientBorder(
                                    cornerRadius = 12.dp,
                                    backgroundColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                )
                            } else {
                                Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(12.dp),
                                    )
                            }
                            Row(
                                modifier = borderModifier
                                    .fillMaxWidth()
                                    .handCursor()
                                    .clickable {
                                        onConversationSelected()
                                        actions.loadConversation(conversation.id)
                                        onDismiss()
                                    }
                                    .padding(vertical = 8.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    if (conversation.isHeartbeat) {
                                        Row(
                                            modifier = Modifier
                                                .padding(bottom = 4.dp)
                                                .background(
                                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                                    shape = RoundedCornerShape(4.dp),
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                imageVector = vectorResource(Res.drawable.ic_history),
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                                modifier = Modifier.size(12.dp),
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = stringResource(Res.string.chat_history_heartbeat_label),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            )
                                        }
                                    }
                                    if (conversation.title.isNotEmpty()) {
                                        Text(
                                            text = conversation.title,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = if (isActive) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onBackground
                                            },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Text(
                                        text = formatDate(conversation.updatedAt),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(
                                    modifier = Modifier.handCursor(),
                                    onClick = { actions.deleteConversation(conversation.id) },
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = stringResource(Res.string.chat_history_delete_content_description),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        item {
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                    VerticalScrollbarForList(
                        listState = historyListState,
                        modifier = Modifier.align(CenterEnd).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

private fun formatDate(epochMillis: Long): String = try {
    kotlin.time.Instant.fromEpochMilliseconds(epochMillis).format(dateFormat)
} catch (_: Exception) {
    ""
}
