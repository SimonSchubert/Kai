package com.kainext.duo.ui.build

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kainext.duo.KAI_BUILD_FILES
import com.kainext.duo.TerminalLine
import com.kainext.duo.build.BuildAgents
import com.kainext.duo.build.KaiBuildSessionInfo
import com.kainext.duo.build.KaiBuildStage
import com.kainext.duo.build.KaiBuildState
import com.kainext.duo.ui.sandbox.SandboxFileBrowserViewModel
import com.kainext.duo.ui.sandbox.SandboxFilesContent
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.kai_build_back_content_description
import kai.composeapp.generated.resources.kai_build_exit_content_description
import kai.composeapp.generated.resources.kai_build_files_tab
import kai.composeapp.generated.resources.kai_build_new_project_content_description
import kai.composeapp.generated.resources.kai_build_new_project_title
import kai.composeapp.generated.resources.kai_build_project_delete
import kai.composeapp.generated.resources.kai_build_project_delete_message
import kai.composeapp.generated.resources.kai_build_project_delete_title
import kai.composeapp.generated.resources.kai_build_project_rename
import kai.composeapp.generated.resources.kai_build_projects_add_agent
import kai.composeapp.generated.resources.kai_build_projects_create
import kai.composeapp.generated.resources.kai_build_projects_empty
import kai.composeapp.generated.resources.kai_build_projects_new_placeholder
import kai.composeapp.generated.resources.kai_build_projects_session_open
import kai.composeapp.generated.resources.kai_build_projects_sessions_open
import kai.composeapp.generated.resources.kai_build_session_close_content_description
import kai.composeapp.generated.resources.kai_build_session_new_content_description
import kai.composeapp.generated.resources.kai_build_session_shell
import kai.composeapp.generated.resources.kai_build_setup_agents_description
import kai.composeapp.generated.resources.kai_build_setup_agents_title
import kai.composeapp.generated.resources.kai_build_setup_cancel
import kai.composeapp.generated.resources.kai_build_setup_description
import kai.composeapp.generated.resources.kai_build_setup_install
import kai.composeapp.generated.resources.kai_build_setup_title
import kai.composeapp.generated.resources.kai_build_step_base_packages
import kai.composeapp.generated.resources.kai_build_step_configure
import kai.composeapp.generated.resources.kai_build_step_download
import kai.composeapp.generated.resources.kai_build_step_extract
import kai.composeapp.generated.resources.kai_build_step_install_agent
import kai.composeapp.generated.resources.kai_build_system_disk_free
import kai.composeapp.generated.resources.kai_build_system_disk_projects
import kai.composeapp.generated.resources.kai_build_system_disk_system
import kai.composeapp.generated.resources.kai_build_system_packages
import kai.composeapp.generated.resources.kai_build_system_title
import kai.composeapp.generated.resources.kai_build_terminal_placeholder
import kai.composeapp.generated.resources.kai_build_terminal_run_content_description
import kai.composeapp.generated.resources.kai_build_title
import kai.composeapp.generated.resources.kai_build_uninstall
import kai.composeapp.generated.resources.kai_build_uninstall_message
import kai.composeapp.generated.resources.kai_build_uninstall_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** The two panes a project shows: the live shell and the project's files. */
private enum class ProjectPane { Terminal, Files }

/** Where a project's sessions live inside the guest. */
private fun projectsPath(project: String): String = "/root/projects/$project"

/**
 * Kai Build: a Debian system under proot, plus project folders with shells and
 * coding agents in them.
 *
 * Three surfaces share one state: setup (install Debian and pick agents),
 * projects (the folder list and the system card), and a project (its sessions
 * and files). Which one shows is derived from [KaiBuildState] rather than kept
 * here, so returning to a project from elsewhere lands where the user left it.
 */
