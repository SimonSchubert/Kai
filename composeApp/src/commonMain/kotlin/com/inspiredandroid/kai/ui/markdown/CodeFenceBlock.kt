package com.inspiredandroid.kai.ui.markdown

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@Composable
internal fun CodeFenceBlock(
    language: String?,
    code: String,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val highlightColors = remember(colorScheme) { codeHighlightColors(colorScheme) }
    val highlighted = remember(code, language, highlightColors) {
        highlightCode(code, language, highlightColors)
    }
    val clipboard = LocalClipboardManager.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = colorScheme.surfaceVariant,
        contentColor = colorScheme.onSurfaceVariant,
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
            ) {
                Text(
                    text = language?.takeIf { it.isNotBlank() } ?: "",
                    style = MaterialTheme.typography.labelSmall,
                )
                IconButton(
                    onClick = { clipboard.setText(AnnotatedString(code)) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "Copy code",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            HorizontalDivider(color = colorScheme.outline.copy(alpha = 0.2f))
            val scroll = rememberScrollState()
            Box(Modifier.horizontalScroll(scroll).padding(12.dp)) {
                Text(
                    text = highlighted,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
