@file:OptIn(ExperimentalMaterial3Api::class)

package com.inspiredandroid.kai.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.inspiredandroid.kai.BackIcon
import com.inspiredandroid.kai.Version
import com.inspiredandroid.kai.data.EmailAccount
import com.inspiredandroid.kai.data.HeartbeatLogEntry
import com.inspiredandroid.kai.data.ImportSection
import com.inspiredandroid.kai.data.MemoryEntry
import com.inspiredandroid.kai.data.ScheduledTask
import com.inspiredandroid.kai.data.Service
import com.inspiredandroid.kai.data.SharedJson
import com.inspiredandroid.kai.data.SkillEntry
import com.inspiredandroid.kai.data.SkillExecutionResult
import com.inspiredandroid.kai.data.TaskStatus
import com.inspiredandroid.kai.data.detectImportSections
import com.inspiredandroid.kai.data.prettyPrintJs
import com.inspiredandroid.kai.mcp.PopularMcpServer
import com.inspiredandroid.kai.mcp.popularMcpServers
import com.inspiredandroid.kai.network.dtos.SponsorsResponseDto
import com.inspiredandroid.kai.network.tools.ToolInfo
import com.inspiredandroid.kai.saveFileToDevice
import com.inspiredandroid.kai.ui.icons.DragIndicator
import com.inspiredandroid.kai.ui.icons.ExpandLess
import com.inspiredandroid.kai.ui.icons.ExpandMore
import com.inspiredandroid.kai.ui.icons.Replay
import com.inspiredandroid.kai.ui.icons.Visibility
import com.inspiredandroid.kai.ui.icons.VisibilityOff
import com.inspiredandroid.kai.ui.outlineTextFieldColors
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.readBytes
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.default_soul
import kai.composeapp.generated.resources.github_mark
import kai.composeapp.generated.resources.ic_arrow_drop_down
import kai.composeapp.generated.resources.settings_add_service
import kai.composeapp.generated.resources.settings_ai_mistakes_warning
import kai.composeapp.generated.resources.settings_api_key_label
import kai.composeapp.generated.resources.settings_api_key_optional_label
import kai.composeapp.generated.resources.settings_base_url_label
import kai.composeapp.generated.resources.settings_become_sponsor
import kai.composeapp.generated.resources.settings_business_partnerships
import kai.composeapp.generated.resources.settings_business_partnerships_description
import kai.composeapp.generated.resources.settings_contact_sponsorship
import kai.composeapp.generated.resources.settings_daemon_mode
import kai.composeapp.generated.resources.settings_daemon_mode_description
import kai.composeapp.generated.resources.settings_documentation
import kai.composeapp.generated.resources.settings_email
import kai.composeapp.generated.resources.settings_email_description
import kai.composeapp.generated.resources.settings_email_empty
import kai.composeapp.generated.resources.settings_email_poll_interval
import kai.composeapp.generated.resources.settings_email_poll_never
import kai.composeapp.generated.resources.settings_email_remove
import kai.composeapp.generated.resources.settings_export
import kai.composeapp.generated.resources.settings_export_import_description
import kai.composeapp.generated.resources.settings_export_import_title
import kai.composeapp.generated.resources.settings_free_fallback
import kai.composeapp.generated.resources.settings_free_tier_description
import kai.composeapp.generated.resources.settings_free_tier_title
import kai.composeapp.generated.resources.settings_heartbeat
import kai.composeapp.generated.resources.settings_heartbeat_active_hours
import kai.composeapp.generated.resources.settings_heartbeat_default_prompt
import kai.composeapp.generated.resources.settings_heartbeat_description
import kai.composeapp.generated.resources.settings_heartbeat_interval
import kai.composeapp.generated.resources.settings_heartbeat_prompt_label
import kai.composeapp.generated.resources.settings_heartbeat_recent
import kai.composeapp.generated.resources.settings_heartbeat_reset_confirm
import kai.composeapp.generated.resources.settings_import
import kai.composeapp.generated.resources.settings_import_error
import kai.composeapp.generated.resources.settings_import_partial
import kai.composeapp.generated.resources.settings_import_preview_title
import kai.composeapp.generated.resources.settings_import_replace_all
import kai.composeapp.generated.resources.settings_import_replace_all_description
import kai.composeapp.generated.resources.settings_import_section_email
import kai.composeapp.generated.resources.settings_import_section_heartbeat
import kai.composeapp.generated.resources.settings_import_section_mcp
import kai.composeapp.generated.resources.settings_import_section_memory
import kai.composeapp.generated.resources.settings_import_section_scheduling
import kai.composeapp.generated.resources.settings_import_section_services
import kai.composeapp.generated.resources.settings_import_section_skills
import kai.composeapp.generated.resources.settings_import_section_soul
import kai.composeapp.generated.resources.settings_import_section_tools
import kai.composeapp.generated.resources.settings_import_success
import kai.composeapp.generated.resources.settings_mcp_add
import kai.composeapp.generated.resources.settings_mcp_add_server
import kai.composeapp.generated.resources.settings_mcp_auth_header
import kai.composeapp.generated.resources.settings_mcp_cancel
import kai.composeapp.generated.resources.settings_mcp_no_tools
import kai.composeapp.generated.resources.settings_mcp_popular_servers
import kai.composeapp.generated.resources.settings_mcp_refresh
import kai.composeapp.generated.resources.settings_mcp_remove
import kai.composeapp.generated.resources.settings_mcp_server_name
import kai.composeapp.generated.resources.settings_mcp_server_url
import kai.composeapp.generated.resources.settings_mcp_servers
import kai.composeapp.generated.resources.settings_mcp_servers_description
import kai.composeapp.generated.resources.settings_mcp_status_connected
import kai.composeapp.generated.resources.settings_mcp_status_connecting
import kai.composeapp.generated.resources.settings_mcp_status_error
import kai.composeapp.generated.resources.settings_memories
import kai.composeapp.generated.resources.settings_memories_delete
import kai.composeapp.generated.resources.settings_memories_description
import kai.composeapp.generated.resources.settings_model_label
import kai.composeapp.generated.resources.settings_model_search
import kai.composeapp.generated.resources.settings_open_github_issue
import kai.composeapp.generated.resources.settings_openai_compatible_or_other_service
import kai.composeapp.generated.resources.settings_openai_compatible_providers
import kai.composeapp.generated.resources.settings_openai_compatible_setup_ollama
import kai.composeapp.generated.resources.settings_remove_service
import kai.composeapp.generated.resources.settings_request_integration_description
import kai.composeapp.generated.resources.settings_request_integration_title
import kai.composeapp.generated.resources.settings_scheduled_tasks
import kai.composeapp.generated.resources.settings_scheduled_tasks_cancel
import kai.composeapp.generated.resources.settings_scheduled_tasks_description
import kai.composeapp.generated.resources.settings_sign_in_copy_api_key_from
import kai.composeapp.generated.resources.settings_skills
import kai.composeapp.generated.resources.settings_skills_delete
import kai.composeapp.generated.resources.settings_skills_description
import kai.composeapp.generated.resources.settings_skills_empty
import kai.composeapp.generated.resources.settings_soul
import kai.composeapp.generated.resources.settings_soul_description
import kai.composeapp.generated.resources.settings_soul_reset
import kai.composeapp.generated.resources.settings_soul_reset_cancel
import kai.composeapp.generated.resources.settings_soul_reset_confirm
import kai.composeapp.generated.resources.settings_soul_save
import kai.composeapp.generated.resources.settings_sponsors_monthly
import kai.composeapp.generated.resources.settings_sponsors_past
import kai.composeapp.generated.resources.settings_status_checking
import kai.composeapp.generated.resources.settings_status_connected
import kai.composeapp.generated.resources.settings_status_error
import kai.composeapp.generated.resources.settings_status_error_connection_failed
import kai.composeapp.generated.resources.settings_status_error_invalid_key
import kai.composeapp.generated.resources.settings_status_error_quota_exhausted
import kai.composeapp.generated.resources.settings_status_error_rate_limited
import kai.composeapp.generated.resources.settings_tab_general
import kai.composeapp.generated.resources.settings_tab_integrations
import kai.composeapp.generated.resources.settings_tab_services
import kai.composeapp.generated.resources.settings_tab_skills
import kai.composeapp.generated.resources.settings_tab_tools
import kai.composeapp.generated.resources.settings_tools_description
import kai.composeapp.generated.resources.settings_tools_none_available
import kai.composeapp.generated.resources.settings_ui_scale
import kai.composeapp.generated.resources.settings_version
import kai.composeapp.generated.resources.snackbar_email_removed
import kai.composeapp.generated.resources.snackbar_mcp_server_removed
import kai.composeapp.generated.resources.snackbar_memory_deleted
import kai.composeapp.generated.resources.snackbar_service_removed
import kai.composeapp.generated.resources.snackbar_skill_deleted
import kai.composeapp.generated.resources.snackbar_task_cancelled
import kai.composeapp.generated.resources.snackbar_undo
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.jsonObject
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import sh.calvin.reorderable.ReorderableColumn
import kotlin.math.roundToInt
import kotlin.time.Instant

