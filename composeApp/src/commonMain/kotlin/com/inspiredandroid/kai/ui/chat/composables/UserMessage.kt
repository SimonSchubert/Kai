package com.inspiredandroid.kai.ui.chat.composables

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import com.inspiredandroid.kai.decodeToImageBitmap
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.ic_file
import org.jetbrains.compose.resources.painterResource
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
@Composable
internal fun UserMessage(
    message: String,
    imageData: String? = null,
    mimeType: String? = null,
    fileName: String? = null,
) {
    val isImage = mimeType == null || mimeType.startsWith("image/")

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
                if (imageData != null && isImage) {
                    val imageBitmap = remember(imageData) {
                        try {
                            val bytes = Base64.decode(imageData)
                            decodeToImageBitmap(bytes)
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
                        if (message.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                } else if (imageData != null && fileName != null) {
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
                        label = { Text(fileName) },
                    )
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
