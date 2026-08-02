package com.inspiredandroid.kai.ui.build

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.inspiredandroid.kai.build.BuildAgent
import com.inspiredandroid.kai.build.BuildAgents
import com.inspiredandroid.kai.build.BuildSystemInfo
import com.inspiredandroid.kai.build.KaiBuildState
import com.inspiredandroid.kai.formatFileSize
import com.inspiredandroid.kai.ui.components.KaiChip
import com.inspiredandroid.kai.ui.handCursor
import com.inspiredandroid.kai.ui.settings.SettingsCard
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.kai_build_new_project_title
import kai.composeapp.generated.resources.kai_build_open_with
import kai.composeapp.generated.resources.kai_build_projects_add_agent
import kai.composeapp.generated.resources.kai_build_projects_create
import kai.composeapp.generated.resources.kai_build_projects_empty
import kai.composeapp.generated.resources.kai_build_projects_new_placeholder
import kai.composeapp.generated.resources.kai_build_projects_session_open
import kai.composeapp.generated.resources.kai_build_projects_sessions_open
import kai.composeapp.generated.resources.kai_build_session_shell
import kai.composeapp.generated.resources.kai_build_system_disk_free
import kai.composeapp.generated.resources.kai_build_system_disk_projects
import kai.composeapp.generated.resources.kai_build_system_disk_system
import kai.composeapp.generated.resources.kai_build_system_packages
import kai.composeapp.generated.resources.kai_build_system_title
import kai.composeapp.generated.resources.kai_build_uninstall
import kai.composeapp.generated.resources.kai_build_uninstall_message
import kai.composeapp.generated.resources.kai_build_uninstall_title
import kai.composeapp.generated.resources.settings_sandbox_cancel
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource

/**
 * Landing surface once Debian is ready: pick what a project opens with, open one
 * — new projects come from the plus button in the top bar. The Linux system
 * itself (size, facts, extra agents, uninstall) sits in one card below.
 */
@Composable
internal fun BuildProjectsContent(
    state: KaiBuildState,
    launchAgentId: String?,
    installedAgents: ImmutableList<BuildAgent>,
    onSelectLaunchAgent: (String?) -> Unit,
    onOpenProject: (String) -> Unit,
    onInstallAgent: (String) -> Unit,
    onUninstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showUninstall by remember { mutableStateOf(false) }
    val missingAgents = remember(state.installedAgents) {
        BuildAgents.all.filterNot { it.id in state.installedAgents }
    }
    val sessionCounts = remember(state.sessions) {
        state.sessions.groupingBy { it.project }.eachCount()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (installedAgents.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.kai_build_open_with),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    KaiChip(
                        selected = launchAgentId == null,
                        onClick = { onSelectLaunchAgent(null) },
                    ) {
                        Text(
                            text = stringResource(Res.string.kai_build_session_shell),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    installedAgents.forEach { agent ->
                        KaiChip(
                            selected = launchAgentId == agent.id,
                            onClick = { onSelectLaunchAgent(agent.id) },
                        ) {
                            Text(text = agent.title, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        if (state.projects.isEmpty()) {
            item {
                Text(
                    text = stringResource(Res.string.kai_build_projects_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(state.projects, key = { it }) { project ->
            SettingsCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onOpenProject(project) },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = project,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    // Shells left behind here: the list is the only place they can be
                    // found from, now that stepping out of a project keeps them.
                    val open = sessionCounts[project] ?: 0
                    if (open > 0) {
                        Text(
                            text = if (open == 1) {
                                stringResource(Res.string.kai_build_projects_session_open)
                            } else {
                                stringResource(Res.string.kai_build_projects_sessions_open, open)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        state.lastError?.let { error ->
            item {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        item {
            BuildSystemCard(
                info = state.systemInfo,
                missingAgents = missingAgents,
                onInstallAgent = onInstallAgent,
                onUninstall = { showUninstall = true },
            )
        }
    }

    if (showUninstall) {
        AlertDialog(
            onDismissRequest = { showUninstall = false },
            title = { Text(stringResource(Res.string.kai_build_uninstall_title)) },
            text = { Text(stringResource(Res.string.kai_build_uninstall_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUninstall = false
                        onUninstall()
                    },
                    modifier = Modifier.handCursor(),
                ) { Text(stringResource(Res.string.kai_build_uninstall)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showUninstall = false },
                    modifier = Modifier.handCursor(),
                ) { Text(stringResource(Res.string.settings_sandbox_cancel)) }
            },
        )
    }
}

/** What the Linux install is and what it costs, plus the two things you can do to it. */
@Composable
private fun BuildSystemCard(
    info: BuildSystemInfo?,
    missingAgents: List<BuildAgent>,
    onInstallAgent: (String) -> Unit,
    onUninstall: () -> Unit,
) {
    SettingsCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.kai_build_system_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (info != null) {
            Text(
                text = "${info.distribution} · ${info.architecture}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (info.packageCount > 0) {
                Text(
                    text = stringResource(Res.string.kai_build_system_packages, info.packageCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            SystemInfoRow(
                label = stringResource(Res.string.kai_build_system_disk_system),
                value = formatFileSize(info.systemBytes),
            )
            SystemInfoRow(
                label = stringResource(Res.string.kai_build_system_disk_projects),
                value = formatFileSize(info.projectsBytes),
            )
            SystemInfoRow(
                label = stringResource(Res.string.kai_build_system_disk_free),
                value = formatFileSize(info.freeBytes),
            )
        }

        if (missingAgents.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(Res.string.kai_build_projects_add_agent),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                missingAgents.forEach { agent ->
                    KaiChip(onClick = { onInstallAgent(agent.id) }) {
                        Text(text = agent.title, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        TextButton(
            onClick = onUninstall,
            modifier = Modifier.handCursor(),
        ) {
            Text(
                text = stringResource(Res.string.kai_build_uninstall),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun SystemInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Reached from the plus button in the top bar; creating opens the project right away. */
@Composable
internal fun CreateProjectDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    val create = { if (name.isNotBlank()) onCreate(name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.kai_build_new_project_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(Res.string.kai_build_projects_new_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { create() }),
            )
        },
        confirmButton = {
            TextButton(
                onClick = create,
                enabled = name.isNotBlank(),
                modifier = Modifier.handCursor(),
            ) { Text(stringResource(Res.string.kai_build_projects_create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.handCursor()) {
                Text(stringResource(Res.string.settings_sandbox_cancel))
            }
        },
    )
}