private val StatusColorConnected = Color(0xFF4CAF50)
private val StatusColorChecking = Color(0xFFFF9800)
private val StatusColorError = Color(0xFFF44336)
private val StatusColorUnknown = Color(0xFF9E9E9E)

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = koinViewModel(),
    onNavigateBack: () -> Unit,
    navigationTabBar: (@Composable () -> Unit)? = null,
) {
    val uiState by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.onScreenVisible()
    }

    SettingsScreenContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        navigationTabBar = navigationTabBar,
    )
}

@Composable
fun SettingsScreenContent(
    uiState: SettingsUiState,
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
    val skillDeletedMsg = stringResource(Res.string.snackbar_skill_deleted)

    LaunchedEffect(uiState.pendingDeletion) {
        val deletion = uiState.pendingDeletion ?: return@LaunchedEffect
        snackbarHostState.currentSnackbarData?.dismiss()
        val message = when (deletion) {
            is PendingDeletion.Memory -> memoryDeletedMsg
            is PendingDeletion.Task -> taskCancelledMsg
            is PendingDeletion.EmailAccount -> emailRemovedMsg
            is PendingDeletion.Service -> serviceRemovedMsg
            is PendingDeletion.McpServer -> mcpServerRemovedMsg
            is PendingDeletion.Skill -> skillDeletedMsg
        }
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = undoLabel,
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) {
            uiState.onUndoDelete()
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
    val filteredSkills = remember(uiState.skills, pendingDeletion) {
        if (pendingDeletion is PendingDeletion.Skill) uiState.skills.filter { it.name != pendingDeletion.name }.toImmutableList() else uiState.skills
    }
    val filteredServices = remember(uiState.configuredServices, pendingDeletion) {
        if (pendingDeletion is PendingDeletion.Service) uiState.configuredServices.filter { it.instanceId != pendingDeletion.instanceId }.toImmutableList() else uiState.configuredServices
    }
    val filteredMcpServers = remember(uiState.mcpServers, pendingDeletion) {
        if (pendingDeletion is PendingDeletion.McpServer) uiState.mcpServers.filter { it.id != pendingDeletion.serverId }.toImmutableList() else uiState.mcpServers
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

            SettingsTabSelector(
                currentTab = filteredUiState.currentTab,
                onSelectTab = filteredUiState.onSelectTab,
            )

            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = CenterHorizontally,
            ) {
                Spacer(Modifier.height(16.dp))

                val maxContentWidth = when (filteredUiState.currentTab) {
                    SettingsTab.Tools -> 900.dp
                    SettingsTab.General -> 900.dp
                    SettingsTab.Integrations -> 900.dp
                    else -> 500.dp
                }
                Column(
                    Modifier.widthIn(max = maxContentWidth).fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalAlignment = CenterHorizontally,
                ) {
                    when (filteredUiState.currentTab) {
                        SettingsTab.General -> {
                            GeneralContent(uiState = filteredUiState)
                        }

                        SettingsTab.Services -> {
                            ServicesContent(uiState = filteredUiState)
                        }

                        SettingsTab.Integrations -> {
                            IntegrationsContent()
                        }

                        SettingsTab.Tools -> {
                            ToolsContent(
                                tools = filteredUiState.tools,
                                onToggleTool = filteredUiState.onToggleTool,
                                mcpServers = filteredUiState.mcpServers,
                                onAddMcpServer = filteredUiState.onAddMcpServer,
                                onRemoveMcpServer = filteredUiState.onRemoveMcpServer,
                                onToggleMcpServer = filteredUiState.onToggleMcpServer,
                                onRefreshMcpServer = filteredUiState.onRefreshMcpServer,
                                showAddMcpServerDialog = filteredUiState.showAddMcpServerDialog,
                                onShowAddMcpServerDialog = filteredUiState.onShowAddMcpServerDialog,
                                onAddPopularMcpServer = filteredUiState.onAddPopularMcpServer,
                            )
                        }

                        SettingsTab.Skills -> {
                            SkillsContent(
                                skills = filteredUiState.skills,
                                onDeleteSkill = filteredUiState.onDeleteSkill,
                                isSkillsEnabled = filteredUiState.isSkillsEnabled,
                                onToggleSkills = filteredUiState.onToggleSkills,
                                onExecuteSkill = filteredUiState.onExecuteSkill,
                                executingSkillName = filteredUiState.executingSkillName,
                                skillExecutionResult = filteredUiState.skillExecutionResult,
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                }

                Spacer(Modifier.weight(1f))

                BottomInfo()
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
            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
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
    currentTab: SettingsTab,
    onSelectTab: (SettingsTab) -> Unit,
) {
    val tabs = SettingsTab.entries
    val selectedIndex = tabs.indexOf(currentTab)
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.PrimaryScrollableTabRow(
            selectedTabIndex = selectedIndex,
            modifier = Modifier.widthIn(max = 600.dp).padding(vertical = 8.dp),
            containerColor = Color.Transparent,
            edgePadding = 0.dp,
            divider = {},
        ) {
            tabs.forEach { tab ->
                androidx.compose.material3.Tab(
                    selected = currentTab == tab,
                    onClick = { onSelectTab(tab) },
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    text = {
                        Text(
                            text = when (tab) {
                                SettingsTab.General -> stringResource(Res.string.settings_tab_general)
                                SettingsTab.Services -> stringResource(Res.string.settings_tab_services)
                                SettingsTab.Tools -> stringResource(Res.string.settings_tab_tools)
                                SettingsTab.Skills -> stringResource(Res.string.settings_tab_skills)
                                SettingsTab.Integrations -> stringResource(Res.string.settings_tab_integrations)
                            },
                        )
                    },
                )
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
                .pointerHoverIcon(PointerIcon.Hand),
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
                .clickable { uriHandler.openUri("https://simonschubert.github.io/Kai/docs/") }
                .pointerHoverIcon(PointerIcon.Hand),
        )
    }

    Spacer(Modifier.height(8.dp))
}

@Composable
private fun FreeSettings(
    showFallbackToggle: Boolean = false,
    isFreeFallbackEnabled: Boolean = true,
    onToggleFreeFallback: (Boolean) -> Unit = {},
    currentSponsors: ImmutableList<SponsorsResponseDto.Sponsor> = persistentListOf(),
    pastSponsors: ImmutableList<SponsorsResponseDto.Sponsor> = persistentListOf(),
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(Res.string.settings_free_tier_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            if (showFallbackToggle) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onToggleFreeFallback(!isFreeFallbackEnabled) }
                        .pointerHoverIcon(PointerIcon.Hand),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.settings_free_fallback),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = isFreeFallbackEnabled,
                        onCheckedChange = onToggleFreeFallback,
                    )
                }
                Spacer(Modifier.height(6.dp))
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text = stringResource(Res.string.settings_free_tier_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))

            val uriHandler = LocalUriHandler.current
            Button(
                onClick = {
                    uriHandler.openUri("https://github.com/sponsors/SimonSchubert")
                },
                Modifier
                    .align(CenterHorizontally)
                    .pointerHoverIcon(PointerIcon.Hand),
            ) {
                Icon(Icons.Default.Favorite, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.settings_become_sponsor))
            }

            if (currentSponsors.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(thickness = 0.5.dp)
                Spacer(Modifier.height(16.dp))
                SponsorList(
                    title = stringResource(Res.string.settings_sponsors_monthly),
                    sponsors = currentSponsors,
                )
            }

            if (pastSponsors.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(thickness = 0.5.dp)
                Spacer(Modifier.height(16.dp))
                SponsorList(
                    title = stringResource(Res.string.settings_sponsors_past),
                    sponsors = pastSponsors,
                )
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(thickness = 0.5.dp)
            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(Res.string.settings_business_partnerships),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(Res.string.settings_business_partnerships_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            TextButton(
                onClick = {
                    uriHandler.openUri("https://schubert-simon.de")
                },
                Modifier
                    .pointerHoverIcon(PointerIcon.Hand),
            ) {
                Text(stringResource(Res.string.settings_contact_sponsorship))
            }
        }
    }
}

