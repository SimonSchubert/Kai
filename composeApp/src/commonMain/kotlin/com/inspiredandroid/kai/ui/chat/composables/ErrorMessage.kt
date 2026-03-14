package com.inspiredandroid.kai.ui.chat.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import com.inspiredandroid.kai.network.UiError
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.ic_refresh
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@Composable
internal fun ErrorMessage(
    error: UiError,
    retry: () -> Unit,
) {
    val text = when (error) {
        is UiError.Resource -> stringResource(error.resource)
        is UiError.Text -> error.message
    }
    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        IconButton(
            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
            onClick = retry,
        ) {
            Icon(
                imageVector = vectorResource(Res.drawable.ic_refresh),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}
