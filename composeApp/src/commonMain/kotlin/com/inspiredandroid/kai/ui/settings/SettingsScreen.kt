@file:OptIn(ExperimentalMaterial3Api::class)

package com.inspiredandroid.kai.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inspiredandroid.kai.BackIcon
import com.inspiredandroid.kai.Version
import com.inspiredandroid.kai.data.EmailAccount
import com.inspiredandroid.kai.data.Service
import com.inspiredandroid.kai.linux.LinuxDistro
import com.inspiredandroid.kai.ui.components.VerticalScrollbarForScroll
import com.inspiredandroid.kai.ui.handCursor
import com.inspiredandroid.kai.ui.kaiAdaptiveCardBorder
import com.inspiredandroid.kai.ui.kaiAdaptiveCardColors
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.github_mark
import kai.composeapp.generated.resources.settings_ai_mistakes_warning
import kai.composeapp.generated.resources.settings_documentation
import kai.composeapp.generated.resources.settings_tab_agent
import kai.composeapp.generated.resources.settings_tab_general
import kai.composeapp.generated.resources.settings_tab_integrations
import kai.composeapp.generated.resources.settings_tab_sandbox
import kai.composeapp.generated.resources.settings_tab_services
import kai.composeapp.generated.resources.settings_tab_tools
import kai.composeapp.generated.resources.settings_version
import kai.composeapp.generated.resources.snackbar_email_removed
import kai.composeapp.generated.resources.snackbar_mcp_server_removed
import kai.composeapp.generated.resources.snackbar_memory_deleted
import kai.composeapp.generated.resources.snackbar_service_removed
import kai.composeapp.generated.resources.snackbar_skill_removed
import kai.composeapp.generated.resources.snackbar_task_cancelled
import kai.composeapp.generated.resources.snackbar_undo
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

internal val StatusColorConnected = Color(0xFF4CAF50)
internal val StatusColorChecking = Color(0xFFFF9800)
internal val StatusColorError = Color(0xFFF44336)
internal val StatusColorUnknown = Color(0xFF9E9E9E)

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = koinViewModel(),
    sandboxViewModel: SandboxViewModel = koinViewModel(),
    onNavigateBack: () -> Unit,
    navigationTabBar: (@Composable () -> Unit)? = null,
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val sandboxState by sandboxViewModel.state.collectAsStateWithLifecycle()

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.onScreenVisible()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SettingsScreenContent(
        uiState = uiState,
        actions = viewModel.actions,
        sandboxState = sandboxState,
        onToggleSandbox = sandboxViewModel::onToggleSandbox,
        onSelectDistro = sandboxViewModel::onSelectDistro,
        onSetupSandbox = sandboxViewModel::onSetupSandbox,
        onCancelSandbox = sandboxViewModel::onCancelSandbox,
        onResetSandbox = sandboxViewModel::onResetSandbox,
        onInstallPackages = sandboxViewModel::onInstallPackages,
        onNavigateBack = onNavigateBack,
        navigationTabBar = navigationTabBar,
    )
}

