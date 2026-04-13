@file:OptIn(ExperimentalMaterial3Api::class)

package com.inspiredandroid.kai.ui.memories

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
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
import com.inspiredandroid.kai.data.MemoryCategory
import com.inspiredandroid.kai.data.MemoryEntry
import com.inspiredandroid.kai.ui.KaiOutlinedTextField
import com.inspiredandroid.kai.ui.components.SettingsListItem
import com.inspiredandroid.kai.ui.components.VerticalScrollbarForScroll
import com.inspiredandroid.kai.ui.handCursor
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun MemoriesScreen(
    viewModel: MemoriesViewModel,
    onNavigateBack: () -> Unit,
    navigationTabBar: (@Composable () -> Unit)? = null,
) {
    val uiState by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Handle pending deletion snackbar
    LaunchedEffect(uiState.pendingDeletionKey) {
        if (uiState.pendingDeletionKey != null) {
            val result = snackbarHostState.showSnackbar(
                message = "Memory deleted",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoDelete()
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
                title = { Text("Memories") },
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
                    text = "Enable Memories",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Switch(
                    checked = uiState.isMemoryEnabled,
                    onCheckedChange = viewModel::onToggleMemory,
                )
            }

            Spacer(Modifier.height(16.dp))

            if (uiState.isMemoryEnabled) {
                // Search
                var searchFocused by remember { mutableStateOf(false) }
                KaiOutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = viewModel::onSearchQueryChange,
                    label = { Text("Search memories...") },
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
                MemoryFilters(
                    selectedCategory = uiState.selectedCategory,
                    sortBy = uiState.sortBy,
                    onSelectCategory = viewModel::onSelectCategory,
                    onSortBy = viewModel::onSortBy,
                )

                Spacer(Modifier.height(16.dp))

                // Memory count
                Text(
                    text = "${uiState.filteredMemories.size} memories",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.alpha(0.6f),
                )

                Spacer(Modifier.height(8.dp))

                // List
                if (uiState.filteredMemories.isEmpty()) {
                    EmptyState(message = "No memories found")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(
                            items = uiState.filteredMemories,
                            key = { it.key },
                        ) { memory ->
                            MemoryCard(
                                memory = memory,
                                isPendingDelete = uiState.pendingDeletionKey == memory.key,
                                onDelete = { viewModel.onDeleteMemory(memory.key) },
                            )
                        }
                    }
                }
            } else {
                EmptyState(message = "Memories are disabled. Enable to view and manage memories.")
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemoryFilters(
    selectedCategory: MemoryCategory?,
    sortBy: MemorySortOption,
    onSelectCategory: (MemoryCategory?) -> Unit,
    onSortBy: (MemorySortOption) -> Unit,
) {
    Column {
        // Category filters
        Text(
            text = "Category",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.alpha(0.6f),
        )
        Spacer(Modifier.height(4.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FilterChip(
                selected = selectedCategory == null,
                onClick = { onSelectCategory(null) },
                label = { Text("All") },
            )
            MemoryCategory.entries.forEach { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { onSelectCategory(category) },
                    label = { Text(category.name.lowercase().replaceFirstChar { it.uppercase() }) },
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
            MemorySortOption.entries.forEach { option ->
                val label = when (option) {
                    MemorySortOption.NEWEST -> "Newest"
                    MemorySortOption.OLDEST -> "Oldest"
                    MemorySortOption.MOST_USED -> "Most used"
                    MemorySortOption.LEAST_USED -> "Least used"
                    MemorySortOption.LAST_MODIFIED -> "Modified"
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
private fun MemoryCard(
    memory: MemoryEntry,
    isPendingDelete: Boolean,
    onDelete: () -> Unit,
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
                    // Category badge
                    CategoryBadge(category = memory.category)

                    Spacer(Modifier.height(4.dp))

                    // Key
                    Text(
                        text = memory.key,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    // Stats row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${memory.hitCount} hits",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.alpha(0.6f),
                        )
                        val created = Instant.fromEpochMilliseconds(memory.createdAt)
                            .toLocalDateTime(TimeZone.currentSystemDefault())
                        Text(
                            text = "Created: ${created.date}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.alpha(0.6f),
                        )
                    }
                }

                // Actions
                Row {
                    if (!isPendingDelete) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.handCursor(),
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
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

                    // Full content
                    Text(
                        text = "Content:",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.alpha(0.6f),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = memory.content,
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    Spacer(Modifier.height(12.dp))

                    // Timestamps
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        val created = Instant.fromEpochMilliseconds(memory.createdAt)
                            .toLocalDateTime(TimeZone.currentSystemDefault())
                        val updated = Instant.fromEpochMilliseconds(memory.updatedAt)
                            .toLocalDateTime(TimeZone.currentSystemDefault())
                        Text(
                            text = "Created: ${created.date} ${created.time}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.alpha(0.6f),
                        )
                        Text(
                            text = "Updated: ${updated.date} ${updated.time}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.alpha(0.6f),
                        )
                    }

                    // Source if available
                    memory.source?.let { source ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Source: $source",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.alpha(0.6f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryBadge(category: MemoryCategory) {
    val (color, label) = when (category) {
        MemoryCategory.GENERAL -> MaterialTheme.colorScheme.primary to "General"
        MemoryCategory.LEARNING -> MaterialTheme.colorScheme.secondary to "Learning"
        MemoryCategory.ERROR -> MaterialTheme.colorScheme.error to "Error"
        MemoryCategory.PREFERENCE -> MaterialTheme.colorScheme.tertiary to "Preference"
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
