package com.inspiredandroid.kai.ui.soul

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.inspiredandroid.kai.BackIcon
import com.inspiredandroid.kai.ui.KaiOutlinedTextField
import com.inspiredandroid.kai.ui.handCursor
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.default_soul
import kai.composeapp.generated.resources.settings_soul
import kai.composeapp.generated.resources.settings_soul_description
import kai.composeapp.generated.resources.settings_soul_reset
import kai.composeapp.generated.resources.settings_soul_reset_cancel
import kai.composeapp.generated.resources.settings_soul_reset_confirm
import kai.composeapp.generated.resources.settings_soul_save
import org.jetbrains.compose.resources.stringResource

@Composable
fun SoulScreen(
    viewModel: SoulViewModel,
    onNavigateBack: () -> Unit,
    navigationTabBar: (@Composable () -> Unit)? = null,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.onScreenVisible()
    }

    SoulScreenContent(
        state = state,
        onNavigateBack = onNavigateBack,
        onSaveSoul = viewModel::onSaveSoul,
        navigationTabBar = navigationTabBar,
    )
}

@Composable
fun SoulScreenContent(
    state: SoulUiState,
    onNavigateBack: () -> Unit,
    onSaveSoul: (String) -> Unit,
    navigationTabBar: (@Composable () -> Unit)? = null,
) {
    val localizedDefault = stringResource(Res.string.default_soul)
    val displayText = state.soulText.ifEmpty { localizedDefault }
    var editedText by remember(displayText) { mutableStateOf(displayText) }
    val hasChanges = editedText != displayText
    val maxChars = 4000

    var showResetDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onNavigateBack, modifier = Modifier.handCursor()) {
                Icon(
                    imageVector = BackIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(Res.string.settings_soul),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.weight(1f))
            if (state.soulText.isNotEmpty()) {
                IconButton(
                    onClick = { showResetDialog = true },
                    modifier = Modifier.handCursor(),
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay,
                        contentDescription = stringResource(Res.string.settings_soul_reset),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Spacer(Modifier.width(48.dp))
            }
        }

        Text(
            text = stringResource(Res.string.settings_soul_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))

        KaiOutlinedTextField(
            modifier = Modifier.fillMaxWidth().height(300.dp),
            value = editedText,
            onValueChange = { if (it.length <= maxChars) editedText = it },
            label = {
                Text(
                    stringResource(Res.string.settings_soul),
                    color = MaterialTheme.colorScheme.onBackground,
                )
            },
        )

        Text(
            text = "${editedText.length}/$maxChars",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.End,
        )

        if (hasChanges) {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onSaveSoul(editedText.trim()) },
                modifier = Modifier.align(Alignment.CenterHorizontally).handCursor(),
            ) {
                Text(stringResource(Res.string.settings_soul_save))
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(Res.string.settings_soul_reset)) },
            text = { Text(stringResource(Res.string.settings_soul_reset_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        onSaveSoul("")
                        editedText = localizedDefault
                    },
                    modifier = Modifier.handCursor(),
                ) {
                    Text(stringResource(Res.string.settings_soul_reset))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetDialog = false },
                    modifier = Modifier.handCursor(),
                ) {
                    Text(stringResource(Res.string.settings_soul_reset_cancel))
                }
            },
        )
    }
}