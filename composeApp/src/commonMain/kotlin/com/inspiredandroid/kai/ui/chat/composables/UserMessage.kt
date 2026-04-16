package com.inspiredandroid.kai.ui.chat.composables

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.inspiredandroid.kai.data.Attachment
import com.inspiredandroid.kai.data.UiSubmission
import com.inspiredandroid.kai.decodeToImageBitmap
import com.inspiredandroid.kai.ui.dynamicui.FrozenSubmission
import com.inspiredandroid.kai.ui.dynamicui.KaiUiParser
import com.inspiredandroid.kai.ui.dynamicui.KaiUiRenderer
import com.inspiredandroid.kai.ui.handCursor
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.ic_file
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.painterResource
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun UserMessage(
    message: String,
    attachments: ImmutableList<Attachment> = persistentListOf(),
    uiSubmission: UiSubmission? = null,
    isPendingSubmission: Boolean = false,
    onResubmit: ((event: String, data: Map<String, String>) -> Unit)? = null,
) {
    if (uiSubmission != null) {
        SubmittedUiMessage(uiSubmission, isPendingSubmission, onResubmit)
        return
    }
    SelectionContainer {
        Row(Modifier.padding(16.dp)) {
            Spacer(Modifier.weight(1f))
            Column(
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f),
                        RoundedCornerShape(8.dp),
                    )
                    .padding(16.dp),
                horizontalAlignment = Alignment.End,
            ) {
                val images = attachments.filter { it.mimeType.startsWith("image/") }
                val others = attachments.filter { !it.mimeType.startsWith("image/") }
                for (att in images) {
                    val imageBitmap = remember(att.data) {
                        try {
                            decodeToImageBitmap(Base64.decode(att.data))
                        } catch (_: Exception) {
                            null
                        }
                    }
                    if (imageBitmap != null) {
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = null,
                            modifier = Modifier
                                .widthIn(max = 200.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.FillWidth,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
                if (others.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        for (att in others) {
                            SuggestionChip(
                                onClick = {},
                                icon = {
                                    Icon(
                                        modifier = Modifier.size(16.dp),
                                        painter = painterResource(Res.drawable.ic_file),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onBackground,
                                    )
                                },
                                label = { Text(truncateFileName(att.fileName ?: att.mimeType)) },
                            )
                        }
                    }
                    if (message.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                    }
                }
                if (message.isNotEmpty()) {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }
    }
}

@Composable
private fun SubmittedUiMessage(
    submission: UiSubmission,
    isPending: Boolean,
    onResubmit: ((event: String, data: Map<String, String>) -> Unit)?,
) {
    val uiSegments = remember(submission.sourceContent) {
        KaiUiParser.parse(submission.sourceContent).filterIsInstance<KaiUiParser.UiSegment>()
    }
    if (uiSegments.isEmpty()) return
    var isEditing by remember(submission) { mutableStateOf(false) }
    // Unfreeze in place: the form becomes interactive and seeded with the user's previous
    // picks. The old exchange is popped only when the user re-clicks a form button, so the
    // chat below stays visible until the new submission actually replaces it.
    val frozen = remember(submission, isPending, isEditing) {
        FrozenSubmission(
            values = submission.values,
            pressedEvent = submission.pressedEvent.takeUnless { isEditing },
            isPending = isPending,
        )
    }
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column {
            for (segment in uiSegments) {
                KaiUiRenderer(
                    node = segment.node,
                    isInteractive = isEditing,
                    onCallback = { event, data ->
                        isEditing = false
                        onResubmit?.invoke(event, data)
                    },
                    frozen = frozen,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }
        if (onResubmit != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .handCursor()
                    .clickable { isEditing = !isEditing },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isEditing) Icons.Default.Close else Icons.Default.Edit,
                    contentDescription = if (isEditing) "Cancel edit" else "Edit submission",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
