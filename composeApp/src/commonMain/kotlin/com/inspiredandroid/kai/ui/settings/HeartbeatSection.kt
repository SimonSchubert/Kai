@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.inspiredandroid.kai.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inspiredandroid.kai.data.EmailAccount
import com.inspiredandroid.kai.data.EmailSyncState
import com.inspiredandroid.kai.data.HeartbeatLogEntry
import com.inspiredandroid.kai.data.ServiceEntry
import com.inspiredandroid.kai.data.SmsSyncState
import com.inspiredandroid.kai.ui.KaiOutlinedTextField
import com.inspiredandroid.kai.ui.components.KaiRangeSlider
import com.inspiredandroid.kai.ui.components.KaiSlider
import com.inspiredandroid.kai.ui.components.RefreshIconButton
import com.inspiredandroid.kai.ui.components.SettingsListItem
import com.inspiredandroid.kai.ui.handCursor
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.settings_email
import kai.composeapp.generated.resources.settings_email_description
import kai.composeapp.generated.resources.settings_email_empty
import kai.composeapp.generated.resources.settings_email_last_poll
import kai.composeapp.generated.resources.settings_email_poll_failed
import kai.composeapp.generated.resources.settings_email_poll_interval
import kai.composeapp.generated.resources.settings_email_poll_never
import kai.composeapp.generated.resources.settings_email_queued
import kai.composeapp.generated.resources.settings_email_refresh
import kai.composeapp.generated.resources.settings_email_remove
import kai.composeapp.generated.resources.settings_heartbeat
import kai.composeapp.generated.resources.settings_heartbeat_active_hours
import kai.composeapp.generated.resources.settings_heartbeat_default_prompt
import kai.composeapp.generated.resources.settings_heartbeat_description
import kai.composeapp.generated.resources.settings_heartbeat_interval
import kai.composeapp.generated.resources.settings_heartbeat_model
import kai.composeapp.generated.resources.settings_heartbeat_model_default
import kai.composeapp.generated.resources.settings_heartbeat_prompt_label
import kai.composeapp.generated.resources.settings_heartbeat_recent
import kai.composeapp.generated.resources.settings_heartbeat_refresh
import kai.composeapp.generated.resources.settings_heartbeat_reset_confirm
import kai.composeapp.generated.resources.settings_sms_description
import kai.composeapp.generated.resources.settings_sms_last_poll
import kai.composeapp.generated.resources.settings_sms_permission_button
import kai.composeapp.generated.resources.settings_sms_permission_required
import kai.composeapp.generated.resources.settings_sms_poll_failed
import kai.composeapp.generated.resources.settings_sms_poll_interval
import kai.composeapp.generated.resources.settings_sms_queued
import kai.composeapp.generated.resources.settings_sms_read_label
import kai.composeapp.generated.resources.settings_sms_refresh
import kai.composeapp.generated.resources.settings_sms_send_description
import kai.composeapp.generated.resources.settings_sms_send_label
import kai.composeapp.generated.resources.settings_sms_send_permission_required
import kai.composeapp.generated.resources.settings_notifications_access_button
import kai.composeapp.generated.resources.settings_notifications_access_required
import kai.composeapp.generated.resources.settings_notifications_clear_queue
import kai.composeapp.generated.resources.settings_notifications_description
import kai.composeapp.generated.resources.settings_notifications_label
import kai.composeapp.generated.resources.settings_notifications_listener_bound
import kai.composeapp.generated.resources.settings_notifications_listener_disconnected
import kai.composeapp.generated.resources.settings_notifications_manage_apps
import kai.composeapp.generated.resources.settings_notifications_queued
import kai.composeapp.generated.resources.settings_soul_reset
import kai.composeapp.generated.resources.settings_soul_reset_cancel
import kai.composeapp.generated.resources.settings_soul_save
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Instant

