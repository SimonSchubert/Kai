package com.inspiredandroid.kai.ui.chat.composables

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.inspiredandroid.kai.data.Attachment
import com.inspiredandroid.kai.decodeToImageBitmap
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.ic_edit
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
    onEdit: (() -> Unit)? = null,
) {
    Column(horizontalAlignment = Alignment.End) {
        SelectionContainer {
            Row(Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp)) {
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
        if (onEdit != null) {
            Row(Modifier.padding(horizontal = 8.dp)) {
                SmallIconButton(
                    iconResource = Res.drawable.ic_edit,
                    contentDescription = "Edit",
                    onClick = onEdit,
                )
            }
        }
    }
}
