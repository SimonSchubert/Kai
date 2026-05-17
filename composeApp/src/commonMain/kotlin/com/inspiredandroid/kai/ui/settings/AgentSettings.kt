@file:OptIn(ExperimentalMaterial3Api::class)

package com.inspiredandroid.kai.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inspiredandroid.kai.data.HeartbeatLogEntry
import com.inspiredandroid.kai.data.MemoryEntry
import com.inspiredandroid.kai.data.ScheduledTask
import com.inspiredandroid.kai.data.TaskTrigger
import com.inspiredandroid.kai.ui.KaiOutlinedTextField
import com.inspiredandroid.kai.ui.components.SettingsListItem
import com.inspiredandroid.kai.ui.handCursor
import com.inspiredandroid.kai.ui.icons.Replay
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.default_soul
import kai.composeapp.generated.resources.settings_heartbeat_recent
import kai.composeapp.generated.resources.settings_memories
import kai.composeapp.generated.resources.settings_memories_all_title
import kai.composeapp.generated.resources.settings_memories_delete
import kai.composeapp.generated.resources.settings_memories_description
import kai.composeapp.generated.resources.settings_memories_edit_cancel
import kai.composeapp.generated.resources.settings_memories_edit_save
import kai.composeapp.generated.resources.settings_memories_edit_title
import kai.composeapp.generated.resources.settings_memories_show_all
import kai.composeapp.generated.resources.settings_scheduled_tasks
import kai.composeapp.generated.resources.settings_scheduled_tasks_cancel
import kai.composeapp.generated.resources.settings_scheduled_tasks_description
import kai.composeapp.generated.resources.settings_soul
import kai.composeapp.generated.resources.settings_soul_description
import kai.composeapp.generated.resources.settings_soul_reset
import kai.composeapp.generated.resources.settings_soul_reset_cancel
import kai.composeapp.generated.resources.settings_soul_reset_confirm
import kai.composeapp.generated.resources.settings_soul_save
import kai.composeapp.generated.resources.settings_task_details_consecutive_failures
import kai.composeapp.generated.resources.settings_task_details_created
import kai.composeapp.generated.resources.settings_task_details_last_result
import kai.composeapp.generated.resources.settings_task_details_next_run
import kai.composeapp.generated.resources.settings_task_details_no_heartbeat_runs
import kai.composeapp.generated.resources.settings_task_details_no_runs
import kai.composeapp.generated.resources.settings_task_details_on_every_heartbeat
import kai.composeapp.generated.resources.settings_task_details_schedule
import kai.composeapp.generated.resources.settings_task_details_scheduled_for
import kai.composeapp.generated.resources.settings_task_details_status
import kai.composeapp.generated.resources.settings_task_details_trigger
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