@Composable
fun SettingsScreenContent(
    uiState: SettingsUiState,
    actions: SettingsActions = SettingsActions.NoOp,
    sandboxState: SandboxUiState = SandboxUiState(),
    onToggleSandbox: (Boolean) -> Unit = {},
    onSelectDistro: (LinuxDistro) -> Unit = {},
    onSetupSandbox: () -> Unit = {},
    onCancelSandbox: () -> Unit = {},
    onResetSandbox: () -> Unit = {},
    onInstallPackages: () -> Unit = {},
    onNavigateBack: () -> Unit = {},
    navigationTabBar: (@Composable () -> Unit)? = null,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val undoLabel = stringResource(Res.string.snackbar_undo)
    val memoryDeletedMsg = stringResource(Res.string.snackbar_memory_deleted)
    val taskCancelledMsg = stringResource(Res.string.snackbar_task_cancelled)
    val emailRemovedMsg = stringResource(Res.string.snackbar_email_removed)
    val serviceRemovedMsg = stringResource(Res.string.snackbar_service_removed)
    val mcpServerRemovedMsg = stringResource(Res.string.snackbar_mcp_server_removed)
    val skillRemovedMsg = stringResource(Res.string.snackbar_skill_removed)

    LaunchedEffect(uiState.pendingDeletion) {
        val deletion = uiState.pendingDeletion ?: return@LaunchedEffect
        snackbarHostState.currentSnackbarData?.dismiss()
        val message = when (deletion) {
            is PendingDeletion.Memory -> memoryDeletedMsg
            is PendingDeletion.Task -> taskCancelledMsg
            is PendingDeletion.EmailAccount -> emailRemovedMsg
            is PendingDeletion.Service -> serviceRemovedMsg
            is PendingDeletion.McpServer -> mcpServerRemovedMsg
            is PendingDeletion.Skill -> skillRemovedMsg
        }
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = undoLabel,
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) {
            actions.onUndoDelete()
        }
    }

    val pendingDeletion = uiState.pendingDeletion
    val filteredMemories = remember(uiState.memories, pendingDeletion) {
        if (pendingDeletion is PendingDeletion.Memory) uiState.memories.filter { it.key != pendingDeletion.key }.toImmutableList() else uiState.memories
    }
    val filteredTasks = remember(uiState.scheduledTasks, pendingDeletion) {
        if (pendingDeletion is PendingDeletion.Task) uiState.scheduledTasks.filter { it.id != pendingDeletion.id }.toImmutableList() else uiState.scheduledTasks
    }
    val filteredEmailAccounts = remember(uiState.emailAccounts, pendingDeletion) {
        if (pendingDeletion is PendingDeletion.EmailAccount) uiState.emailAccounts.filter { it.id != pendingDeletion.id }.toImmutableList() else uiState.emailAccounts
    }
    val filteredServices = remember(uiState.configuredServices, pendingDeletion) {
        if (pendingDeletion is PendingDeletion.Service) uiState.configuredServices.filter { it.instanceId != pendingDeletion.instanceId }.toImmutableList() else uiState.configuredServices
    }
    val filteredMcpServers = remember(uiState.mcpServers, pendingDeletion) {
        if (pendingDeletion is PendingDeletion.McpServer) uiState.mcpServers.filter { it.id != pendingDeletion.serverId }.toImmutableList() else uiState.mcpServers
    }
    val filteredSkills = remember(uiState.skills, pendingDeletion) {
        if (pendingDeletion is PendingDeletion.Skill) uiState.skills.filter { it.id != pendingDeletion.id }.toImmutableList() else uiState.skills
    }

    val filteredUiState = remember(uiState, filteredMemories, filteredTasks, filteredEmailAccounts, filteredServices, filteredMcpServers, filteredSkills) {
        uiState.copy(
            memories = filteredMemories,
            scheduledTasks = filteredTasks,
            emailAccounts = filteredEmailAccounts,
            configuredServices = filteredServices,
            mcpServers = filteredMcpServers,
            skills = filteredSkills,
        )
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding().statusBarsPadding().imePadding()) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = CenterHorizontally) {
            if (navigationTabBar != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 64.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = CenterVertically,
                ) {
                    navigationTabBar()
                }
            } else {
                TopBar(onNavigateBack = onNavigateBack)
            }

            val visibleTabs = remember(sandboxState.showSandbox) {
                SettingsTab.entries.filter { it != SettingsTab.Sandbox || sandboxState.showSandbox }.toImmutableList()
            }

            SettingsTabSelector(
                tabs = visibleTabs,
                currentTab = filteredUiState.currentTab,
                onSelectTab = actions.onSelectTab,
            )

            val settingsScrollState = rememberScrollState()
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().verticalScroll(settingsScrollState),
                    horizontalAlignment = CenterHorizontally,
                ) {
                    Spacer(Modifier.height(16.dp))

                    val maxContentWidth = when (filteredUiState.currentTab) {
                        SettingsTab.Services -> 500.dp
                        else -> 900.dp
                    }
                    Column(
                        Modifier.widthIn(max = maxContentWidth).fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalAlignment = CenterHorizontally,
                    ) {
                        when (filteredUiState.currentTab) {
                            SettingsTab.General -> {
                                GeneralContent(uiState = filteredUiState, actions = actions)
                            }

                            SettingsTab.Agent -> {
                                AgentContent(uiState = filteredUiState, actions = actions)
                            }

                            SettingsTab.Services -> {
                                ServicesContent(uiState = filteredUiState, actions = actions)
                            }

                            SettingsTab.Integrations -> {
                                IntegrationsContent()
                            }

                            SettingsTab.Tools -> {
                                ToolsContent(
                                    tools = filteredUiState.tools,
                                    onToggleTool = actions.onToggleTool,
                                    mcpServers = filteredUiState.mcpServers,
                                    onAddMcpServer = actions.onAddMcpServer,
                                    onRemoveMcpServer = actions.onRemoveMcpServer,
                                    onToggleMcpServer = actions.onToggleMcpServer,
                                    onRefreshMcpServer = actions.onRefreshMcpServer,
                                    showAddMcpServerDialog = filteredUiState.showAddMcpServerDialog,
                                    onShowAddMcpServerDialog = actions.onShowAddMcpServerDialog,
                                    onAddPopularMcpServer = actions.onAddPopularMcpServer,
                                    skills = filteredUiState.skills,
                                    onUninstallSkill = actions.onUninstallSkill,
                                    showAddSkillDialog = filteredUiState.showAddSkillDialog,
                                    onShowAddSkillDialog = actions.onShowAddSkillDialog,
                                    onInstallGitHubSkill = actions.onInstallGitHubSkill,
                                    onInstallBrowsedSkill = actions.onInstallBrowsedSkill,
                                    isInstallingSkill = filteredUiState.isInstallingSkill,
                                    skillInstallError = filteredUiState.skillInstallError,
                                    browsableSkills = filteredUiState.browsableSkills,
                                    isBrowsingSkills = filteredUiState.isBrowsingSkills,
                                    browseSkillsFailed = filteredUiState.browseSkillsFailed,
                                    showSkills = sandboxState.showSandbox,
                                    isSandboxInstalled = sandboxState.sandboxInstalled,
                                    onNavigateToSandbox = { actions.onSelectTab(SettingsTab.Sandbox) },
                                )
                            }

                            SettingsTab.Sandbox -> {
                                SandboxSettingsCard(
                                    sandboxState = sandboxState,
                                    onToggleSandbox = onToggleSandbox,
                                    onSelectDistro = onSelectDistro,
                                    onSetupSandbox = onSetupSandbox,
                                    onCancelSandbox = onCancelSandbox,
                                    onResetSandbox = onResetSandbox,
                                    onInstallPackages = onInstallPackages,
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                    }

                    Spacer(Modifier.weight(1f))

                    BottomInfo()
                }
                VerticalScrollbarForScroll(
                    scrollState = settingsScrollState,
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                )
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
        ) { data ->
            Snackbar(snackbarData = data)
        }
    }
}