@Composable
internal fun HeartbeatSection(
    isHeartbeatEnabled: Boolean,
    heartbeatIntervalMinutes: Int,
    activeHoursStart: Int,
    activeHoursEnd: Int,
    heartbeatPrompt: String,
    heartbeatLog: ImmutableList<HeartbeatLogEntry>,
    heartbeatServiceEntries: ImmutableList<ServiceEntry>,
    heartbeatSelectedInstanceId: String?,
    isRefreshing: Boolean,
    onToggleHeartbeat: (Boolean) -> Unit,
    onChangeInterval: (Int) -> Unit,
    onChangeActiveHours: (Int, Int) -> Unit,
    onSaveHeartbeatPrompt: (String) -> Unit,
    onChangeHeartbeatService: (String?) -> Unit,
    onRefresh: () -> Unit,
) {
    val defaultPrompt = stringResource(Res.string.settings_heartbeat_default_prompt)
    val displayText = heartbeatPrompt.ifEmpty { defaultPrompt }
    var editedText by remember(displayText) { mutableStateOf(displayText) }
    val hasChanges = editedText != displayText
    val maxChars = 4000

    var showResetDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        ToggleableHeadline(
            title = stringResource(Res.string.settings_heartbeat),
            description = stringResource(Res.string.settings_heartbeat_description, heartbeatIntervalMinutes),
            checked = isHeartbeatEnabled,
            onCheckedChange = onToggleHeartbeat,
            actions = {
                if (heartbeatPrompt.isNotEmpty()) {
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
                }
            },
        )

        if (isHeartbeatEnabled) {
            Spacer(Modifier.height(12.dp))

            val intervalPresets = listOf(5, 10, 15, 30, 45, 60, 120, 240)
            val initialSliderPos = intervalPresets.indexOf(heartbeatIntervalMinutes)
                .takeIf { it >= 0 }?.toFloat() ?: 2f
            var intervalSliderValue by remember(heartbeatIntervalMinutes) {
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

            var activeStart by remember(activeHoursStart) { mutableStateOf(activeHoursStart.toFloat()) }
            var activeEnd by remember(activeHoursEnd) { mutableStateOf(activeHoursEnd.toFloat()) }
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

            if (heartbeatServiceEntries.size > 1) {
                Spacer(Modifier.height(12.dp))

                Text(
                    text = stringResource(Res.string.settings_heartbeat_model),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(4.dp))

                var modelExpanded by remember { mutableStateOf(false) }
                val selectedEntry = heartbeatServiceEntries.find { it.instanceId == heartbeatSelectedInstanceId }

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
                                    color = if (heartbeatSelectedInstanceId == null) {
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
                                    if (heartbeatSelectedInstanceId == null) {
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
                        heartbeatServiceEntries.forEach { entry ->
                            val isSelected = entry.instanceId == heartbeatSelectedInstanceId
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
                modifier = Modifier.fillMaxWidth(),
                value = editedText,
                onValueChange = { if (it.length <= maxChars) editedText = it },
                minLines = 8,
                maxLines = 8,
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
                textAlign = TextAlign.End,
            )

            if (hasChanges) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { onSaveHeartbeatPrompt(editedText.trim()) },
                    modifier = Modifier.align(CenterHorizontally).handCursor(),
                ) {
                    Text(stringResource(Res.string.settings_soul_save))
                }
            }

            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.settings_heartbeat_recent),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                RefreshIconButton(
                    onClick = onRefresh,
                    isRefreshing = isRefreshing,
                    contentDescription = stringResource(Res.string.settings_heartbeat_refresh),
                )
            }
            if (heartbeatLog.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                for (entry in heartbeatLog) {
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

@Composable
internal fun EmailSection(
    isEmailEnabled: Boolean,
    emailAccounts: ImmutableList<EmailAccount>,
    pollIntervalMinutes: Int,
    pendingCount: Int,
    syncStates: ImmutableMap<String, EmailSyncState>,
    refreshingAccountIds: ImmutableSet<String>,
    onToggleEmail: (Boolean) -> Unit,
    onRemoveAccount: (String) -> Unit,
    onChangePollInterval: (Int) -> Unit,
    onRefreshAccount: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ToggleableHeadline(
            title = stringResource(Res.string.settings_email),
            description = stringResource(Res.string.settings_email_description),
            checked = isEmailEnabled,
            onCheckedChange = onToggleEmail,
        )

        if (isEmailEnabled) {
            Spacer(Modifier.height(12.dp))

            if (emailAccounts.isEmpty()) {
                Text(
                    text = stringResource(Res.string.settings_email_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                if (pendingCount > 0) {
                    Text(
                        text = stringResource(Res.string.settings_email_queued, pendingCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                val emailPresets = listOf(0, 5, 15, 30, 60)
                val neverLabel = stringResource(Res.string.settings_email_poll_never)
                val initialEmailPos = emailPresets.indexOf(pollIntervalMinutes)
                    .takeIf { it >= 0 }?.toFloat() ?: 0f
                var emailSliderValue by remember(pollIntervalMinutes) {
                    mutableStateOf(initialEmailPos)
                }
                val currentEmailMinutes = emailPresets[emailSliderValue.roundToInt()]
                val emailDisplay = if (currentEmailMinutes == 0) neverLabel else "${currentEmailMinutes}m"

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.settings_email_poll_interval, currentEmailMinutes),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = emailDisplay,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                KaiSlider(
                    value = emailSliderValue,
                    onValueChange = { emailSliderValue = it },
                    onValueChangeFinished = {
                        onChangePollInterval(emailPresets[emailSliderValue.roundToInt()])
                    },
                    valueRange = 0f..(emailPresets.size - 1).toFloat(),
                    steps = emailPresets.size - 2,
                )

                Spacer(Modifier.height(12.dp))

                val nowMs = remember(syncStates) { Clock.System.now().toEpochMilliseconds() }
                for (account in emailAccounts) {
                    SettingsListItem(
                        title = account.email,
                        subtitle = "${account.imapHost}:${account.imapPort}",
                        onDelete = { onRemoveAccount(account.id) },
                        deleteContentDescription = stringResource(Res.string.settings_email_remove),
                        onRefresh = { onRefreshAccount(account.id) },
                        refreshContentDescription = stringResource(Res.string.settings_email_refresh),
                        isRefreshing = account.id in refreshingAccountIds,
                    )
                    val sync = syncStates[account.id]
                    if (sync != null) {
                        val failed = sync.lastError != null && sync.lastAttemptEpochMs > 0
                        val timestampMs = if (failed) sync.lastAttemptEpochMs else sync.lastSyncEpochMs
                        if (timestampMs > 0) {
                            val relative = formatPollRelative(nowMs - timestampMs)
                            val text = if (failed) {
                                stringResource(Res.string.settings_email_poll_failed, relative)
                            } else {
                                stringResource(Res.string.settings_email_last_poll, relative)
                            }
                            Text(
                                text = text,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 12.dp, top = 4.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
internal fun SmsSection(
    isSmsEnabled: Boolean,
    permissionGranted: Boolean,
    pollIntervalMinutes: Int,
    pendingCount: Int,
    syncState: SmsSyncState,
    isRefreshing: Boolean,
    isSmsSendEnabled: Boolean,
    sendPermissionGranted: Boolean,
    onToggleSms: (Boolean) -> Unit,
    onChangePollInterval: (Int) -> Unit,
    onRefresh: () -> Unit,
    onToggleSmsSend: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ToggleableHeadline(
            title = stringResource(Res.string.settings_sms_read_label),
            description = stringResource(Res.string.settings_sms_description),
            checked = isSmsEnabled,
            onCheckedChange = onToggleSms,
        )

        if (isSmsEnabled) {
            Spacer(Modifier.height(12.dp))

            if (!permissionGranted) {
                PermissionRequiredRow(
                    message = stringResource(Res.string.settings_sms_permission_required),
                    buttonLabel = stringResource(Res.string.settings_sms_permission_button),
                    onGrant = { onToggleSms(true) },
                )
            } else {
                if (pendingCount > 0) {
                    Text(
                        text = stringResource(Res.string.settings_sms_queued, pendingCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                }

                val smsPresets = listOf(0, 5, 15, 30, 60)
                val neverLabel = stringResource(Res.string.settings_email_poll_never)
                val initialSmsPos = smsPresets.indexOf(pollIntervalMinutes)
                    .takeIf { it >= 0 }?.toFloat() ?: 0f
                var smsSliderValue by remember(pollIntervalMinutes) {
                    mutableStateOf(initialSmsPos)
                }
                val currentSmsMinutes = smsPresets[smsSliderValue.roundToInt()]
                val smsDisplay = if (currentSmsMinutes == 0) neverLabel else "${currentSmsMinutes}m"

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.settings_sms_poll_interval, currentSmsMinutes),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = smsDisplay,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                KaiSlider(
                    value = smsSliderValue,
                    onValueChange = { smsSliderValue = it },
                    onValueChangeFinished = {
                        onChangePollInterval(smsPresets[smsSliderValue.roundToInt()])
                    },
                    valueRange = 0f..(smsPresets.size - 1).toFloat(),
                    steps = smsPresets.size - 2,
                )

                Spacer(Modifier.height(8.dp))

                val nowMs = remember(syncState) { Clock.System.now().toEpochMilliseconds() }
                val failed = syncState.lastError != null && syncState.lastAttemptEpochMs > 0
                val timestampMs = if (failed) syncState.lastAttemptEpochMs else syncState.lastSyncEpochMs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (timestampMs > 0) {
                        val relative = formatPollRelative(nowMs - timestampMs)
                        val text = if (failed) {
                            stringResource(Res.string.settings_sms_poll_failed, relative)
                        } else {
                            stringResource(Res.string.settings_sms_last_poll, relative)
                        }
                        Text(
                            text = text,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }
                    RefreshIconButton(
                        onClick = onRefresh,
                        isRefreshing = isRefreshing,
                        contentDescription = stringResource(Res.string.settings_sms_refresh),
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        ToggleableHeadline(
            title = stringResource(Res.string.settings_sms_send_label),
            description = stringResource(Res.string.settings_sms_send_description),
            checked = isSmsSendEnabled,
            onCheckedChange = onToggleSmsSend,
        )

        if (isSmsSendEnabled && !sendPermissionGranted) {
            Spacer(Modifier.height(8.dp))
            PermissionRequiredRow(
                message = stringResource(Res.string.settings_sms_send_permission_required),
                buttonLabel = stringResource(Res.string.settings_sms_permission_button),
                onGrant = { onToggleSmsSend(true) },
            )
        }
    }
}

@Composable
private fun PermissionRequiredRow(
    message: String,
    buttonLabel: String,
    onGrant: () -> Unit,
) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = onGrant) {
        Text(buttonLabel)
    }
}

@Composable
internal fun NotificationsSection(
    isEnabled: Boolean,
    accessGranted: Boolean,
    listenerBound: Boolean,
    pendingCount: Int,
    onToggle: (Boolean) -> Unit,
    onOpenAccessSettings: () -> Unit,
    onClearPending: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ToggleableHeadline(
            title = stringResource(Res.string.settings_notifications_label),
            description = stringResource(Res.string.settings_notifications_description),
            checked = isEnabled,
            onCheckedChange = onToggle,
        )

        if (isEnabled) {
            Spacer(Modifier.height(12.dp))

            if (!accessGranted) {
                PermissionRequiredRow(
                    message = stringResource(Res.string.settings_notifications_access_required),
                    buttonLabel = stringResource(Res.string.settings_notifications_access_button),
                    onGrant = onOpenAccessSettings,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(
                            if (listenerBound) {
                                Res.string.settings_notifications_listener_bound
                            } else {
                                Res.string.settings_notifications_listener_disconnected
                            },
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (listenerBound) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                    OutlinedButton(onClick = onOpenAccessSettings) {
                        Text(stringResource(Res.string.settings_notifications_manage_apps))
                    }
                }

                if (pendingCount > 0) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(Res.string.settings_notifications_queued, pendingCount),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        TextButton(onClick = onClearPending) {
                            Text(stringResource(Res.string.settings_notifications_clear_queue))
                        }
                    }
                }
            }
        }
    }
}

private fun formatPollRelative(diffMs: Long): String {
    val clamped = diffMs.coerceAtLeast(0L)
    val minutes = clamped / 60_000L
    val hours = minutes / 60L
    val days = hours / 24L
    return when {
        minutes < 1L -> "just now"
        minutes < 60L -> "${minutes}m ago"
        hours < 24L -> "${hours}h ago"
        else -> "${days}d ago"
    }
}

private fun formatHeartbeatTime(epochMs: Long): String {
    val instant = Instant.fromEpochMilliseconds(epochMs)
    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${local.day} ${local.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} ${local.hour}:${local.minute.toString().padStart(2, '0')}"
}

internal fun describeCron(cron: String): String {
    val parts = cron.trim().split("\\s+".toRegex())
    if (parts.size != 5) return cron

    val (minute, hour, dayOfMonth, month, dayOfWeek) = parts
    val isEveryDay = dayOfMonth == "*" && month == "*" && dayOfWeek == "*"
    val isEveryWeekday = dayOfMonth == "*" && month == "*" && dayOfWeek != "*"
    val isEveryMonth = dayOfMonth != "*" && month == "*" && dayOfWeek == "*"

    val timeStr = formatCronTime(hour, minute) ?: return cron

    return when {
        isEveryDay -> "Daily at $timeStr"

        isEveryWeekday -> {
            val days = dayOfWeek.split(",").mapNotNull { dayName(it.trim()) }
            if (days.isNotEmpty()) "Every ${days.joinToString(", ")} at $timeStr" else cron
        }

        isEveryMonth -> "Monthly on day $dayOfMonth at $timeStr"

        else -> cron
    }
}

private fun formatCronTime(hour: String, minute: String): String? {
    val h = hour.toIntOrNull() ?: return null
    val m = minute.toIntOrNull() ?: return null
    return "$h:${m.toString().padStart(2, '0')}"
}

private fun dayName(day: String): String? = when (day) {
    "0", "7" -> "Sun"
    "1" -> "Mon"
    "2" -> "Tue"
    "3" -> "Wed"
    "4" -> "Thu"
    "5" -> "Fri"
    "6" -> "Sat"
    "MON" -> "Mon"
    "TUE" -> "Tue"
    "WED" -> "Wed"
    "THU" -> "Thu"
    "FRI" -> "Fri"
    "SAT" -> "Sat"
    "SUN" -> "Sun"
    else -> null
}