@Composable
private fun SponsorList(
    title: String,
    sponsors: ImmutableList<SponsorsResponseDto.Sponsor>,
) {
    val uriHandler = LocalUriHandler.current
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        sponsors.forEach { sponsor ->
            Column(
                horizontalAlignment = CenterHorizontally,
                modifier = Modifier
                    .width(72.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { uriHandler.openUri("https://github.com/${sponsor.username}") }
                    .pointerHoverIcon(PointerIcon.Hand)
                    .padding(4.dp),
            ) {
                coil3.compose.AsyncImage(
                    model = sponsor.avatar,
                    contentDescription = sponsor.username,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = sponsor.username,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ServicesContent(uiState: SettingsUiState) {
    var showAddServiceSheet by remember { mutableStateOf(false) }

    // Configured services list
    val entries = uiState.configuredServices
    ReorderableColumn(
        list = entries,
        onSettle = { fromIndex, toIndex ->
            val ids = entries.map { it.instanceId }.toMutableList()
            ids.add(toIndex, ids.removeAt(fromIndex))
            uiState.onReorderServices(ids)
        },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { _, entry, isDragging ->
        key(entry.instanceId) {
            ReorderableItem {
                ConfiguredServiceCardContent(
                    entry = entry,
                    isExpanded = uiState.expandedServiceId == entry.instanceId,
                    onExpand = { uiState.onExpandService(if (uiState.expandedServiceId == entry.instanceId) null else entry.instanceId) },
                    onChangeApiKey = { apiKey -> uiState.onChangeApiKey(entry.instanceId, apiKey) },
                    onChangeBaseUrl = { baseUrl -> uiState.onChangeBaseUrl(entry.instanceId, baseUrl) },
                    onSelectModel = { modelId -> uiState.onSelectModel(entry.instanceId, modelId) },
                    onRemove = { uiState.onRemoveService(entry.instanceId) },
                    isDragging = isDragging,
                    dragHandleModifier = if (entries.size >= 2) Modifier.draggableHandle() else null,
                )
            }
        }
    }

    if (uiState.availableServicesToAdd.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { showAddServiceSheet = true }, modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)) {
            Text(stringResource(Res.string.settings_add_service))
        }
    }

    // Free tier card (always at bottom)
    Spacer(Modifier.height(16.dp))
    FreeSettings(
        showFallbackToggle = entries.isNotEmpty(),
        isFreeFallbackEnabled = uiState.isFreeFallbackEnabled,
        onToggleFreeFallback = uiState.onToggleFreeFallback,
        currentSponsors = uiState.currentSponsors,
        pastSponsors = uiState.pastSponsors,
    )

    // Add service bottom sheet
    if (showAddServiceSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddServiceSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                uiState.availableServicesToAdd.forEach { service ->
                    Surface(
                        onClick = {
                            uiState.onAddService(service)
                            showAddServiceSheet = false
                        },
                        modifier = Modifier.fillMaxWidth().pointerHoverIcon(PointerIcon.Hand),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = vectorResource(service.icon),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = service.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ConfiguredServiceCardContent(
    entry: ConfiguredServiceEntry,
    isExpanded: Boolean,
    onExpand: () -> Unit,
    onChangeApiKey: (String) -> Unit,
    onChangeBaseUrl: (String) -> Unit,
    onSelectModel: (String) -> Unit,
    onRemove: () -> Unit,
    isDragging: Boolean = false,
    dragHandleModifier: Modifier? = null,
) {
    Card(
        onClick = onExpand,
        modifier = Modifier.fillMaxWidth().pointerHoverIcon(PointerIcon.Hand),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 4.dp else 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Drag handle
                if (dragHandleModifier != null) {
                    Icon(
                        imageVector = Icons.Rounded.DragIndicator,
                        contentDescription = "Reorder",
                        modifier = dragHandleModifier.pointerHoverIcon(PointerIcon.Hand),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                }

                // Connection status dot
                val dotColor = when (entry.connectionStatus) {
                    ConnectionStatus.Connected -> StatusColorConnected
                    ConnectionStatus.Checking -> StatusColorChecking
                    ConnectionStatus.Unknown -> StatusColorUnknown
                    else -> StatusColorError
                }
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )

                Spacer(Modifier.width(12.dp))

                // Service name and model
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.service.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    if (entry.selectedModel != null) {
                        Text(
                            text = entry.selectedModel.id,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Expand/collapse chevron
                Icon(
                    imageVector = vectorResource(Res.drawable.ic_arrow_drop_down),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Expanded content
        if (isExpanded) {
            Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                if (entry.service is Service.OpenAICompatible) {
                    OpenAICompatibleSettings(
                        baseUrl = entry.baseUrl,
                        onChangeBaseUrl = onChangeBaseUrl,
                        apiKey = entry.apiKey,
                        onChangeApiKey = onChangeApiKey,
                        selectedModel = entry.selectedModel,
                        models = entry.models,
                        onSelectModel = onSelectModel,
                        connectionStatus = entry.connectionStatus,
                    )
                } else {
                    ServiceSettings(
                        apiKey = entry.apiKey,
                        onChangeApiKey = onChangeApiKey,
                        apiKeyUrl = entry.service.apiKeyUrl ?: "",
                        apiKeyUrlDisplay = entry.service.apiKeyUrlDisplay ?: "",
                        selectedModel = entry.selectedModel,
                        models = entry.models,
                        onSelectModel = onSelectModel,
                        connectionStatus = entry.connectionStatus,
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Remove action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onRemove,
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(Res.string.settings_remove_service),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceSettings(
    apiKey: String,
    onChangeApiKey: (String) -> Unit,
    apiKeyUrl: String,
    apiKeyUrlDisplay: String,
    selectedModel: SettingsModel?,
    models: ImmutableList<SettingsModel>,
    onSelectModel: (String) -> Unit,
    connectionStatus: ConnectionStatus,
    testTag: String? = null,
) {
    var apiKeyFocused by remember { mutableStateOf(false) }
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth()
            .let { if (testTag != null) it.testTag(testTag) else it }
            .onFocusChanged { apiKeyFocused = it.isFocused },
        value = apiKey,
        onValueChange = onChangeApiKey,
        label = {
            Text(
                stringResource(Res.string.settings_api_key_label),
                color = MaterialTheme.colorScheme.onBackground,
            )
        },
        colors = outlineTextFieldColors(),
        trailingIcon = {
            IconButton(
                onClick = { onChangeApiKey("") },
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                    .alpha(if (apiKeyFocused && apiKey.isNotEmpty()) 1f else 0f),
                enabled = apiKey.isNotEmpty(),
            ) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )

    Spacer(Modifier.height(8.dp))

    ConnectionStatusIndicator(connectionStatus)

    Spacer(Modifier.height(8.dp))

    val linkColor = MaterialTheme.colorScheme.primary

    val copyApiKeyPromptString = stringResource(Res.string.settings_sign_in_copy_api_key_from)
    val annotatedString = remember(apiKeyUrl, apiKeyUrlDisplay) {
        buildAnnotatedString {
            append(copyApiKeyPromptString)
            append(" ")
            withLink(LinkAnnotation.Url(url = apiKeyUrl)) {
                withStyle(style = SpanStyle(color = linkColor)) {
                    append(apiKeyUrlDisplay)
                }
            }
        }
    }
    Text(
        annotatedString,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.onBackground,
    )

    Spacer(Modifier.height(16.dp))

    if (connectionStatus == ConnectionStatus.Connected || models.isNotEmpty()) {
        ModelSelection(selectedModel, models, onSelectModel)
    }
}

@Composable
private fun OpenAICompatibleSettings(
    baseUrl: String,
    onChangeBaseUrl: (String) -> Unit,
    apiKey: String,
    onChangeApiKey: (String) -> Unit,
    selectedModel: SettingsModel?,
    models: ImmutableList<SettingsModel>,
    onSelectModel: (String) -> Unit,
    connectionStatus: ConnectionStatus,
) {
    var baseUrlFocused by remember { mutableStateOf(false) }
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth().onFocusChanged { baseUrlFocused = it.isFocused },
        value = baseUrl,
        onValueChange = onChangeBaseUrl,
        label = {
            Text(
                stringResource(Res.string.settings_base_url_label),
                color = MaterialTheme.colorScheme.onBackground,
            )
        },
        colors = outlineTextFieldColors(),
        singleLine = true,
        trailingIcon = {
            IconButton(
                onClick = { onChangeBaseUrl("") },
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                    .alpha(if (baseUrlFocused && baseUrl.isNotEmpty()) 1f else 0f),
                enabled = baseUrl.isNotEmpty(),
            ) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
    if (baseUrl.isNotBlank()) {
        Text(
            text = "${baseUrl.trimEnd('/')}${Service.OpenAICompatible.chatUrl}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp),
        )
    }

    Spacer(Modifier.height(8.dp))

    var apiKeyFocused by remember { mutableStateOf(false) }
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth().onFocusChanged { apiKeyFocused = it.isFocused },
        value = apiKey,
        onValueChange = onChangeApiKey,
        label = {
            Text(
                stringResource(Res.string.settings_api_key_optional_label),
                color = MaterialTheme.colorScheme.onBackground,
            )
        },
        colors = outlineTextFieldColors(),
        singleLine = true,
        trailingIcon = {
            IconButton(
                onClick = { onChangeApiKey("") },
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                    .alpha(if (apiKeyFocused && apiKey.isNotEmpty()) 1f else 0f),
                enabled = apiKey.isNotEmpty(),
            ) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )

    Spacer(Modifier.height(8.dp))

    ConnectionStatusIndicator(connectionStatus)

    Spacer(Modifier.height(8.dp))

    val linkColor = MaterialTheme.colorScheme.primary
    val setupOllamaText = stringResource(Res.string.settings_openai_compatible_setup_ollama)
    val orOtherServiceText = stringResource(Res.string.settings_openai_compatible_or_other_service)
    val providersText = stringResource(Res.string.settings_openai_compatible_providers)
    val annotatedString = remember(setupOllamaText, orOtherServiceText, providersText, linkColor) {
        buildAnnotatedString {
            append(setupOllamaText)
            append(" ")
            withLink(LinkAnnotation.Url(url = "https://github.com/ollama/ollama")) {
                withStyle(style = SpanStyle(color = linkColor)) {
                    append("github.com/ollama/ollama")
                }
            }
            append(" ")
            append(orOtherServiceText)
            append(" ")
            withLink(LinkAnnotation.Url(url = "https://docs.litellm.ai/docs/providers")) {
                withStyle(style = SpanStyle(color = linkColor)) {
                    append(providersText)
                }
            }
        }
    }
    Text(
        annotatedString,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.onBackground,
    )

    Spacer(Modifier.height(16.dp))

    if (connectionStatus == ConnectionStatus.Connected) {
        ModelSelection(selectedModel, models, onSelectModel)
    }
}

@Composable
private fun ConnectionStatusIndicator(status: ConnectionStatus) {
    when (status) {
        ConnectionStatus.Unknown -> return

        ConnectionStatus.Checking -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(Res.string.settings_status_checking),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        ConnectionStatus.Connected -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(Res.string.settings_status_connected),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        ConnectionStatus.ErrorQuotaExhausted -> {
            val warningColor = Color(0xFFFF9800)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = warningColor,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(Res.string.settings_status_error_quota_exhausted),
                    style = MaterialTheme.typography.bodySmall,
                    color = warningColor,
                )
            }
        }

        ConnectionStatus.ErrorInvalidKey,
        ConnectionStatus.ErrorRateLimited,
        ConnectionStatus.ErrorConnectionFailed,
        ConnectionStatus.Error,
        -> {
            val errorMessage = when (status) {
                ConnectionStatus.ErrorInvalidKey -> stringResource(Res.string.settings_status_error_invalid_key)
                ConnectionStatus.ErrorRateLimited -> stringResource(Res.string.settings_status_error_rate_limited)
                ConnectionStatus.ErrorConnectionFailed -> stringResource(Res.string.settings_status_error_connection_failed)
                else -> stringResource(Res.string.settings_status_error)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ModelSelection(
    currentSelectedModel: SettingsModel?,
    models: ImmutableList<SettingsModel>,
    onClick: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    if (models.isNotEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = currentSelectedModel?.id ?: "",
                colors = outlineTextFieldColors(),
                onValueChange = {},
                readOnly = true,
                label = {
                    Text(
                        stringResource(Res.string.settings_model_label),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                },
                trailingIcon = {
                    Icon(
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                        imageVector = vectorResource(Res.drawable.ic_arrow_drop_down),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                },
            )
            // Transparent overlay to capture clicks reliably on all platforms
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable { expanded = true },
            )
        }
        if (expanded) {
            ModalBottomSheet(
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                onDismissRequest = {
                    expanded = false
                },
            ) {
                var searchQuery by remember { mutableStateOf("") }
                val filteredModels = if (searchQuery.isBlank()) {
                    models
                } else {
                    models.filter {
                        it.id.contains(searchQuery, ignoreCase = true) ||
                            it.subtitle.contains(searchQuery, ignoreCase = true)
                    }
                }
                if (models.size > 6) {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(stringResource(Res.string.settings_model_search))
                        },
                        singleLine = true,
                        colors = outlineTextFieldColors(),
                    )
                }
                LazyVerticalGrid(
                    GridCells.Adaptive(300.dp),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filteredModels, key = { it.id }) { model ->
                        ModelCard(
                            model = model,
                            onClick = {
                                onClick(model.id)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelCard(model: SettingsModel, onClick: () -> Unit) {
    val description = model.descriptionRes?.let { stringResource(it) } ?: model.description
    Card(
        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand).clip(CardDefaults.shape).clickable { onClick() },
        shape = CardDefaults.shape,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = model.id,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (model.subtitle.isNotEmpty()) {
                Text(
                    text = model.subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            description?.let {
                Text(
                    text = it,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(
    modifier: Modifier = Modifier,
    innerPadding: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand) else Modifier)
                .then(if (innerPadding) Modifier.padding(16.dp) else Modifier),
        ) {
            content()
        }
    }
}

@Composable
private fun GeneralContent(uiState: SettingsUiState) {
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
                    if (uiState.showUiScale) {
                        SettingsCard {
                            UiScaleSection(
                                uiScale = uiState.uiScale,
                                onChangeUiScale = uiState.onChangeUiScale,
                            )
                        }
                    }
                    SettingsCard {
                        SoulEditor(
                            soulText = uiState.soulText,
                            onSaveSoul = uiState.onSaveSoul,
                        )
                    }
                    SettingsCard {
                        ScheduledTaskList(
                            tasks = uiState.scheduledTasks,
                            onCancelTask = uiState.onCancelTask,
                            isSchedulingEnabled = uiState.isSchedulingEnabled,
                            onToggleScheduling = uiState.onToggleScheduling,
                        )
                    }
                    SettingsCard {
                        MemoryList(
                            memories = uiState.memories,
                            onDeleteMemory = uiState.onDeleteMemory,
                            isMemoryEnabled = uiState.isMemoryEnabled,
                            onToggleMemory = uiState.onToggleMemory,
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (uiState.showDaemonToggle) {
                        SettingsCard {
                            DaemonModeToggle(
                                isDaemonEnabled = uiState.isDaemonEnabled,
                                onToggleDaemon = uiState.onToggleDaemon,
                            )
                        }
                    }
                    SettingsCard {
                        HeartbeatSection(
                            isHeartbeatEnabled = uiState.isHeartbeatEnabled,
                            heartbeatIntervalMinutes = uiState.heartbeatIntervalMinutes,
                            activeHoursStart = uiState.heartbeatActiveHoursStart,
                            activeHoursEnd = uiState.heartbeatActiveHoursEnd,
                            heartbeatPrompt = uiState.heartbeatPrompt,
                            heartbeatLog = uiState.heartbeatLog,
                            onToggleHeartbeat = uiState.onToggleHeartbeat,
                            onChangeInterval = uiState.onChangeHeartbeatInterval,
                            onChangeActiveHours = uiState.onChangeHeartbeatActiveHours,
                            onSaveHeartbeatPrompt = uiState.onSaveHeartbeatPrompt,
                        )
                    }
                    if (uiState.showEmailToggle) {
                        SettingsCard {
                            EmailSection(
                                isEmailEnabled = uiState.isEmailEnabled,
                                emailAccounts = uiState.emailAccounts,
                                pollIntervalMinutes = uiState.emailPollIntervalMinutes,
                                onToggleEmail = uiState.onToggleEmail,
                                onRemoveAccount = uiState.onRemoveEmailAccount,
                                onChangePollInterval = uiState.onChangeEmailPollInterval,
                            )
                        }
                    }
                    SettingsCard {
                        ExportImportSection(
                            onExportSettings = uiState.onExportSettings,
                            onImportSettings = uiState.onImportSettings,
                        )
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (uiState.showUiScale) {
                    SettingsCard {
                        UiScaleSection(
                            uiScale = uiState.uiScale,
                            onChangeUiScale = uiState.onChangeUiScale,
                        )
                    }
                }
                if (uiState.showDaemonToggle) {
                    SettingsCard {
                        DaemonModeToggle(
                            isDaemonEnabled = uiState.isDaemonEnabled,
                            onToggleDaemon = uiState.onToggleDaemon,
                        )
                    }
                }
                SettingsCard {
                    SoulEditor(
                        soulText = uiState.soulText,
                        onSaveSoul = uiState.onSaveSoul,
                    )
                }
                SettingsCard {
                    MemoryList(
                        memories = uiState.memories,
                        onDeleteMemory = uiState.onDeleteMemory,
                        isMemoryEnabled = uiState.isMemoryEnabled,
                        onToggleMemory = uiState.onToggleMemory,
                    )
                }
                SettingsCard {
                    ScheduledTaskList(
                        tasks = uiState.scheduledTasks,
                        onCancelTask = uiState.onCancelTask,
                        isSchedulingEnabled = uiState.isSchedulingEnabled,
                        onToggleScheduling = uiState.onToggleScheduling,
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
                        onToggleHeartbeat = uiState.onToggleHeartbeat,
                        onChangeInterval = uiState.onChangeHeartbeatInterval,
                        onChangeActiveHours = uiState.onChangeHeartbeatActiveHours,
                        onSaveHeartbeatPrompt = uiState.onSaveHeartbeatPrompt,
                    )
                }
                if (uiState.showEmailToggle) {
                    SettingsCard {
                        EmailSection(
                            isEmailEnabled = uiState.isEmailEnabled,
                            emailAccounts = uiState.emailAccounts,
                            pollIntervalMinutes = uiState.emailPollIntervalMinutes,
                            onToggleEmail = uiState.onToggleEmail,
                            onRemoveAccount = uiState.onRemoveEmailAccount,
                            onChangePollInterval = uiState.onChangeEmailPollInterval,
                        )
                    }
                }
                SettingsCard {
                    ExportImportSection(
                        onExportSettings = uiState.onExportSettings,
                        onImportSettings = uiState.onImportSettings,
                    )
                }
            }
        }
    }
}

@Composable
private fun IntegrationsContent(
    splinterlandsViewModel: SplinterlandsViewModel = koinViewModel(),
) {
    val splinterlandsState by splinterlandsViewModel.state.collectAsState()
    LaunchedEffect(Unit) { splinterlandsViewModel.onScreenVisible() }

    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (splinterlandsState.showSplinterlandsSection) {
            SettingsCard {
                SplinterlandsSection(
                    isEnabled = splinterlandsState.isSplinterlandsEnabled,
                    accounts = splinterlandsState.splinterlandsAccounts,
                    instanceIds = splinterlandsState.splinterlandsInstanceIds,
                    addStatus = splinterlandsState.splinterlandsAddStatus,
                    battleLog = splinterlandsState.splinterlandsBattleLog,
                    availableServices = splinterlandsState.splinterlandsAvailableServices,
                    onToggle = splinterlandsState.onToggleSplinterlands,
                    onTestAndAddAccount = splinterlandsState.onTestAndAddSplinterlandsAccount,
                    onRemoveAccount = splinterlandsState.onRemoveSplinterlandsAccount,
                    onAddService = splinterlandsState.onAddSplinterlandsService,
                    onRemoveService = splinterlandsState.onRemoveSplinterlandsService,
                    onReorderServices = splinterlandsState.onReorderSplinterlandsServices,
                    onStartBattle = splinterlandsState.onStartSplinterlandsBattle,
                    onStopBattle = splinterlandsState.onStopSplinterlandsBattle,
                    onClearBattleLog = splinterlandsState.onClearSplinterlandsBattleLog,
                )
            }
        }
        SettingsCard {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(Res.string.settings_request_integration_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(Res.string.settings_request_integration_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { uriHandler.openUri("https://github.com/SimonSchubert/Kai/issues/new?template=integration_request.yml") },
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Text(stringResource(Res.string.settings_open_github_issue))
                }
            }
        }
    }
}

@Composable
private fun ExportImportSection(
    onExportSettings: () -> String,
    onImportSettings: (ByteArray, Set<ImportSection>, Boolean) -> ImportResult,
) {
    val isPreview = LocalInspectionMode.current
    val scope = rememberCoroutineScope()
    var importResult by remember { mutableStateOf<ImportResult?>(null) }
    var importPreview by remember { mutableStateOf<Pair<String, Map<ImportSection, String?>>?>(null) }

    val filePickerLauncher = if (!isPreview) {
        rememberFilePickerLauncher(
            type = FileKitType.File(extensions = listOf("json")),
        ) { file ->
            if (file != null) {
                scope.launch {
                    val bytes = file.readBytes()
                    try {
                        val jsonString = bytes.decodeToString()
                        val jsonObject = SharedJson.parseToJsonElement(jsonString).jsonObject
                        val detectedSections = detectImportSections(jsonObject)
                        importPreview = jsonString to detectedSections
                    } catch (_: Exception) {
                        importResult = ImportResult.Failure
                    }
                }
            }
        }
    } else {
        null
    }

    importPreview?.let { (jsonString, sectionDetails) ->
        ImportPreviewDialog(
            sectionDetails = sectionDetails,
            onConfirm = { selectedSections, replace ->
                importResult = onImportSettings(jsonString.encodeToByteArray(), selectedSections, replace)
                importPreview = null
            },
            onDismiss = { importPreview = null },
        )
    }

    Text(
        text = stringResource(Res.string.settings_export_import_title),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = stringResource(Res.string.settings_export_import_description),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = {
                importResult = null
                val json = onExportSettings()
                scope.launch {
                    saveFileToDevice(
                        bytes = json.encodeToByteArray(),
                        baseName = "kai-settings",
                        extension = "json",
                    )
                }
            },
            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
        ) {
            Text(stringResource(Res.string.settings_export))
        }
        OutlinedButton(
            onClick = {
                importResult = null
                filePickerLauncher?.launch()
            },
            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
        ) {
            Text(stringResource(Res.string.settings_import))
        }
    }
    if (importResult != null) {
        Spacer(Modifier.height(8.dp))
        val (text, color) = when (val result = importResult!!) {
            is ImportResult.Success -> stringResource(Res.string.settings_import_success) to MaterialTheme.colorScheme.primary
            is ImportResult.PartialSuccess -> stringResource(Res.string.settings_import_partial, result.errorCount) to MaterialTheme.colorScheme.primary
            is ImportResult.Failure -> stringResource(Res.string.settings_import_error) to MaterialTheme.colorScheme.error
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color,
        )
    }
}

@Composable
private fun ImportPreviewDialog(
    sectionDetails: Map<ImportSection, String?>,
    onConfirm: (Set<ImportSection>, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var replace by remember { mutableStateOf(true) }
    var selectedSections by remember { mutableStateOf(sectionDetails.keys) }
    val sortedEntries = remember(sectionDetails) { sectionDetails.entries.sortedBy { it.key } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(Res.string.settings_import_preview_title))
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(
                    verticalAlignment = CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { replace = !replace }
                        .pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(Res.string.settings_import_replace_all),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (replace) {
                            Text(
                                text = stringResource(Res.string.settings_import_replace_all_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = replace,
                        onCheckedChange = { replace = it },
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    )
                }
                Spacer(Modifier.height(12.dp))
                for ((section, count) in sortedEntries) {
                    Row(
                        verticalAlignment = CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedSections = if (section in selectedSections) {
                                    selectedSections - section
                                } else {
                                    selectedSections + section
                                }
                            }
                            .pointerHoverIcon(PointerIcon.Hand)
                            .padding(vertical = 4.dp),
                    ) {
                        Checkbox(
                            checked = section in selectedSections,
                            onCheckedChange = { checked ->
                                selectedSections = if (checked) selectedSections + section else selectedSections - section
                            },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = sectionDisplayName(section),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (count != null) {
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "($count)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedSections, replace) },
                enabled = selectedSections.isNotEmpty(),
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
            ) {
                Text(stringResource(Res.string.settings_import))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
            ) {
                Text(stringResource(Res.string.settings_mcp_cancel))
            }
        },
    )
}

@Composable
private fun sectionDisplayName(section: ImportSection): String = when (section) {
    ImportSection.SERVICES -> stringResource(Res.string.settings_import_section_services)
    ImportSection.SOUL -> stringResource(Res.string.settings_import_section_soul)
    ImportSection.MEMORY -> stringResource(Res.string.settings_import_section_memory)
    ImportSection.SCHEDULING -> stringResource(Res.string.settings_import_section_scheduling)
    ImportSection.HEARTBEAT -> stringResource(Res.string.settings_import_section_heartbeat)
    ImportSection.EMAIL -> stringResource(Res.string.settings_import_section_email)
    ImportSection.SPLINTERLANDS -> "Splinterlands"
    ImportSection.TOOLS -> stringResource(Res.string.settings_import_section_tools)
    ImportSection.MCP -> stringResource(Res.string.settings_import_section_mcp)
    ImportSection.SKILLS -> stringResource(Res.string.settings_import_section_skills)
}

@Composable
private fun ToolsContent(
    tools: ImmutableList<ToolInfo>,
    onToggleTool: (String, Boolean) -> Unit,
    mcpServers: ImmutableList<McpServerUiState>,
    onAddMcpServer: (String, String, Map<String, String>) -> Unit,
    onRemoveMcpServer: (String) -> Unit,
    onToggleMcpServer: (String, Boolean) -> Unit,
    onRefreshMcpServer: (String) -> Unit,
    showAddMcpServerDialog: Boolean,
    onShowAddMcpServerDialog: (Boolean) -> Unit,
    onAddPopularMcpServer: (PopularMcpServer) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // MCP Servers section
        McpServersSection(
            mcpServers = mcpServers,
            onAddMcpServer = onAddMcpServer,
            onRemoveMcpServer = onRemoveMcpServer,
            onToggleMcpServer = onToggleMcpServer,
            onRefreshMcpServer = onRefreshMcpServer,
            onToggleTool = onToggleTool,
            showAddDialog = showAddMcpServerDialog,
            onShowAddDialog = onShowAddMcpServerDialog,
            onAddPopularMcpServer = onAddPopularMcpServer,
        )

        Spacer(Modifier.height(24.dp))

        // Native tools section
        Text(
            text = stringResource(Res.string.settings_tools_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        if (tools.isEmpty()) {
            Text(
                text = stringResource(Res.string.settings_tools_none_available),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val columns = when {
                    maxWidth >= 800.dp -> 3
                    maxWidth >= 500.dp -> 2
                    else -> 1
                }
                val rows = tools.chunked(columns)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    rows.forEach { rowTools ->
                        Row(
                            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            rowTools.forEach { tool ->
                                ToolItem(
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    tool = tool,
                                    onToggle = { enabled -> onToggleTool(tool.id, enabled) },
                                )
                            }
                            // Fill empty slots so last row items don't stretch
                            repeat(columns - rowTools.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun McpServersSection(
    mcpServers: ImmutableList<McpServerUiState>,
    onAddMcpServer: (String, String, Map<String, String>) -> Unit,
    onRemoveMcpServer: (String) -> Unit,
    onToggleMcpServer: (String, Boolean) -> Unit,
    onRefreshMcpServer: (String) -> Unit,
    onToggleTool: (String, Boolean) -> Unit,
    showAddDialog: Boolean,
    onShowAddDialog: (Boolean) -> Unit,
    onAddPopularMcpServer: (PopularMcpServer) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.settings_mcp_servers),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(Res.string.settings_mcp_servers_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))

        for (server in mcpServers) {
            McpServerCard(
                server = server,
                onToggle = { enabled -> onToggleMcpServer(server.id, enabled) },
                onRemove = { onRemoveMcpServer(server.id) },
                onRefresh = { onRefreshMcpServer(server.id) },
                onToggleTool = onToggleTool,
            )
            Spacer(Modifier.height(8.dp))
        }

        OutlinedButton(
            onClick = { onShowAddDialog(true) },
            modifier = Modifier.align(Alignment.CenterHorizontally).pointerHoverIcon(PointerIcon.Hand),
        ) {
            Text(stringResource(Res.string.settings_mcp_add_server))
        }
    }

    if (showAddDialog) {
        AddMcpServerDialog(
            onDismiss = { onShowAddDialog(false) },
            onAdd = onAddMcpServer,
            onAddPopular = onAddPopularMcpServer,
        )
    }
}

@Composable
private fun McpServerCard(
    server: McpServerUiState,
    onToggle: (Boolean) -> Unit,
    onRemove: () -> Unit,
    onRefresh: () -> Unit,
    onToggleTool: (String, Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth().pointerHoverIcon(PointerIcon.Hand),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Status dot
                val statusColor = when (server.connectionStatus) {
                    McpConnectionStatus.Connected -> StatusColorConnected
                    McpConnectionStatus.Connecting -> StatusColorChecking
                    McpConnectionStatus.Error -> StatusColorError
                    McpConnectionStatus.Unknown -> StatusColorUnknown
                }
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(statusColor),
                )
                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = server.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = server.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }

                Switch(
                    checked = server.isEnabled,
                    onCheckedChange = onToggle,
                )

                Spacer(Modifier.width(8.dp))

                Icon(
                    imageVector = vectorResource(Res.drawable.ic_arrow_drop_down),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (expanded) {
                Spacer(Modifier.height(12.dp))

                // Status text
                val statusText = when (server.connectionStatus) {
                    McpConnectionStatus.Connected -> stringResource(Res.string.settings_mcp_status_connected)
                    McpConnectionStatus.Connecting -> stringResource(Res.string.settings_mcp_status_connecting)
                    McpConnectionStatus.Error -> stringResource(Res.string.settings_mcp_status_error)
                    McpConnectionStatus.Unknown -> ""
                }
                if (statusText.isNotEmpty()) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = when (server.connectionStatus) {
                            McpConnectionStatus.Error -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                }

                // Tools list
                if (server.tools.isNotEmpty()) {
                    for (tool in server.tools) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tool.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                                if (tool.description.isNotEmpty()) {
                                    Text(
                                        text = tool.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Switch(
                                checked = tool.isEnabled,
                                onCheckedChange = { enabled -> onToggleTool(tool.id, enabled) },
                            )
                        }
                    }
                } else if (server.connectionStatus == McpConnectionStatus.Connected) {
                    Text(
                        text = stringResource(Res.string.settings_mcp_no_tools),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onRefresh, modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)) {
                        Text(stringResource(Res.string.settings_mcp_refresh))
                    }
                    TextButton(onClick = onRemove, modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)) {
                        Text(
                            text = stringResource(Res.string.settings_mcp_remove),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddMcpServerDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, Map<String, String>) -> Unit,
    onAddPopular: (PopularMcpServer) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var authHeader by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(Res.string.settings_mcp_add_server),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(Res.string.settings_mcp_server_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(stringResource(Res.string.settings_mcp_server_url)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = authHeader,
                onValueChange = { authHeader = it },
                label = { Text(stringResource(Res.string.settings_mcp_auth_header)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = {
                        val headers = if (authHeader.isNotBlank()) {
                            mapOf("Authorization" to authHeader)
                        } else {
                            emptyMap()
                        }
                        onAdd(name, url, headers)
                    },
                    enabled = name.isNotBlank() && url.isNotBlank(),
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Text(stringResource(Res.string.settings_mcp_add))
                }
            }

            if (popularMcpServers.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(Res.string.settings_mcp_popular_servers),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                for (server in popularMcpServers) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CardDefaults.shape)
                            .clickable {
                                onAddPopular(server)
                            }
                            .pointerHoverIcon(PointerIcon.Hand),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        ),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = server.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = server.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ToolItem(
    tool: ToolInfo,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .clip(CardDefaults.shape)
            .clickable { onToggle(!tool.isEnabled) }
            .pointerHoverIcon(PointerIcon.Hand),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tool.nameRes?.let { stringResource(it) } ?: tool.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = tool.descriptionRes?.let { stringResource(it) } ?: tool.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.width(16.dp))

            Switch(
                checked = tool.isEnabled,
                onCheckedChange = onToggle,
            )
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
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
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

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth().height(250.dp),
            value = editedText,
            onValueChange = { if (it.length <= maxChars) editedText = it },
            label = {
                Text(
                    stringResource(Res.string.settings_soul),
                    color = MaterialTheme.colorScheme.onBackground,
                )
            },
            colors = outlineTextFieldColors(),
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
                modifier = Modifier.align(CenterHorizontally).pointerHoverIcon(PointerIcon.Hand),
            ) {
                Text(stringResource(Res.string.settings_soul_save))
            }
        }
    }

    if (showResetDialog) {
        androidx.compose.material3.AlertDialog(
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
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Text(stringResource(Res.string.settings_soul_reset))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetDialog = false },
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
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
    isMemoryEnabled: Boolean,
    onToggleMemory: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleMemory(!isMemoryEnabled) }
                .pointerHoverIcon(PointerIcon.Hand),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.settings_memories),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = isMemoryEnabled,
                onCheckedChange = onToggleMemory,
            )
        }
        Text(
            text = stringResource(Res.string.settings_memories_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        if (isMemoryEnabled) {
            memories.forEach { memory ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = memory.key,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = memory.content,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(
                        onClick = { onDeleteMemory(memory.key) },
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(Res.string.settings_memories_delete),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun ScheduledTaskList(
    tasks: ImmutableList<ScheduledTask>,
    onCancelTask: (String) -> Unit,
    isSchedulingEnabled: Boolean,
    onToggleScheduling: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleScheduling(!isSchedulingEnabled) }
                .pointerHoverIcon(PointerIcon.Hand),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.settings_scheduled_tasks),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = isSchedulingEnabled,
                onCheckedChange = onToggleScheduling,
            )
        }
        Text(
            text = stringResource(Res.string.settings_scheduled_tasks_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        if (isSchedulingEnabled && tasks.isNotEmpty()) {
            tasks.forEach { task ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = task.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        val subtitle = if (task.cron != null) {
                            "${task.status} - ${describeCron(task.cron)}"
                        } else {
                            val scheduledTime = Instant.fromEpochMilliseconds(task.scheduledAtEpochMs)
                                .toLocalDateTime(TimeZone.currentSystemDefault())
                            "${task.status} - $scheduledTime"
                        }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(
                        onClick = { onCancelTask(task.id) },
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(Res.string.settings_scheduled_tasks_cancel),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SkillsContent(
    skills: ImmutableList<SkillEntry>,
    onDeleteSkill: (String) -> Unit,
    isSkillsEnabled: Boolean,
    onToggleSkills: (Boolean) -> Unit,
    onExecuteSkill: (String, String?) -> Unit,
    executingSkillName: String?,
    skillExecutionResult: Pair<String, SkillExecutionResult>?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsCard {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleSkills(!isSkillsEnabled) }
                        .pointerHoverIcon(PointerIcon.Hand),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.settings_skills),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = isSkillsEnabled,
                        onCheckedChange = onToggleSkills,
                    )
                }
                Text(
                    text = stringResource(Res.string.settings_skills_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (isSkillsEnabled && skills.isEmpty()) {
            SettingsCard {
                Text(
                    text = stringResource(Res.string.settings_skills_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (isSkillsEnabled) {
            skills.forEach { skill ->
                SkillCard(
                    skill = skill,
                    onDeleteSkill = onDeleteSkill,
                    onExecuteSkill = onExecuteSkill,
                    isExecuting = executingSkillName == skill.name,
                    executionResult = skillExecutionResult?.takeIf { it.first == skill.name }?.second,
                )
            }
        }
    }
}

@Composable
private fun SkillCard(
    skill: SkillEntry,
    onDeleteSkill: (String) -> Unit,
    onExecuteSkill: (String, String?) -> Unit,
    isExecuting: Boolean,
    executionResult: SkillExecutionResult?,
) {
    var expanded by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    val prettyScript = remember(skill.script) { prettyPrintJs(skill.script) }

    SettingsCard(onClick = { expanded = !expanded }) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header — clickable to expand
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = skill.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = skill.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (skill.executionCount > 0) {
                        Text(
                            text = "Executed ${skill.executionCount}x",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(
                    onClick = { onDeleteSkill(skill.name) },
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(Res.string.settings_skills_delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (expanded) {
                Spacer(Modifier.height(8.dp))

                // Input + Execute
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                text = "Input (optional)",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                    )
                    Button(
                        onClick = { onExecuteSkill(skill.name, inputText.ifBlank { null }) },
                        enabled = !isExecuting,
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        if (isExecuting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Execute",
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Run")
                        }
                    }
                }

                // Result
                if (executionResult != null) {
                    Spacer(Modifier.height(8.dp))
                    val resultColor = if (executionResult.success) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                    val resultText = if (executionResult.success) {
                        executionResult.output.ifEmpty { "(no output)" }
                    } else {
                        executionResult.error ?: "Execution failed"
                    }
                    SelectionContainer {
                        Text(
                            text = resultText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            ),
                            color = resultColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(resultColor.copy(alpha = 0.08f))
                                .padding(12.dp),
                        )
                    }
                }

                // Readme
                if (skill.readme.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Documentation",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(4.dp))
                    SelectionContainer {
                        Text(
                            text = skill.readme,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                .padding(12.dp),
                        )
                    }
                }

                // Data
                if (skill.dataJson.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Data",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(4.dp))
                    val prettyData = remember(skill.dataJson) { prettyPrintJs(skill.dataJson) }
                    SelectionContainer {
                        Text(
                            text = prettyData,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(12.dp),
                        )
                    }
                }

                // Script source
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Script",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(4.dp))
                SelectionContainer {
                    Text(
                        text = prettyScript,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DaemonModeToggle(
    isDaemonEnabled: Boolean,
    onToggleDaemon: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleDaemon(!isDaemonEnabled) }
                .pointerHoverIcon(PointerIcon.Hand),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.settings_daemon_mode),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = isDaemonEnabled,
                onCheckedChange = onToggleDaemon,
            )
        }
        Text(
            text = stringResource(Res.string.settings_daemon_mode_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HeartbeatSection(
    isHeartbeatEnabled: Boolean,
    heartbeatIntervalMinutes: Int,
    activeHoursStart: Int,
    activeHoursEnd: Int,
    heartbeatPrompt: String,
    heartbeatLog: ImmutableList<HeartbeatLogEntry>,
    onToggleHeartbeat: (Boolean) -> Unit,
    onChangeInterval: (Int) -> Unit,
    onChangeActiveHours: (Int, Int) -> Unit,
    onSaveHeartbeatPrompt: (String) -> Unit,
) {
    val defaultPrompt = stringResource(Res.string.settings_heartbeat_default_prompt)
    val displayText = heartbeatPrompt.ifEmpty { defaultPrompt }
    var editedText by remember(displayText) { mutableStateOf(displayText) }
    val hasChanges = editedText != displayText
    val maxChars = 4000

    var showResetDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleHeartbeat(!isHeartbeatEnabled) }
                .pointerHoverIcon(PointerIcon.Hand),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.settings_heartbeat),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            if (heartbeatPrompt.isNotEmpty()) {
                IconButton(
                    onClick = { showResetDialog = true },
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay,
                        contentDescription = stringResource(Res.string.settings_soul_reset),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Switch(
                checked = isHeartbeatEnabled,
                onCheckedChange = onToggleHeartbeat,
            )
        }
        Text(
            text = stringResource(Res.string.settings_heartbeat_description, heartbeatIntervalMinutes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            Slider(
                value = intervalSliderValue,
                onValueChange = { intervalSliderValue = it },
                onValueChangeFinished = {
                    onChangeInterval(intervalPresets[intervalSliderValue.roundToInt()])
                },
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                valueRange = 0f..(intervalPresets.size - 1).toFloat(),
                steps = intervalPresets.size - 2,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                ),
                thumb = {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                    )
                },
                track = { sliderState ->
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        colors = SliderDefaults.colors(
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        drawStopIndicator = null,
                        drawTick = { _, _ -> },
                    )
                },
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
            RangeSlider(
                value = activeStart..activeEnd,
                onValueChange = { range ->
                    activeStart = range.start
                    activeEnd = range.endInclusive
                },
                onValueChangeFinished = {
                    onChangeActiveHours(activeStart.roundToInt(), activeEnd.roundToInt())
                },
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                valueRange = 0f..23f,
                steps = 22,
                startThumb = {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                    )
                },
                endThumb = {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                    )
                },
                track = { rangeSliderState ->
                    SliderDefaults.Track(
                        rangeSliderState = rangeSliderState,
                        colors = SliderDefaults.colors(
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        drawStopIndicator = null,
                        drawTick = { _, _ -> },
                    )
                },
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                value = editedText,
                onValueChange = { if (it.length <= maxChars) editedText = it },
                label = {
                    Text(
                        stringResource(Res.string.settings_heartbeat_prompt_label),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                },
                colors = outlineTextFieldColors(),
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
                    modifier = Modifier.align(CenterHorizontally).pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Text(stringResource(Res.string.settings_soul_save))
                }
            }

            if (heartbeatLog.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(Res.string.settings_heartbeat_recent),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
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
                                    maxLines = 1,
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
        androidx.compose.material3.AlertDialog(
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
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Text(stringResource(Res.string.settings_soul_reset))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetDialog = false },
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Text(stringResource(Res.string.settings_soul_reset_cancel))
                }
            },
        )
    }
}

@Composable
private fun EmailSection(
    isEmailEnabled: Boolean,
    emailAccounts: ImmutableList<EmailAccount>,
    pollIntervalMinutes: Int,
    onToggleEmail: (Boolean) -> Unit,
    onRemoveAccount: (String) -> Unit,
    onChangePollInterval: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleEmail(!isEmailEnabled) }
                .pointerHoverIcon(PointerIcon.Hand),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.settings_email),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = isEmailEnabled,
                onCheckedChange = onToggleEmail,
            )
        }
        Text(
            text = stringResource(Res.string.settings_email_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                Slider(
                    value = emailSliderValue,
                    onValueChange = { emailSliderValue = it },
                    onValueChangeFinished = {
                        onChangePollInterval(emailPresets[emailSliderValue.roundToInt()])
                    },
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                    valueRange = 0f..(emailPresets.size - 1).toFloat(),
                    steps = emailPresets.size - 2,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent,
                    ),
                    thumb = {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                        )
                    },
                    track = { sliderState ->
                        SliderDefaults.Track(
                            sliderState = sliderState,
                            colors = SliderDefaults.colors(
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                            drawStopIndicator = null,
                            drawTick = { _, _ -> },
                        )
                    },
                )

                Spacer(Modifier.height(12.dp))

                for (account in emailAccounts) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = account.email,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Text(
                                text = "${account.imapHost}:${account.imapPort}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            onClick = { onRemoveAccount(account.id) },
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(Res.string.settings_email_remove),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatHeartbeatTime(epochMs: Long): String {
    val instant = Instant.fromEpochMilliseconds(epochMs)
    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${local.day} ${local.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} ${local.hour}:${local.minute.toString().padStart(2, '0')}"
}

private fun describeCron(cron: String): String {
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

@Composable
private fun UiScaleSection(
    uiScale: Float,
    onChangeUiScale: (Float) -> Unit,
) {
    var sliderValue by remember(uiScale) { mutableStateOf(uiScale) }
    val steps = ((2.0f - 0.5f) / 0.1f).toInt() - 1 // 10% steps between 50% and 200%

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.settings_ui_scale),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "${(sliderValue * 100).toInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onChangeUiScale(sliderValue) },
            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
            valueRange = 0.5f..2.0f,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
            thumb = {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                )
            },
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    colors = SliderDefaults.colors(
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    drawStopIndicator = null,
                    drawTick = { _, _ -> },
                )
            },
        )
    }
}
