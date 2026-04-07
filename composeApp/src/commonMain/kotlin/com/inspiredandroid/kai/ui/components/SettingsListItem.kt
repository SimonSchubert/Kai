package com.inspiredandroid.kai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inspiredandroid.kai.ui.handCursor

@Composable
fun SettingsListItem(
    title: String,
    subtitle: String,
    onDelete: () -> Unit,
    deleteContentDescription: String?,
    modifier: Modifier = Modifier,
    subtitleMaxLines: Int = 1,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = subtitleMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.handCursor(),
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = deleteContentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
