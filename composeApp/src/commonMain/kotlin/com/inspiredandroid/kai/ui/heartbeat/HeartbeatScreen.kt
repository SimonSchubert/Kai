package com.inspiredandroid.kai.ui.heartbeat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inspiredandroid.kai.BackIcon
import com.inspiredandroid.kai.ui.KaiOutlinedTextField
import com.inspiredandroid.kai.ui.components.KaiRangeSlider
import com.inspiredandroid.kai.ui.components.KaiSlider
import com.inspiredandroid.kai.ui.handCursor
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.settings_heartbeat
import kai.composeapp.generated.resources.settings_heartbeat_active_hours
import kai.composeapp.generated.resources.settings_heartbeat_default_prompt
import kai.composeapp.generated.resources.settings_heartbeat_description
import kai.composeapp.generated.resources.settings_heartbeat_interval
import kai.composeapp.generated.resources.settings_heartbeat_model
import kai.composeapp.generated.resources.settings_heartbeat_model_default
import kai.composeapp.generated.resources.settings_heartbeat_prompt_label
import kai.composeapp.generated.resources.settings_heartbeat_recent
import kai.composeapp.generated.resources.settings_heartbeat_reset_confirm
import kai.composeapp.generated.resources.settings_soul_reset
import kai.composeapp.generated.resources.settings_soul_reset_cancel
import kai.composeapp.generated.resources.settings_soul_save
import kotlinx.collections.immutable.ImmutableList
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import kotlin.math.roundToInt
import kotlin.time.Instant

@Composable
fun HeartbeatScreen(
    viewModel: HeartbeatViewModel,
    onNavigateBack: () -> Unit,
    navigationTabBar: (@Composable () -> Unit)? = null,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.onScreenVisible()
    }

    HeartbeatScreenContent(
        state = state,
        onNavigateBack = onNavigateBack,
        onToggleHeartbeat = viewModel::onToggleHeartbeat,
        onChangeInterval = viewModel::onChangeInterval,
        onChangeActiveHours = viewModel::onChangeActiveHours,
        onSaveHeartbeatPrompt = viewModel::onSaveHeartbeatPrompt,
        onChangeHeartbeatService = viewModel::onChangeHeartbeatService,
        navigationTabBar = navigationTabBar,
    )
}