@Composable
internal fun AgentContent(uiState: SettingsUiState, actions: SettingsActions) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val useStaggered = maxWidth >= 600.dp
        if (useStaggered) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    SettingsCard {
                        SoulEditor(
                            soulText = uiState.soulText,
                            onSaveSoul = actions.onSaveSoul,
                        )
                    }
                    SettingsCard {
                        ScheduledTaskList(
                            tasks = uiState.scheduledTasks,
                            heartbeatLog = uiState.heartbeatLog,
                            onCancelTask = actions.onCancelTask,
                            isSchedulingEnabled = uiState.isSchedulingEnabled,
                            onToggleScheduling = actions.onToggleScheduling,
                        )
                    }
                    SettingsCard {
                        MemoryList(
                            memories = uiState.memories,
                            onDeleteMemory = actions.onDeleteMemory,
                            onUpdateMemory = actions.onUpdateMemory,
                            isMemoryEnabled = uiState.isMemoryEnabled,
                            onToggleMemory = actions.onToggleMemory,
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    SettingsCard {
                        HeartbeatSection(
                            isHeartbeatEnabled = uiState.isHeartbeatEnabled,
                            heartbeatIntervalMinutes = uiState.heartbeatIntervalMinutes,
                            activeHoursStart = uiState.heartbeatActiveHoursStart,
                            activeHoursEnd = uiState.heartbeatActiveHoursEnd,
                            heartbeatPrompt = uiState.heartbeatPrompt,
                            heartbeatLog = uiState.heartbeatLog,
                            heartbeatServiceEntries = uiState.heartbeatServiceEntries,
                            heartbeatSelectedInstanceId = uiState.heartbeatSelectedInstanceId,
                            isRefreshing = uiState.isRefreshingHeartbeat,
                            onToggleHeartbeat = actions.onToggleHeartbeat,
                            onChangeInterval = actions.onChangeHeartbeatInterval,
                            onChangeActiveHours = actions.onChangeHeartbeatActiveHours,
                            onSaveHeartbeatPrompt = actions.onSaveHeartbeatPrompt,
                            onChangeHeartbeatService = actions.onChangeHeartbeatService,
                            onRefresh = actions.onRefreshHeartbeat,
                        )
                    }
                    if (uiState.showEmailToggle) {
                        SettingsCard {
                            EmailSection(
                                isEmailEnabled = uiState.isEmailEnabled,
                                emailAccounts = uiState.emailAccounts,
                                pollIntervalMinutes = uiState.emailPollIntervalMinutes,
                                pendingCount = uiState.emailPendingCount,
                                syncStates = uiState.emailSyncStates,
                                refreshingAccountIds = uiState.refreshingEmailAccountIds,
                                onToggleEmail = actions.onToggleEmail,
                                onRemoveAccount = actions.onRemoveEmailAccount,
                                onChangePollInterval = actions.onChangeEmailPollInterval,
                                onRefreshAccount = actions.onRefreshEmailAccount,
                            )
                        }
                    }
                    if (uiState.showSmsSection) {
                        SettingsCard {
                            SmsSection(
                                isSmsEnabled = uiState.isSmsEnabled,
                                permissionGranted = uiState.smsPermissionGranted,
                                pollIntervalMinutes = uiState.smsPollIntervalMinutes,
                                pendingCount = uiState.smsPendingCount,
                                syncState = uiState.smsSyncState,
                                isRefreshing = uiState.isRefreshingSms,
                                isSmsSendEnabled = uiState.isSmsSendEnabled,
                                sendPermissionGranted = uiState.smsSendPermissionGranted,
                                onToggleSms = actions.onToggleSms,
                                onChangePollInterval = actions.onChangeSmsPollInterval,
                                onRefresh = actions.onRefreshSms,
                                onToggleSmsSend = actions.onToggleSmsSend,
                            )
                        }
                    }
                    if (uiState.showNotificationsSection) {
                        SettingsCard {
                            NotificationsSection(
                                isEnabled = uiState.isNotificationsEnabled,
                                accessGranted = uiState.notificationListenerAccessGranted,
                                listenerBound = uiState.notificationListenerBound,
                                pendingCount = uiState.notificationPendingCount,
                                onToggle = actions.onToggleNotifications,
                                onOpenAccessSettings = actions.onOpenNotificationListenerSettings,
                                onClearPending = actions.onClearPendingNotifications,
                            )
                        }
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SettingsCard {
                    SoulEditor(
                        soulText = uiState.soulText,
                        onSaveSoul = actions.onSaveSoul,
                    )
                }
                SettingsCard {
                    MemoryList(
                        memories = uiState.memories,
                        onDeleteMemory = actions.onDeleteMemory,
                        onUpdateMemory = actions.onUpdateMemory,
                        isMemoryEnabled = uiState.isMemoryEnabled,
                        onToggleMemory = actions.onToggleMemory,
                    )
                }
                SettingsCard {
                    ScheduledTaskList(
                        tasks = uiState.scheduledTasks,
                        heartbeatLog = uiState.heartbeatLog,
                        onCancelTask = actions.onCancelTask,
                        isSchedulingEnabled = uiState.isSchedulingEnabled,
                        onToggleScheduling = actions.onToggleScheduling,
                    )
                }
                SettingsCard {
                    HeartbeatSection(
                        isHeartbeatEnabled = uiState.isHeartbeatEnabled,
                        heartbeatIntervalMinutes = uiState.heartbeatIntervalMinutes,
                        activeHoursStart = uiState.heartbeatActiveHoursStart,
                        activeHoursEnd = uiState.heartbeatActiveHoursEnd,
                        heartbeatPrompt = uiState.heartbeatPrompt,
                        heartbeatLog = uiState.heartbeatLog,
                        heartbeatServiceEntries = uiState.heartbeatServiceEntries,
                        heartbeatSelectedInstanceId = uiState.heartbeatSelectedInstanceId,
                        isRefreshing = uiState.isRefreshingHeartbeat,
                        onToggleHeartbeat = actions.onToggleHeartbeat,
                        onChangeInterval = actions.onChangeHeartbeatInterval,
                        onChangeActiveHours = actions.onChangeHeartbeatActiveHours,
                        onSaveHeartbeatPrompt = actions.onSaveHeartbeatPrompt,
                        onChangeHeartbeatService = actions.onChangeHeartbeatService,
                        onRefresh = actions.onRefreshHeartbeat,
                    )
                }
                if (uiState.showEmailToggle) {
                    SettingsCard {
                        EmailSection(
                            isEmailEnabled = uiState.isEmailEnabled,
                            emailAccounts = uiState.emailAccounts,
                            pollIntervalMinutes = uiState.emailPollIntervalMinutes,
                            pendingCount = uiState.emailPendingCount,
                            syncStates = uiState.emailSyncStates,
                            refreshingAccountIds = uiState.refreshingEmailAccountIds,
                            onToggleEmail = actions.onToggleEmail,
                            onRemoveAccount = actions.onRemoveEmailAccount,
                            onChangePollInterval = actions.onChangeEmailPollInterval,
                            onRefreshAccount = actions.onRefreshEmailAccount,
                        )
                    }
                }
                if (uiState.showSmsSection) {
                    SettingsCard {
                        SmsSection(
                            isSmsEnabled = uiState.isSmsEnabled,
                            permissionGranted = uiState.smsPermissionGranted,
                            pollIntervalMinutes = uiState.smsPollIntervalMinutes,
                            pendingCount = uiState.smsPendingCount,
                            syncState = uiState.smsSyncState,
                            isRefreshing = uiState.isRefreshingSms,
                            isSmsSendEnabled = uiState.isSmsSendEnabled,
                            sendPermissionGranted = uiState.smsSendPermissionGranted,
                            onToggleSms = actions.onToggleSms,
                            onChangePollInterval = actions.onChangeSmsPollInterval,
                            onRefresh = actions.onRefreshSms,
                            onToggleSmsSend = actions.onToggleSmsSend,
                        )
                    }
                }
                if (uiState.showNotificationsSection) {
                    SettingsCard {
                        NotificationsSection(
                            isEnabled = uiState.isNotificationsEnabled,
                            accessGranted = uiState.notificationListenerAccessGranted,
                            listenerBound = uiState.notificationListenerBound,
                            pendingCount = uiState.notificationPendingCount,
                            onToggle = actions.onToggleNotifications,
                            onOpenAccessSettings = actions.onOpenNotificationListenerSettings,
                            onClearPending = actions.onClearPendingNotifications,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SoulEditor(
    soulText: String,
    onSaveSoul: (String) -> Unit,
) {
    val localizedDefault = stringResource(Res.string.default_soul)
    val displayText = soulText.ifEmpty { localizedDefault }
    var editedText by remember(displayText) { mutableStateOf(displayText) }
    val hasChanges = editedText != displayText
    val maxChars = 4000

    var showResetDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.settings_soul),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            if (soulText.isNotEmpty()) {
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
        }
        Text(
            text = stringResource(Res.string.settings_soul_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        KaiOutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = editedText,
            onValueChange = { if (it.length <= maxChars) editedText = it },
            minLines = 8,
            maxLines = 8,
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
                modifier = Modifier.align(CenterHorizontally).handCursor(),
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

@Composable
private fun MemoryList(
    memories: ImmutableList<MemoryEntry>,
    onDeleteMemory: (String) -> Unit,
    onUpdateMemory: (String, String) -> Unit,
    isMemoryEnabled: Boolean,
    onToggleMemory: (Boolean) -> Unit,
) {
    var showAllDialog by remember { mutableStateOf(false) }
    var editingMemory by remember { mutableStateOf<MemoryEntry?>(null) }

    val sortedMemories = remember(memories) {
        memories.sortedByDescending { it.updatedAt }.toImmutableList()
    }
    val previewMemories = remember(sortedMemories) { sortedMemories.take(5).toImmutableList() }

    Column(modifier = Modifier.fillMaxWidth()) {
        ToggleableHeadline(
            title = stringResource(Res.string.settings_memories),
            description = stringResource(Res.string.settings_memories_description),
            checked = isMemoryEnabled,
            onCheckedChange = onToggleMemory,
        )
        Spacer(Modifier.height(12.dp))

        if (isMemoryEnabled) {
            previewMemories.forEach { memory ->
                SettingsListItem(
                    title = memory.key,
                    subtitle = memory.content,
                    onDelete = { onDeleteMemory(memory.key) },
                    deleteContentDescription = stringResource(Res.string.settings_memories_delete),
                    subtitleMaxLines = 3,
                    onClick = { editingMemory = memory },
                )
                Spacer(Modifier.height(8.dp))
            }
            if (sortedMemories.size > previewMemories.size) {
                OutlinedButton(
                    onClick = { showAllDialog = true },
                    modifier = Modifier.align(CenterHorizontally).handCursor(),
                ) {
                    Text(stringResource(Res.string.settings_memories_show_all, sortedMemories.size))
                }
            }
        }
    }

    if (showAllDialog) {
        AllMemoriesSheet(
            memories = sortedMemories,
            onDismiss = { showAllDialog = false },
            onDeleteMemory = onDeleteMemory,
            onEditMemory = { editingMemory = it },
        )
    }

    editingMemory?.let { memory ->
        EditMemorySheet(
            memory = memory,
            onDismiss = { editingMemory = null },
            onSave = { newContent ->
                onUpdateMemory(memory.key, newContent)
                editingMemory = null
            },
        )
    }
}

@Composable
private fun AllMemoriesSheet(
    memories: ImmutableList<MemoryEntry>,
    onDismiss: () -> Unit,
    onDeleteMemory: (String) -> Unit,
    onEditMemory: (MemoryEntry) -> Unit,
) {
    val deleteContentDescription = stringResource(Res.string.settings_memories_delete)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(Res.string.settings_memories_all_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(12.dp))
            memories.forEach { memory ->
                SettingsListItem(
                    title = memory.key,
                    subtitle = memory.content,
                    onDelete = { onDeleteMemory(memory.key) },
                    deleteContentDescription = deleteContentDescription,
                    subtitleMaxLines = 3,
                    onClick = { onEditMemory(memory) },
                )
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun EditMemorySheet(
    memory: MemoryEntry,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var content by remember(memory.key) { mutableStateOf(memory.content) }
    val hasChanges = content != memory.content && content.isNotBlank()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(Res.string.settings_memories_edit_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = memory.key,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            KaiOutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = content,
                onValueChange = { content = it },
                minLines = 4,
                maxLines = 10,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.handCursor(),
                ) {
                    Text(stringResource(Res.string.settings_memories_edit_cancel))
                }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = { onSave(content.trim()) },
                    enabled = hasChanges,
                    modifier = Modifier.handCursor(),
                ) {
                    Text(stringResource(Res.string.settings_memories_edit_save))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ScheduledTaskList(
    tasks: ImmutableList<ScheduledTask>,
    heartbeatLog: ImmutableList<HeartbeatLogEntry>,
    onCancelTask: (String) -> Unit,
    isSchedulingEnabled: Boolean,
    onToggleScheduling: (Boolean) -> Unit,
) {
    var selectedTaskId by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        ToggleableHeadline(
            title = stringResource(Res.string.settings_scheduled_tasks),
            description = stringResource(Res.string.settings_scheduled_tasks_description),
            checked = isSchedulingEnabled,
            onCheckedChange = onToggleScheduling,
        )
        Spacer(Modifier.height(12.dp))

        val onEveryHeartbeat = stringResource(Res.string.settings_task_details_on_every_heartbeat)
        if (isSchedulingEnabled && tasks.isNotEmpty()) {
            tasks.forEach { task ->
                val subtitle = when (task.trigger) {
                    TaskTrigger.HEARTBEAT -> "${task.status} - $onEveryHeartbeat"

                    TaskTrigger.CRON -> "${task.status} - ${task.cron?.let { describeCron(it) } ?: "cron"}"

                    TaskTrigger.TIME -> {
                        val instant = Instant.fromEpochMilliseconds(task.scheduledAtEpochMs)
                        val zone = TimeZone.currentSystemDefault()
                        val scheduledTime = instant.toLocalDateTime(zone)
                        val offset = zone.offsetAt(instant)
                        "${task.status} - $scheduledTime $offset"
                    }
                }
                SettingsListItem(
                    title = task.description,
                    subtitle = subtitle,
                    onClick = { selectedTaskId = task.id },
                    onDelete = { onCancelTask(task.id) },
                    deleteContentDescription = stringResource(Res.string.settings_scheduled_tasks_cancel),
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    val selectedTask = selectedTaskId?.let { id -> tasks.firstOrNull { it.id == id } }
    if (selectedTask != null) {
        TaskDetailsSheet(
            task = selectedTask,
            heartbeatLog = heartbeatLog,
            onDismiss = { selectedTaskId = null },
        )
    }
}

@Composable
private fun TaskDetailsSheet(
    task: ScheduledTask,
    heartbeatLog: ImmutableList<HeartbeatLogEntry>,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = task.description,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(12.dp))

            TaskDetailRow(
                label = stringResource(Res.string.settings_task_details_trigger),
                value = task.trigger.name,
            )
            TaskDetailRow(
                label = stringResource(Res.string.settings_task_details_status),
                value = task.status.name,
            )
            when (task.trigger) {
                TaskTrigger.TIME -> TaskDetailRow(
                    label = stringResource(Res.string.settings_task_details_scheduled_for),
                    value = formatTaskInstant(task.scheduledAtEpochMs),
                )

                TaskTrigger.CRON -> {
                    TaskDetailRow(
                        label = stringResource(Res.string.settings_task_details_schedule),
                        value = task.cron?.let { describeCron(it) } ?: "cron",
                    )
                    TaskDetailRow(
                        label = stringResource(Res.string.settings_task_details_next_run),
                        value = formatTaskInstant(task.scheduledAtEpochMs),
                    )
                }

                TaskTrigger.HEARTBEAT -> TaskDetailRow(
                    label = stringResource(Res.string.settings_task_details_schedule),
                    value = stringResource(Res.string.settings_task_details_on_every_heartbeat),
                )
            }
            TaskDetailRow(
                label = stringResource(Res.string.settings_task_details_created),
                value = formatTaskInstant(task.createdAtEpochMs),
            )
            if (task.consecutiveFailures > 0) {
                TaskDetailRow(
                    label = stringResource(Res.string.settings_task_details_consecutive_failures),
                    value = task.consecutiveFailures.toString(),
                )
            }
            // The scheduler stores its retry/backoff phrasing in `lastResult` ("Failed at ...:
            // ... (retry after 120s backoff)"). Surface it so the user can see what the
            // scheduler is going to do next, not just what already happened.
            task.lastResult?.takeIf { it.isNotBlank() }?.let { result ->
                TaskDetailRow(
                    label = stringResource(Res.string.settings_task_details_last_result),
                    value = result,
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(Res.string.settings_heartbeat_recent),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(4.dp))

            if (task.trigger == TaskTrigger.HEARTBEAT) {
                // Heartbeat additions don't carry their own log — they fire as part of every
                // heartbeat run, so the heartbeat-wide log is the right surface.
                if (heartbeatLog.isEmpty()) {
                    EmptyLogText(stringResource(Res.string.settings_task_details_no_heartbeat_runs))
                } else {
                    heartbeatLog.forEach { entry ->
                        ExecutionLogRow(
                            success = entry.success,
                            timestampEpochMs = entry.timestampEpochMs,
                            message = entry.error,
                        )
                    }
                }
            } else {
                if (task.recentExecutions.isEmpty()) {
                    EmptyLogText(stringResource(Res.string.settings_task_details_no_runs))
                } else {
                    task.recentExecutions.forEach { entry ->
                        ExecutionLogRow(
                            success = entry.success,
                            timestampEpochMs = entry.timestampEpochMs,
                            message = entry.message,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TaskDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(140.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun ExecutionLogRow(success: Boolean, timestampEpochMs: Long, message: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = if (success) "OK" else "FAIL",
            style = MaterialTheme.typography.labelSmall,
            color = if (success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.width(36.dp),
        )
        Column {
            Text(
                text = formatTaskInstant(timestampEpochMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!message.isNullOrBlank()) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (success) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EmptyLogText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun formatTaskInstant(epochMs: Long): String {
    if (epochMs <= 0L) return "—"
    val instant = Instant.fromEpochMilliseconds(epochMs)
    val zone = TimeZone.currentSystemDefault()
    val local = instant.toLocalDateTime(zone)
    val month = local.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    val minute = local.minute.toString().padStart(2, '0')
    return "${local.day} $month ${local.year} ${local.hour}:$minute"
}