@Composable
private fun TopBar(onNavigateBack: () -> Unit) {
    Row {
        IconButton(
            modifier = Modifier.handCursor(),
            onClick = onNavigateBack,
        ) {
            Icon(
                imageVector = BackIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun SettingsTabSelector(
    tabs: ImmutableList<SettingsTab>,
    currentTab: SettingsTab,
    onSelectTab: (SettingsTab) -> Unit,
) {
    Surface(
        modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth().padding(vertical = 8.dp),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .padding(4.dp)
                .horizontalScroll(rememberScrollState()),
        ) {
            tabs.forEach { tab ->
                val isSelected = currentTab == tab
                Surface(
                    modifier = Modifier
                        .handCursor()
                        .clip(RoundedCornerShape(50))
                        .clickable { onSelectTab(tab) },
                    shape = RoundedCornerShape(50),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    } else {
                        Color.Transparent
                    },
                ) {
                    Text(
                        text = when (tab) {
                            SettingsTab.General -> stringResource(Res.string.settings_tab_general)
                            SettingsTab.Agent -> stringResource(Res.string.settings_tab_agent)
                            SettingsTab.Services -> stringResource(Res.string.settings_tab_services)
                            SettingsTab.Tools -> stringResource(Res.string.settings_tab_tools)
                            SettingsTab.Sandbox -> stringResource(Res.string.settings_tab_sandbox)
                            SettingsTab.Integrations -> stringResource(Res.string.settings_tab_integrations)
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomInfo() {
    Text(
        text = stringResource(Res.string.settings_ai_mistakes_warning),
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onBackground,
    )

    Spacer(Modifier.height(8.dp))

    val uriHandler = LocalUriHandler.current

    Row(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(Res.string.settings_version, Version.appVersion),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.width(8.dp))

        Icon(
            modifier = Modifier
                .clip(CircleShape)
                .size(24.dp)
                .clickable(onClick = {
                    uriHandler.openUri("https://github.com/SimonSchubert/Kai")
                })
                .handCursor(),
            painter = painterResource(Res.drawable.github_mark),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.width(12.dp))

        Text(
            text = stringResource(Res.string.settings_documentation),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable { uriHandler.openUri("https://kai9000.com/docs/") }
                .handCursor(),
        )
    }

    Spacer(Modifier.height(8.dp))
}

@Composable
internal fun SettingsCard(
    modifier: Modifier = Modifier,
    innerPadding: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        colors = kaiAdaptiveCardColors(),
        border = kaiAdaptiveCardBorder(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick).handCursor() else Modifier)
                .then(if (innerPadding) Modifier.padding(16.dp) else Modifier),
        ) {
            content()
        }
    }
}

/**
 * The two-column-on-wide, one-column-on-narrow arrangement the settings tabs share.
 *
 * Each section is declared exactly once. [start] and [end] are the two columns a window of at
 * least 600.dp gets; below that they run into a single column, [start] first. Writing a tab as
 * two independent branches instead is what let the same section drift into two different
 * argument lists, so the branch lives here and the tabs only say what goes in which column.
 */
@Composable
internal fun StaggeredSettingsColumns(
    start: @Composable ColumnScope.() -> Unit,
    end: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth >= 600.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    content = start,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    content = end,
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                start()
                end()
            }
        }
    }
}

@Composable
internal fun ToggleableHeadline(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val switchInteractionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = switchInteractionSource,
                indication = null,
            ) { onCheckedChange(!checked) }
            .handCursor(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        actions()
        Switch(
            checked = checked,
            onCheckedChange = null,
            interactionSource = switchInteractionSource,
        )
    }
    Spacer(Modifier.size(4.dp))
    Text(
        text = description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