@Composable
fun KaiBuildScreen(
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: KaiBuildViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // The installed marker, project folders and agent binaries all live on disk;
    // a re-entry after an install elsewhere must not render stale facts.
    LaunchedEffect(Unit) { viewModel.refresh() }

    val project = state.currentProject
    Column(modifier = modifier.fillMaxSize().imePadding()) {
        KaiBuildHeader(
            subtitle = project?.let(::projectsPath),
            backContentDescription = if (project != null) {
                Res.string.kai_build_back_content_description
            } else {
                Res.string.kai_build_exit_content_description
            },
            onBack = { if (project != null) viewModel.leaveProject(project) else onExit() },
            onRefresh = viewModel::refresh,
            showRefresh = state.isInstalled && !state.isInstalling,
        )

        when {
            state.isInstalling || !state.isInstalled -> SetupContent(
                state = state,
                onInstall = viewModel::install,
                onCancel = viewModel::cancel,
            )

            project == null -> ProjectsContent(state = state, viewModel = viewModel)

            else -> ProjectContent(
                state = state,
                project = project,
                viewModel = viewModel,
            )
        }
    }
}

// --------------------------------------------------------------------- header

@Composable
private fun KaiBuildHeader(
    subtitle: String?,
    backContentDescription: StringResource,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    showRefresh: Boolean,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(backContentDescription),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.kai_build_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (showRefresh) {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------- setup

/**
 * Install screen. The agent checklist is what [onInstall] receives: the system
 * is installed either way, agents are the part the user chooses.
 */
@Composable
private fun SetupContent(
    state: KaiBuildState,
    onInstall: (Set<String>) -> Unit,
    onCancel: () -> Unit,
) {
    var selected by remember { mutableStateOf(setOf(BuildAgents.all.first().id)) }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(Res.string.kai_build_setup_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(Res.string.kai_build_setup_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(Res.string.kai_build_setup_agents_title),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = stringResource(Res.string.kai_build_setup_agents_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BuildAgents.all.forEach { agent ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !state.isInstalling) {
                        selected = if (agent.id in selected) selected - agent.id else selected + agent.id
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = agent.id in selected,
                    onCheckedChange = null,
                    enabled = !state.isInstalling,
                )
                Text(text = agent.label, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(Modifier.weight(1f))

        state.lastError?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (state.isInstalling) {
            stageLabel(state.stage)?.let { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val download = state.stage as? KaiBuildStage.Download
            if (download != null) {
                LinearProgressIndicator(
                    progress = { download.percent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            TextButton(
                onClick = onCancel,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(Res.string.kai_build_setup_cancel))
            }
        } else {
            Button(
                onClick = { onInstall(selected) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.kai_build_setup_install))
            }
        }
    }
}

/** Turns an install stage into a sentence. Null means "installing, nothing to say yet". */
@Composable
private fun stageLabel(stage: KaiBuildStage?): String? = when (stage) {
    null -> null
    is KaiBuildStage.Download -> stringResource(Res.string.kai_build_step_download, stage.percent)
    KaiBuildStage.Extract -> stringResource(Res.string.kai_build_step_extract)
    KaiBuildStage.Configure -> stringResource(Res.string.kai_build_step_configure)
    KaiBuildStage.BasePackages -> stringResource(Res.string.kai_build_step_base_packages)
    is KaiBuildStage.Agent -> stringResource(Res.string.kai_build_step_install_agent, stage.label)
}

// ------------------------------------------------------------------- projects

@Composable
private fun ProjectsContent(
    state: KaiBuildState,
    viewModel: KaiBuildViewModel,
) {
    var showNewProject by remember { mutableStateOf(false) }
    var newProjectName by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<String?>(null) }
    var renameTo by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            IconButton(onClick = {
                newProjectName = ""
                showNewProject = true
            }) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(Res.string.kai_build_new_project_content_description),
                )
            }
        }

        if (state.projects.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(Res.string.kai_build_projects_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(state.projects, key = { it }) { name ->
                    ProjectRow(
                        name = name,
                        sessionCount = state.sessionsOf(name).size,
                        onOpen = {
                            if (!viewModel.resumeProject(name)) viewModel.startSession(name, null)
                        },
                        onRename = {
                            renameTo = name
                            renaming = name
                        },
                        onDelete = { deleting = name },
                    )
                }
            }
        }

        SystemCard(state = state, onUninstall = viewModel::uninstall)
    }

    if (showNewProject) {
        AlertDialog(
            onDismissRequest = { showNewProject = false },
            title = { Text(stringResource(Res.string.kai_build_new_project_title)) },
            text = {
                OutlinedTextField(
                    value = newProjectName,
                    onValueChange = { newProjectName = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(Res.string.kai_build_projects_new_placeholder)) },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newProjectName.isNotBlank(),
                    onClick = {
                        val created = viewModel.createProject(newProjectName)
                        showNewProject = false
                        if (created != null && !viewModel.resumeProject(created)) {
                            viewModel.startSession(created, null)
                        }
                    },
                ) {
                    Text(stringResource(Res.string.kai_build_projects_create))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewProject = false }) {
                    Text(stringResource(Res.string.kai_build_setup_cancel))
                }
            },
        )
    }

    renaming?.let { current ->
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(stringResource(Res.string.kai_build_project_rename)) },
            text = {
                OutlinedTextField(
                    value = renameTo,
                    onValueChange = { renameTo = it },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = renameTo.isNotBlank() && renameTo != current,
                    onClick = {
                        viewModel.renameProject(current, renameTo)
                        renaming = null
                    },
                ) {
                    Text(stringResource(Res.string.kai_build_project_rename))
                }
            },
            dismissButton = {
                TextButton(onClick = { renaming = null }) {
                    Text(stringResource(Res.string.kai_build_setup_cancel))
                }
            },
        )
    }

    deleting?.let { name ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(Res.string.kai_build_project_delete_title)) },
            text = { Text(stringResource(Res.string.kai_build_project_delete_message, name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteProject(name)
                    deleting = null
                }) {
                    Text(
                        text = stringResource(Res.string.kai_build_project_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    Text(stringResource(Res.string.kai_build_setup_cancel))
                }
            },
        )
    }
}