@Composable
fun HeartbeatScreenContent(
    state: HeartbeatUiState,
    onNavigateBack: () -> Unit,
    onToggleHeartbeat: (Boolean) -> Unit,
    onChangeInterval: (Int) -> Unit,
    onChangeActiveHours: (Int, Int) -> Unit,
    onSaveHeartbeatPrompt: (String) -> Unit,
    onChangeHeartbeatService: (String?) -> Unit,
    navigationTabBar: (@Composable () -> Unit)? = null,
) {
    val defaultPrompt = stringResource(Res.string.settings_heartbeat_default_prompt)
    val displayText = state.heartbeatPrompt.ifEmpty { defaultPrompt }
    var editedText by remember(displayText) { mutableStateOf(displayText) }
    val hasChanges = editedText != displayText
    val maxChars = 4000

    var showResetDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
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
                text = stringResource(Res.string.settings_heartbeat),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.weight(1f))
            if (state.heartbeatPrompt.isNotEmpty()) {
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

        val intervalPresets = listOf(5, 10, 15, 30, 45, 60, 120, 240)
        val initialSliderPos = intervalPresets.indexOf(state.heartbeatIntervalMinutes)
            .takeIf { it >= 0 }?.toFloat() ?: 2f
        var intervalSliderValue by remember(state.heartbeatIntervalMinutes) {
            mutableStateOf(initialSliderPos)
        }
        val currentPresetMinutes = intervalPresets[intervalSliderValue.roundToInt()]
        val intervalDisplay = if (currentPresetMinutes < 60) {
            "${currentPresetMinutes}m"
        } else {
            "${currentPresetMinutes / 60}h"
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.settings_heartbeat_interval),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = intervalDisplay,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        KaiSlider(
            value = intervalSliderValue,
            onValueChange = { intervalSliderValue = it },
            onValueChangeFinished = {
                onChangeInterval(intervalPresets[intervalSliderValue.roundToInt()])
            },
            valueRange = 0f..(intervalPresets.size - 1).toFloat(),
            steps = intervalPresets.size - 2,
        )

        Spacer(Modifier.height(12.dp))

        var activeStart by remember(state.activeHoursStart) { mutableStateOf(state.activeHoursStart.toFloat()) }
        var activeEnd by remember(state.activeHoursEnd) { mutableStateOf(state.activeHoursEnd.toFloat()) }
        val startDisplay = "${activeStart.roundToInt()}:00"
        val endDisplay = "${activeEnd.roundToInt()}:00"

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.settings_heartbeat_active_hours),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "$startDisplay – $endDisplay",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        KaiRangeSlider(
            value = activeStart..activeEnd,
            onValueChange = { range ->
                activeStart = range.start
                activeEnd = range.endInclusive
            },
            onValueChangeFinished = {
                onChangeActiveHours(activeStart.roundToInt(), activeEnd.roundToInt())
            },
            valueRange = 0f..23f,
            steps = 22,
        )

        if (state.heartbeatServiceEntries.size > 1) {
            Spacer(Modifier.height(12.dp))

            Text(
                text = stringResource(Res.string.settings_heartbeat_model),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(4.dp))

            var modelExpanded by remember { mutableStateOf(false) }
            val selectedEntry = state.heartbeatServiceEntries.find { it.instanceId == state.heartbeatSelectedInstanceId }

            Box {
                OutlinedButton(
                    onClick = { modelExpanded = true },
                    modifier = Modifier.handCursor(),
                ) {
                    if (selectedEntry != null) {
                        Icon(
                            imageVector = vectorResource(selectedEntry.icon),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${selectedEntry.serviceName} · ${selectedEntry.modelId}",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        Text(stringResource(Res.string.settings_heartbeat_model_default))
                    }
                }

                DropdownMenu(
                    expanded = modelExpanded,
                    onDismissRequest = { modelExpanded = false },
                    shape = RoundedCornerShape(16.dp),
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(Res.string.settings_heartbeat_model_default),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (state.heartbeatSelectedInstanceId == null) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        },
                        onClick = {
                            modelExpanded = false
                            onChangeHeartbeatService(null)
                        },
                        modifier = Modifier
                            .handCursor()
                            .then(
                                if (state.heartbeatSelectedInstanceId == null) {
                                    Modifier
                                        .padding(horizontal = 4.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(12.dp),
                                        )
                                } else {
                                    Modifier
                                },
                            ),
                    )
                    state.heartbeatServiceEntries.forEach { entry ->
                        val isSelected = entry.instanceId == state.heartbeatSelectedInstanceId
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    imageVector = vectorResource(entry.icon),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            },
                            text = {
                                Column {
                                    Text(
                                        text = entry.serviceName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                    Text(
                                        text = entry.modelId,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                }
                            },
                            onClick = {
                                modelExpanded = false
                                onChangeHeartbeatService(entry.instanceId)
                            },
                            modifier = Modifier
                                .handCursor()
                                .then(
                                    if (isSelected) {
                                        Modifier
                                            .padding(horizontal = 4.dp)
                                            .background(
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shape = RoundedCornerShape(12.dp),
                                            )
                                    } else {
                                        Modifier
                                    },
                                ),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        KaiOutlinedTextField(
            modifier = Modifier.fillMaxWidth().height(200.dp),
            value = editedText,
            onValueChange = { if (it.length <= maxChars) editedText = it },
            label = {
                Text(
                    stringResource(Res.string.settings_heartbeat_prompt_label),
                    color = MaterialTheme.colorScheme.onBackground,
                )
            },
        )

        Text(
            text = "${editedText.length}/$maxChars",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )

        if (hasChanges) {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onSaveHeartbeatPrompt(editedText.trim()) },
                modifier = Modifier.align(Alignment.CenterHorizontally).handCursor(),
            ) {
                Text(stringResource(Res.string.settings_soul_save))
            }
        }

        if (state.heartbeatLog.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(Res.string.settings_heartbeat_recent),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(4.dp))
            for (entry in state.heartbeatLog) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (entry.success) "OK" else "FAIL",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (entry.success) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier.width(36.dp),
                    )
                    Column {
                        Text(
                            text = formatHeartbeatTime(entry.timestampEpochMs),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!entry.success && entry.error != null) {
                            Text(
                                text = entry.error,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(Res.string.settings_soul_reset)) },
            text = { Text(stringResource(Res.string.settings_heartbeat_reset_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        onSaveHeartbeatPrompt("")
                        editedText = defaultPrompt
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

private fun formatHeartbeatTime(epochMs: Long): String {
    val instant = Instant.fromEpochMilliseconds(epochMs)
    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${local.day} ${local.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} ${local.hour}:${local.minute.toString().padStart(2, '0')}"
}