@file:OptIn(ExperimentalMaterial3Api::class)

package com.inspiredandroid.kai.ui.tasks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inspiredandroid.kai.BackIcon
import com.inspiredandroid.kai.data.ScheduledTask
import com.inspiredandroid.kai.data.TaskStatus
import com.inspiredandroid.kai.data.describeCron
import com.inspiredandroid.kai.ui.KaiOutlinedTextField
import com.inspiredandroid.kai.ui.handCursor
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun TasksScreen(
    viewModel: TasksViewModel,
    onNavigateBack: () -> Unit,
    navigationTabBar: (@Composable () -> Unit)? = null,
) {
    val uiState by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Handle pending deletion snackbar
    LaunchedEffect(uiState.pendingDeletionId) {
        if (uiState.pendingDeletionId != null) {
            val result = snackbarHostState.showSnackbar(
                message = "Task cancelled",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoCancel()
            }
        }
    }

    DisposableEffect(Unit) {
        viewModel.refresh()
        onDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scheduled Tasks") },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.handCursor(),
                    ) {
                        Icon(BackIcon, contentDescription = "Back")
                    }
                },
                actions = {
                    if (navigationTabBar != null) {
                        navigationTabBar()
                    }
                },
            )
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    actionColor = MaterialTheme.colorScheme.primary,
                )
            }
        },
        modifier = Modifier.statusBarsPadding(),
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Spacer(Modifier.height(16.dp))

            // Enable toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Enable Scheduling",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Switch(
                    checked = uiState.isSchedulingEnabled,
                    onCheckedChange = viewModel::onToggleScheduling,
                )
            }

            Spacer(Modifier.height(16.dp))

            if (uiState.isSchedulingEnabled) {
                // Search
                var searchFocused by remember { mutableStateOf(false) }
                KaiOutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = viewModel::onSearchQueryChange,
                    label = { Text("Search tasks...") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(
                            onClick = { viewModel.onSearchQueryChange("") },
                            modifier = Modifier.handCursor()
                                .alpha(if (searchFocused && uiState.searchQuery.isNotEmpty()) 1f else 0f),
                            enabled = uiState.searchQuery.isNotEmpty(),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().onFocusChanged { searchFocused = it.isFocused },
                )

                Spacer(Modifier.height(12.dp))

                // Filters
                TaskFilters(
                    selectedStatus = uiState.selectedStatus,
                    sortBy = uiState.sortBy,
                    onSelectStatus = viewModel::onSelectStatus,
                    onSortBy = viewModel::onSortBy,
                )

                Spacer(Modifier.height(16.dp))

                // Task count
                Text(
                    text = "${uiState.filteredTasks.size} tasks",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.alpha(0.6f),
                )

                Spacer(Modifier.height(8.dp))

                // List
                if (uiState.filteredTasks.isEmpty()) {
                    EmptyState(message = "No tasks found")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(
                            items = uiState.filteredTasks,
                            key = { it.id },
                        ) { task ->
                            TaskCard(
                                task = task,
                                isPendingDelete = uiState.pendingDeletionId == task.id,
                                onCancel = { viewModel.onCancelTask(task.id) },
                            )
                        }
                    }
                }
            } else {
                EmptyState(message = "Task scheduling is disabled. Enable to view and manage tasks.")
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskFilters(
    selectedStatus: TaskStatus?,
    sortBy: TaskSortOption,
    onSelectStatus: (TaskStatus?) -> Unit,
    onSortBy: (TaskSortOption) -> Unit,
) {
    Column {
        // Status filters
        Text(
            text = "Status",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.alpha(0.6f),
        )
        Spacer(Modifier.height(4.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FilterChip(
                selected = selectedStatus == null,
                onClick = { onSelectStatus(null) },
                label = { Text("All") },
            )
            TaskStatus.entries.forEach { status ->
                FilterChip(
                    selected = selectedStatus == status,
                    onClick = { onSelectStatus(status) },
                    label = { Text(status.name.lowercase().replaceFirstChar { it.uppercase() }) },
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Sort options
        Text(
            text = "Sort by",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.alpha(0.6f),
        )
        Spacer(Modifier.height(4.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TaskSortOption.entries.forEach { option ->
                val label = when (option) {
                    TaskSortOption.NEXT_EXECUTION -> "Next execution"
                    TaskSortOption.OLDEST -> "Oldest first"
                    TaskSortOption.NEWEST -> "Newest first"
                }
                FilterChip(
                    selected = sortBy == option,
                    onClick = { onSortBy(option) },
                    label = { Text(label) },
                )
            }
        }
    }
}

@Composable
private fun TaskCard(
    task: ScheduledTask,
    isPendingDelete: Boolean,
    onCancel: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // Status badge
                    StatusBadge(status = task.status)

                    Spacer(Modifier.height(4.dp))

                    // Description
                    Text(
                        text = task.description,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    // Schedule info
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (task.cron != null) {
                            Text(
                                text = describeCron(task.cron),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.alpha(0.6f),
                            )
                        } else {
                            val scheduled = Instant.fromEpochMilliseconds(task.scheduledAtEpochMs)
                                .toLocalDateTime(TimeZone.currentSystemDefault())
                            Text(
                                text = "Scheduled: ${scheduled.date} ${scheduled.time}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.alpha(0.6f),
                            )
                        }
                    }
                }

                // Actions
                Row {
                    if (!isPendingDelete) {
                        IconButton(
                            onClick = onCancel,
                            modifier = Modifier.handCursor(),
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Cancel",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    IconButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.handCursor(),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = if (expanded) "Collapse" else "Expand",
                            modifier = Modifier.rotate(rotation),
                        )
                    }
                }
            }

            // Expanded content
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))

                    // Full prompt
                    Text(
                        text = "Prompt:",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.alpha(0.6f),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = task.prompt,
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    Spacer(Modifier.height(12.dp))

                    // Timestamps
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        val created = Instant.fromEpochMilliseconds(task.createdAtEpochMs)
                            .toLocalDateTime(TimeZone.currentSystemDefault())
                        Text(
                            text = "Created: ${created.date} ${created.time}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.alpha(0.6f),
                        )
                        if (task.cron == null) {
                            val scheduled = Instant.fromEpochMilliseconds(task.scheduledAtEpochMs)
                                .toLocalDateTime(TimeZone.currentSystemDefault())
                            Text(
                                text = "Scheduled: ${scheduled.date} ${scheduled.time}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.alpha(0.6f),
                            )
                        }
                    }

                    // Last result if available
                    task.lastResult?.let { result ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Last result:",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.alpha(0.6f),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = result,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    // Failures warning
                    if (task.consecutiveFailures > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Consecutive failures: ${task.consecutiveFailures}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: TaskStatus) {
    val (color, label) = when (status) {
        TaskStatus.PENDING -> MaterialTheme.colorScheme.primary to "Pending"
        TaskStatus.COMPLETED -> MaterialTheme.colorScheme.tertiary to "Completed"
    }

    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .background(
                color = color.copy(alpha = 0.1f),
                shape = RoundedCornerShape(4.dp),
            )
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.alpha(0.6f),
        )
    }
}
