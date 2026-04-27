package com.inspiredandroid.kai.ui.sandbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inspiredandroid.kai.ui.handCursor
import com.inspiredandroid.kai.ui.kaiAdaptiveCardBorder
import com.inspiredandroid.kai.ui.kaiAdaptiveCardColors
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.sandbox_files_dialog_cancel
import kai.composeapp.generated.resources.sandbox_packages_action_clear_search
import kai.composeapp.generated.resources.sandbox_packages_action_install
import kai.composeapp.generated.resources.sandbox_packages_action_uninstall
import kai.composeapp.generated.resources.sandbox_packages_action_upgrade
import kai.composeapp.generated.resources.sandbox_packages_empty_installed
import kai.composeapp.generated.resources.sandbox_packages_empty_results
import kai.composeapp.generated.resources.sandbox_packages_search_hint
import kai.composeapp.generated.resources.sandbox_packages_uninstall_confirm
import kai.composeapp.generated.resources.sandbox_packages_uninstall_message
import kai.composeapp.generated.resources.sandbox_packages_uninstall_title
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

// Fixed width prevents the row reflowing when the action label toggles
// between Install and Uninstall during a mutation.
private val ActionSlotWidth = 96.dp
private val ActionSlotHeight = 36.dp

@Composable
fun SandboxPackagesContent(
    modifier: Modifier = Modifier,
    viewModel: SandboxPackagesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.start()
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.snackbarMessage) {
        val msg = state.snackbarMessage ?: return@LaunchedEffect
        val text = msg.arg?.let { getString(msg.resource, it) } ?: getString(msg.resource)
        snackbarHostState.showSnackbar(text)
        viewModel.consumeSnackbar()
    }

    Box(modifier = modifier) {
        Column(Modifier.fillMaxSize()) {
            SearchRow(
                query = state.searchQuery,
                onQueryChange = viewModel::updateSearchQuery,
            )
            UpgradeRow(
                upgrading = state.upgrading,
                onUpgrade = viewModel::upgradePackages,
            )
            val isSearching = state.searchQuery.isNotBlank()
            PackagesList(
                entries = if (isSearching) state.searchResults else state.installed,
                installedNames = state.installedNames,
                mutating = state.mutating,
                isLoading = if (isSearching) state.searching else state.loadingInstalled,
                isSearching = isSearching,
                onInstall = { viewModel.install(it) },
                onUninstall = { viewModel.requestUninstall(it) },
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        ) { Snackbar(snackbarData = it) }
    }

    state.pendingUninstall?.let { pkg ->
        UninstallConfirmDialog(
            name = pkg.name,
            onConfirm = viewModel::confirmUninstall,
            onDismiss = viewModel::cancelUninstall,
        )
    }
}

@Composable
private fun SearchRow(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        singleLine = true,
        placeholder = { Text(stringResource(Res.string.sandbox_packages_search_hint)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.handCursor(),
                ) {
                    Icon(
                        Icons.Filled.Clear,
                        contentDescription = stringResource(Res.string.sandbox_packages_action_clear_search),
                    )
                }
            }
        },
        shape = RoundedCornerShape(8.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    )
}

@Composable
private fun UpgradeRow(
    upgrading: Boolean,
    onUpgrade: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = onUpgrade,
            enabled = !upgrading,
            modifier = Modifier.handCursor(),
        ) {
            if (upgrading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(stringResource(Res.string.sandbox_packages_action_upgrade))
        }
    }
}

@Composable
private fun PackagesList(
    entries: List<PackageEntry>,
    installedNames: Set<String>,
    mutating: Set<String>,
    isLoading: Boolean,
    isSearching: Boolean,
    onInstall: (PackageEntry) -> Unit,
    onUninstall: (PackageEntry) -> Unit,
) {
    if (isLoading && entries.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (entries.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            val emptyRes = if (isSearching) {
                Res.string.sandbox_packages_empty_results
            } else {
                Res.string.sandbox_packages_empty_installed
            }
            Text(
                text = stringResource(emptyRes),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(entries, key = { "${it.name}@${it.version}" }) { entry ->
            PackageRow(
                entry = entry,
                installed = entry.name in installedNames,
                mutating = entry.name in mutating,
                onInstall = onInstall,
                onUninstall = onUninstall,
            )
        }
    }
}

@Composable
private fun PackageRow(
    entry: PackageEntry,
    installed: Boolean,
    mutating: Boolean,
    onInstall: (PackageEntry) -> Unit,
    onUninstall: (PackageEntry) -> Unit,
) {
    Card(
        colors = kaiAdaptiveCardColors(),
        border = kaiAdaptiveCardBorder(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Inventory2,
                contentDescription = null,
                tint = if (installed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (entry.version.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = entry.version,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (!entry.description.isNullOrEmpty()) {
                    Text(
                        text = entry.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier.size(width = ActionSlotWidth, height = ActionSlotHeight),
                contentAlignment = Alignment.Center,
            ) {
                if (mutating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else if (installed) {
                    TextButton(onClick = { onUninstall(entry) }, modifier = Modifier.handCursor()) {
                        Text(stringResource(Res.string.sandbox_packages_action_uninstall))
                    }
                } else {
                    TextButton(onClick = { onInstall(entry) }, modifier = Modifier.handCursor()) {
                        Text(stringResource(Res.string.sandbox_packages_action_install))
                    }
                }
            }
        }
    }
}

@Composable
private fun UninstallConfirmDialog(
    name: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.sandbox_packages_uninstall_title, name)) },
        text = { Text(stringResource(Res.string.sandbox_packages_uninstall_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.handCursor()) {
                Text(stringResource(Res.string.sandbox_packages_uninstall_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.handCursor()) {
                Text(stringResource(Res.string.sandbox_files_dialog_cancel))
            }
        },
    )
}