@Composable
private fun ProjectRow(
    name: String,
    sessionCount: Int,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (sessionCount > 0) {
                    Text(
                        text = if (sessionCount == 1) {
                            stringResource(Res.string.kai_build_projects_session_open)
                        } else {
                            stringResource(Res.string.kai_build_projects_sessions_open, sessionCount)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = null)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.kai_build_project_rename)) },
                        onClick = {
                            menuOpen = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(Res.string.kai_build_project_delete),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

/** The installed system: what it weighs, and the one way to remove it. */
@Composable
private fun SystemCard(
    state: KaiBuildState,
    onUninstall: () -> Unit,
) {
    var confirming by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(Res.string.kai_build_system_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(Res.string.kai_build_system_packages, state.packagesInstalled),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DiskRow(Res.string.kai_build_system_disk_system, state.diskSystemMB)
            DiskRow(Res.string.kai_build_system_disk_projects, state.diskProjectsMB)
            DiskRow(Res.string.kai_build_system_disk_free, state.diskFreeMB)
            TextButton(
                onClick = { confirming = true },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(Res.string.kai_build_uninstall))
            }
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(Res.string.kai_build_uninstall_title)) },
            text = { Text(stringResource(Res.string.kai_build_uninstall_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    onUninstall()
                }) {
                    Text(
                        text = stringResource(Res.string.kai_build_uninstall),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) {
                    Text(stringResource(Res.string.kai_build_setup_cancel))
                }
            },
        )
    }
}

@Composable
private fun DiskRow(label: StringResource, megabytes: Long) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatMegabytes(megabytes),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
    }
}

/** Sizes are measured in whole MiB; the unit only switches once GiB is reached. */
private fun formatMegabytes(megabytes: Long): String = if (megabytes >= 1024) {
    val tenths = (megabytes % 1024) * 10 / 1024
    "${megabytes / 1024}.$tenths GB"
} else {
    "$megabytes MB"
}

// -------------------------------------------------------------------- project

@Composable
private fun ProjectContent(
    state: KaiBuildState,
    project: String,
    viewModel: KaiBuildViewModel,
    filesViewModel: SandboxFileBrowserViewModel = koinViewModel(qualifier = KAI_BUILD_FILES),
) {
    var pane by remember(project) { mutableStateOf(ProjectPane.Terminal) }
    var showAgentPicker by remember { mutableStateOf(false) }
    val sessions = state.sessionsOf(project)

    Column(Modifier.fillMaxSize()) {
        SessionStrip(
            sessions = sessions,
            activeSessionId = state.currentSession,
            onSelect = viewModel::selectSession,
            onClose = viewModel::closeSession,
            onNew = { showAgentPicker = true },
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PaneTab(
                label = stringResource(Res.string.kai_build_session_shell),
                selected = pane == ProjectPane.Terminal,
                onClick = { pane = ProjectPane.Terminal },
            )
            Spacer(Modifier.width(8.dp))
            PaneTab(
                label = stringResource(Res.string.kai_build_files_tab),
                selected = pane == ProjectPane.Files,
                onClick = { pane = ProjectPane.Files },
            )
        }

        when (pane) {
            ProjectPane.Terminal -> TerminalPane(
                sessionId = state.currentSession,
                viewModel = viewModel,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )

            ProjectPane.Files -> Box(Modifier.fillMaxWidth().weight(1f)) {
                SandboxFilesContent(
                    modifier = Modifier.fillMaxSize(),
                    initialPath = projectsPath(project),
                    rootPath = "/root",
                    viewModel = filesViewModel,
                )
            }
        }
    }

    if (showAgentPicker) {
        AlertDialog(
            onDismissRequest = { showAgentPicker = false },
            title = { Text(stringResource(Res.string.kai_build_projects_add_agent)) },
            text = {
                Column {
                    BuildAgents.all.forEach { agent ->
                        TextButton(onClick = {
                            showAgentPicker = false
                            viewModel.startSession(project, agent.id)
                        }) {
                            Text(agent.label)
                        }
                    }
                    TextButton(onClick = {
                        showAgentPicker = false
                        viewModel.startSession(project, null)
                    }) {
                        Text(stringResource(Res.string.kai_build_session_shell))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAgentPicker = false }) {
                    Text(stringResource(Res.string.kai_build_setup_cancel))
                }
            },
        )
    }
}

/**
 * The open sessions of one project, as chips. The active one is filled; closing
 * a chip ends that shell, not the project.
 */
@Composable
private fun SessionStrip(
    sessions: List<KaiBuildSessionInfo>,
    activeSessionId: String?,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
    onNew: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        sessions.forEach { session ->
            val selected = session.id == activeSessionId
            Row(
                modifier = Modifier
                    .background(
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        shape = RoundedCornerShape(10.dp),
                    )
                    .clickable { onSelect(session.id) }
                    .padding(start = 12.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = session.agentLabel ?: stringResource(Res.string.kai_build_session_shell),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
                IconButton(
                    onClick = { onClose(session.id) },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(Res.string.kai_build_session_close_content_description),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
        }
        IconButton(onClick = onNew) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(Res.string.kai_build_session_new_content_description),
            )
        }
    }
}

@Composable
private fun PaneTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/**
 * The active session's output plus a command line.
 *
 * The transcript is the shell's own list (live, appended as output arrives), so
 * this only has to render it and keep the tail in view. A submitted line runs as
 * a command — the shell has no PTY, so there is no character-at-a-time mode to
 * feed; while an agent or a build holds the shell, the line goes to its stdin.
 */
@Composable
private fun TerminalPane(
    sessionId: String?,
    viewModel: KaiBuildViewModel,
    modifier: Modifier = Modifier,
) {
    val transcript: SnapshotStateList<TerminalLine> = remember(sessionId) {
        if (sessionId == null) mutableStateListOf() else viewModel.transcriptFor(sessionId)
    }

    val listState = rememberLazyListState()
    var input by remember(sessionId) { mutableStateOf("") }

    LaunchedEffect(transcript.size) {
        if (transcript.isNotEmpty()) listState.animateScrollToItem(transcript.lastIndex)
    }

    Column(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp),
        ) {
            items(transcript) { line ->
                Text(
                    text = line.text,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = when (line) {
                        is TerminalLine.Command -> MaterialTheme.colorScheme.primary
                        is TerminalLine.Error -> MaterialTheme.colorScheme.error
                        is TerminalLine.Output -> MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                placeholder = { Text(stringResource(Res.string.kai_build_terminal_placeholder)) },
                maxLines = 3,
            )
            IconButton(
                onClick = {
                    val line = input.trim()
                    if (line.isNotEmpty()) {
                        viewModel.writeToTerminal(line)
                        input = ""
                    }
                },
                enabled = input.isNotBlank(),
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = stringResource(Res.string.kai_build_terminal_run_content_description),
                )
            }
        }
    }
}
