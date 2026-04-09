@file:OptIn(ExperimentalMaterial3Api::class)

package com.inspiredandroid.kai.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.mutableStateListOf
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
import com.inspiredandroid.kai.SandboxController
import com.inspiredandroid.kai.Version
import com.inspiredandroid.kai.data.EmailAccount
import com.inspiredandroid.kai.data.HeartbeatLogEntry
import com.inspiredandroid.kai.data.ImportSection
import com.inspiredandroid.kai.data.MemoryEntry
import com.inspiredandroid.kai.data.ScheduledTask
import com.inspiredandroid.kai.data.Service
import com.inspiredandroid.kai.data.ServiceEntry
import com.inspiredandroid.kai.data.SharedJson
import com.inspiredandroid.kai.data.TaskStatus
import com.inspiredandroid.kai.data.detectImportSections
import com.inspiredandroid.kai.formatFileSize
import com.inspiredandroid.kai.inference.DevicePerformance
import com.inspiredandroid.kai.inference.DownloadError
import com.inspiredandroid.kai.inference.LocalModel
import com.inspiredandroid.kai.inference.calculateDevicePerformance
import com.inspiredandroid.kai.inference.estimateGpuMemoryMb
import com.inspiredandroid.kai.mcp.PopularMcpServer
import com.inspiredandroid.kai.mcp.popularMcpServers
import com.inspiredandroid.kai.network.dtos.SponsorsResponseDto
import com.inspiredandroid.kai.network.tools.ToolInfo
import com.inspiredandroid.kai.saveFileToDevice
import com.inspiredandroid.kai.ui.KaiClearableTextField
import com.inspiredandroid.kai.ui.KaiOutlinedTextField
import com.inspiredandroid.kai.ui.components.KaiRangeSlider
import com.inspiredandroid.kai.ui.components.KaiSlider
import com.inspiredandroid.kai.ui.components.SettingsListItem
import com.inspiredandroid.kai.ui.components.VerticalScrollbarForGrid
import com.inspiredandroid.kai.ui.components.VerticalScrollbarForScroll
import com.inspiredandroid.kai.ui.handCursor
import com.inspiredandroid.kai.ui.icons.DragIndicator
import com.inspiredandroid.kai.ui.icons.Replay
import com.inspiredandroid.kai.ui.icons.Visibility
import com.inspiredandroid.kai.ui.icons.VisibilityOff
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.readBytes
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.default_soul
import kai.composeapp.generated.resources.error_unknown
import kai.composeapp.generated.resources.github_mark
import kai.composeapp.generated.resources.ic_arrow_drop_down
import kai.composeapp.generated.resources.litert_cancel
import kai.composeapp.generated.resources.litert_context_size
import kai.composeapp.generated.resources.litert_download
import kai.composeapp.generated.resources.litert_error_not_enough_disk_space
import kai.composeapp.generated.resources.litert_free_space
import kai.composeapp.generated.resources.litert_limitations
import kai.composeapp.generated.resources.litert_on_device_description
import kai.composeapp.generated.resources.litert_performance_good
import kai.composeapp.generated.resources.litert_performance_ok
import kai.composeapp.generated.resources.litert_performance_poor
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
import kai.composeapp.generated.resources.settings_dynamic_ui
import kai.composeapp.generated.resources.settings_dynamic_ui_description
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
import kai.composeapp.generated.resources.settings_heartbeat_model
import kai.composeapp.generated.resources.settings_heartbeat_model_default
import kai.composeapp.generated.resources.settings_heartbeat_prompt_label
import kai.composeapp.generated.resources.settings_heartbeat_recent
import kai.composeapp.generated.resources.settings_heartbeat_reset_confirm
import kai.composeapp.generated.resources.settings_import
import kai.composeapp.generated.resources.settings_import_error
import kai.composeapp.generated.resources.settings_import_partial
import kai.composeapp.generated.resources.settings_import_preview_title
import kai.composeapp.generated.resources.settings_import_replace_all
import kai.composeapp.generated.resources.settings_import_replace_all_description
import kai.composeapp.generated.resources.settings_import_section_conversations
import kai.composeapp.generated.resources.settings_import_section_email
import kai.composeapp.generated.resources.settings_import_section_heartbeat
import kai.composeapp.generated.resources.settings_import_section_mcp
import kai.composeapp.generated.resources.settings_import_section_memory
import kai.composeapp.generated.resources.settings_import_section_scheduling
import kai.composeapp.generated.resources.settings_import_section_services
import kai.composeapp.generated.resources.settings_import_section_soul
import kai.composeapp.generated.resources.settings_import_section_tools
import kai.composeapp.generated.resources.settings_import_success
import kai.composeapp.generated.resources.settings_mcp_add
import kai.composeapp.generated.resources.settings_mcp_add_header
import kai.composeapp.generated.resources.settings_mcp_add_server
import kai.composeapp.generated.resources.settings_mcp_cancel
import kai.composeapp.generated.resources.settings_mcp_header_key
import kai.composeapp.generated.resources.settings_mcp_header_value
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
import kai.composeapp.generated.resources.settings_reorder_content_description
import kai.composeapp.generated.resources.settings_request_integration_description
import kai.composeapp.generated.resources.settings_request_integration_title
import kai.composeapp.generated.resources.settings_sandbox_cancel
import kai.composeapp.generated.resources.settings_sandbox_description
import kai.composeapp.generated.resources.settings_sandbox_disk_usage
import kai.composeapp.generated.resources.settings_sandbox_install
import kai.composeapp.generated.resources.settings_sandbox_install_packages
import kai.composeapp.generated.resources.settings_sandbox_open_terminal
import kai.composeapp.generated.resources.settings_sandbox_title
import kai.composeapp.generated.resources.settings_sandbox_uninstall
import kai.composeapp.generated.resources.settings_sandbox_uninstall_confirm
import kai.composeapp.generated.resources.settings_scheduled_tasks
import kai.composeapp.generated.resources.settings_scheduled_tasks_cancel
import kai.composeapp.generated.resources.settings_scheduled_tasks_description
import kai.composeapp.generated.resources.settings_sign_in_copy_api_key_from
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
import kai.composeapp.generated.resources.settings_tab_sandbox
import kai.composeapp.generated.resources.settings_tab_services
import kai.composeapp.generated.resources.settings_tab_tools
import kai.composeapp.generated.resources.settings_tools_description
import kai.composeapp.generated.resources.settings_tools_none_available
import kai.composeapp.generated.resources.settings_ui_scale
import kai.composeapp.generated.resources.settings_version
import kai.composeapp.generated.resources.snackbar_email_removed
import kai.composeapp.generated.resources.snackbar_mcp_server_removed
import kai.composeapp.generated.resources.snackbar_memory_deleted
import kai.composeapp.generated.resources.snackbar_service_removed
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
import org.koin.compose.koinInject
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
    sandboxViewModel: SandboxViewModel = koinViewModel(),
    onNavigateBack: () -> Unit,
    navigationTabBar: (@Composable () -> Unit)? = null,
) {
    val uiState by viewModel.state.collectAsState()
    val sandboxState by sandboxViewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.onScreenVisible()
    }

    SettingsScreenContent(
        uiState = uiState,
        sandboxState = sandboxState,
        onToggleSandbox = sandboxViewModel::onToggleSandbox,
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
    sandboxState: SandboxUiState = SandboxUiState(),
    onToggleSandbox: (Boolean) -> Unit = {},
    onSetupSandbox: () -> Unit = {},
    onCancelSandbox: () -> Unit = {},
    onResetSandbox: () -> Unit = {},
    onInstallPackages: () -> Unit = {},
    onNavigateBack: () -> Unit = {},
    navigationTabBar: (@Composable () -> Unit)? = null,
    terminalPreviewLines: List<TerminalLine> = emptyList(),
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val undoLabel = stringResource(Res.string.snackbar_undo)
    val memoryDeletedMsg = stringResource(Res.string.snackbar_memory_deleted)
    val taskCancelledMsg = stringResource(Res.string.snackbar_task_cancelled)
    val emailRemovedMsg = stringResource(Res.string.snackbar_email_removed)
    val serviceRemovedMsg = stringResource(Res.string.snackbar_service_removed)
    val mcpServerRemovedMsg = stringResource(Res.string.snackbar_mcp_server_removed)

    LaunchedEffect(uiState.pendingDeletion) {
        val deletion = uiState.pendingDeletion ?: return@LaunchedEffect
        snackbarHostState.currentSnackbarData?.dismiss()
        val message = when (deletion) {
            is PendingDeletion.Memory -> memoryDeletedMsg
            is PendingDeletion.Task -> taskCancelledMsg
            is PendingDeletion.EmailAccount -> emailRemovedMsg
            is PendingDeletion.Service -> serviceRemovedMsg
            is PendingDeletion.McpServer -> mcpServerRemovedMsg
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
    val filteredServices = remember(uiState.configuredServices, pendingDeletion) {
        if (pendingDeletion is PendingDeletion.Service) uiState.configuredServices.filter { it.instanceId != pendingDeletion.instanceId }.toImmutableList() else uiState.configuredServices
    }
    val filteredMcpServers = remember(uiState.mcpServers, pendingDeletion) {
        if (pendingDeletion is PendingDeletion.McpServer) uiState.mcpServers.filter { it.id != pendingDeletion.serverId }.toImmutableList() else uiState.mcpServers
    }

    val filteredUiState = remember(uiState, filteredMemories, filteredTasks, filteredEmailAccounts, filteredServices, filteredMcpServers) {
        uiState.copy(
            memories = filteredMemories,
            scheduledTasks = filteredTasks,
            emailAccounts = filteredEmailAccounts,
            configuredServices = filteredServices,
            mcpServers = filteredMcpServers,
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
                SettingsTab.entries.filter { it != SettingsTab.Sandbox || sandboxState.showSandbox }
            }

            SettingsTabSelector(
                tabs = visibleTabs,
                currentTab = filteredUiState.currentTab,
                onSelectTab = filteredUiState.onSelectTab,
            )

            val isTerminalReady = filteredUiState.currentTab == SettingsTab.Sandbox && sandboxState.sandboxReady

            val settingsScrollState = rememberScrollState()
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (isTerminalReady) {
                    // Terminal fills entire space with its own internal scroll
                    Column(
                        Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        horizontalAlignment = CenterHorizontally,
                    ) {
                        TerminalTabContent(
                            sandboxState = sandboxState,
                            onToggleSandbox = onToggleSandbox,
                            onResetSandbox = onResetSandbox,
                            onInstallPackages = onInstallPackages,
                            previewLines = terminalPreviewLines,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                } else {
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

                                SettingsTab.Sandbox -> {
                                    // Not-ready state (install UI) - scrollable
                                    TerminalTabContent(
                                        sandboxState = sandboxState,
                                        onToggleSandbox = onToggleSandbox,
                                        onSetupSandbox = onSetupSandbox,
                                        onCancelSandbox = onCancelSandbox,
                                        onResetSandbox = onResetSandbox,
                                        onInstallPackages = onInstallPackages,
                                        previewLines = terminalPreviewLines,
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
    tabs: List<SettingsTab>,
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
                        .handCursor(),
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
                    .handCursor(),
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
                    .handCursor(),
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
                    .handCursor()
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
                    localAvailableModels = uiState.localAvailableModels,
                    totalDeviceMemoryBytes = uiState.totalDeviceMemoryBytes,
                    localFreeSpaceBytes = uiState.localFreeSpaceBytes,
                    localDownloadingModelId = uiState.localDownloadingModelId,
                    localDownloadProgress = uiState.localDownloadProgress,
                    localDownloadError = uiState.localDownloadError,
                    onDownloadLocalModel = uiState.onDownloadLocalModel,
                    onCancelLocalModelDownload = uiState.onCancelLocalModelDownload,
                    onDeleteLocalModel = uiState.onDeleteLocalModel,
                    onChangeModelContextTokens = uiState.onChangeModelContextTokens,
                    modelContextTokens = uiState.modelContextTokens,
                )
            }
        }
    }

    if (uiState.availableServicesToAdd.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { showAddServiceSheet = true }, modifier = Modifier.handCursor()) {
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
            val addServiceScrollState = rememberScrollState()
            Box {
                Column(modifier = Modifier.verticalScroll(addServiceScrollState).padding(16.dp)) {
                    uiState.availableServicesToAdd.forEach { service ->
                        val isSpecial = service.isOnDevice || service is Service.OpenAICompatible
                        Surface(
                            onClick = {
                                uiState.onAddService(service)
                                showAddServiceSheet = false
                            },
                            modifier = Modifier.fillMaxWidth().handCursor().then(
                                if (isSpecial) {
                                    Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                } else {
                                    Modifier
                                },
                            ),
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
                VerticalScrollbarForScroll(
                    scrollState = addServiceScrollState,
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                )
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
    localAvailableModels: ImmutableList<LocalModel> = persistentListOf(),
    totalDeviceMemoryBytes: Long = Long.MAX_VALUE,
    localFreeSpaceBytes: Long = 0L,
    localDownloadingModelId: String? = null,
    localDownloadProgress: Float? = null,
    localDownloadError: DownloadError? = null,
    onDownloadLocalModel: (LocalModel) -> Unit = {},
    onCancelLocalModelDownload: () -> Unit = {},
    onDeleteLocalModel: (String) -> Unit = {},
    onChangeModelContextTokens: (String, Int) -> Unit = { _, _ -> },
    modelContextTokens: Map<String, Int> = emptyMap(),
) {
    Column(
        modifier = Modifier
            .clip(CardDefaults.shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .fillMaxWidth()
            .clickable { onExpand() }
            .handCursor(),
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
                        contentDescription = stringResource(Res.string.settings_reorder_content_description),
                        modifier = dragHandleModifier.handCursor(),
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
                if (entry.service.isOnDevice) {
                    LiteRTSettings(
                        selectedModel = entry.selectedModel,
                        downloadedModels = entry.models,
                        availableModels = localAvailableModels,
                        totalDeviceMemoryBytes = totalDeviceMemoryBytes,
                        freeSpaceBytes = localFreeSpaceBytes,
                        downloadingModelId = localDownloadingModelId,
                        downloadProgress = localDownloadProgress,
                        downloadError = localDownloadError,
                        onSelectModel = onSelectModel,
                        onDownloadModel = onDownloadLocalModel,
                        onCancelDownload = onCancelLocalModelDownload,
                        onDeleteModel = onDeleteLocalModel,
                        onChangeModelContextTokens = onChangeModelContextTokens,
                        modelContextTokens = modelContextTokens,
                    )
                } else if (entry.service is Service.OpenAICompatible) {
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
                        modifier = Modifier.handCursor(),
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
    KaiClearableTextField(
        modifier = Modifier.let { if (testTag != null) it.testTag(testTag) else it },
        value = apiKey,
        onValueChange = onChangeApiKey,
        label = {
            Text(
                stringResource(Res.string.settings_api_key_label),
                color = MaterialTheme.colorScheme.onBackground,
            )
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
    KaiClearableTextField(
        value = baseUrl,
        onValueChange = onChangeBaseUrl,
        label = {
            Text(
                stringResource(Res.string.settings_base_url_label),
                color = MaterialTheme.colorScheme.onBackground,
            )
        },
        singleLine = true,
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

    KaiClearableTextField(
        value = apiKey,
        onValueChange = onChangeApiKey,
        label = {
            Text(
                stringResource(Res.string.settings_api_key_optional_label),
                color = MaterialTheme.colorScheme.onBackground,
            )
        },
        singleLine = true,
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
private fun LiteRTSettings(
    selectedModel: SettingsModel?,
    downloadedModels: ImmutableList<SettingsModel>,
    availableModels: ImmutableList<LocalModel>,
    totalDeviceMemoryBytes: Long,
    freeSpaceBytes: Long,
    downloadingModelId: String?,
    downloadProgress: Float?,
    downloadError: DownloadError?,
    onSelectModel: (String) -> Unit,
    onDownloadModel: (LocalModel) -> Unit,
    onCancelDownload: () -> Unit,
    onDeleteModel: (String) -> Unit,
    onChangeModelContextTokens: (String, Int) -> Unit,
    modelContextTokens: Map<String, Int>,
) {
    val downloadedIds = remember(downloadedModels) { downloadedModels.map { it.id }.toSet() }

    Text(
        text = stringResource(Res.string.litert_on_device_description),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(4.dp))

    Text(
        text = stringResource(Res.string.litert_limitations),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(12.dp))

    availableModels.forEach { model ->
        val isDownloaded = model.id in downloadedIds
        val isSelected = selectedModel?.id == model.id
        val isDownloading = downloadingModelId == model.id
        val steps = (model.maxContextTokens - model.defaultContextTokens) / 1024
        val storedContextTokens = modelContextTokens[model.id] ?: model.defaultContextTokens
        var contextSliderValue by remember(storedContextTokens) {
            mutableStateOf(((storedContextTokens - model.defaultContextTokens) / 1024).toFloat())
        }
        val contextTokens = model.defaultContextTokens + (contextSliderValue.roundToInt() * 1024)
        val estimatedMemoryMb = estimateGpuMemoryMb(model, contextTokens)
        val performance = calculateDevicePerformance(totalDeviceMemoryBytes, estimatedMemoryMb)

        Surface(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            shape = RoundedCornerShape(8.dp),
            tonalElevation = if (isSelected) 3.dp else 1.dp,
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isDownloaded) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelectModel(model.id) },
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = model.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = formatFileSize(model.sizeBytes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(8.dp))
                            DevicePerformanceLabel(performance)
                        }
                    }
                    if (isDownloaded) {
                        IconButton(
                            onClick = { onDeleteModel(model.id) },
                            modifier = Modifier.size(32.dp).handCursor(),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    } else if (!isDownloading) {
                        TextButton(
                            onClick = { onDownloadModel(model) },
                            modifier = Modifier.handCursor(),
                            enabled = downloadingModelId == null,
                        ) {
                            Text(stringResource(Res.string.litert_download))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(Res.string.litert_context_size, "${contextTokens / 1024}K"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                KaiSlider(
                    value = contextSliderValue,
                    onValueChange = { contextSliderValue = it },
                    onValueChangeFinished = {
                        onChangeModelContextTokens(model.id, contextTokens)
                    },
                    valueRange = 0f..steps.toFloat(),
                    steps = steps - 1,
                )
                if (isDownloading && downloadProgress != null) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${(downloadProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            onClick = onCancelDownload,
                            modifier = Modifier.handCursor(),
                        ) {
                            Text(
                                text = stringResource(Res.string.litert_cancel),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }
    }

    if (downloadError != null) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(
                when (downloadError) {
                    DownloadError.NOT_ENOUGH_DISK_SPACE -> Res.string.litert_error_not_enough_disk_space
                    DownloadError.NETWORK_ERROR -> Res.string.error_unknown
                    DownloadError.DOWNLOAD_INCOMPLETE -> Res.string.error_unknown
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }

    Spacer(Modifier.height(8.dp))

    Text(
        text = stringResource(Res.string.litert_free_space, formatFileSize(freeSpaceBytes)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DevicePerformanceLabel(performance: DevicePerformance) {
    when (performance) {
        DevicePerformance.GOOD -> Text(
            text = stringResource(Res.string.litert_performance_good),
            style = MaterialTheme.typography.labelSmall,
            color = StatusColorConnected,
        )

        DevicePerformance.OK -> Text(
            text = stringResource(Res.string.litert_performance_ok),
            style = MaterialTheme.typography.labelSmall,
            color = StatusColorChecking,
        )

        DevicePerformance.POOR -> Text(
            text = stringResource(Res.string.litert_performance_poor),
            style = MaterialTheme.typography.labelSmall,
            color = StatusColorError,
        )
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
            KaiOutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = currentSelectedModel?.id ?: "",
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
                        modifier = Modifier.handCursor(),
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
                    .handCursor()
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
                    KaiOutlinedTextField(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(stringResource(Res.string.settings_model_search))
                        },
                        singleLine = true,
                    )
                }
                val gridState = rememberLazyGridState()
                Box {
                    LazyVerticalGrid(
                        GridCells.Adaptive(300.dp),
                        state = gridState,
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
                    VerticalScrollbarForGrid(
                        gridState = gridState,
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelCard(model: SettingsModel, onClick: () -> Unit) {
    val description = model.descriptionRes?.let { stringResource(it) } ?: model.description
    Card(
        modifier = Modifier.handCursor().clip(CardDefaults.shape).clickable { onClick() },
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
                .then(if (onClick != null) Modifier.clickable(onClick = onClick).handCursor() else Modifier)
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
                        DynamicUiToggle(
                            isDynamicUiEnabled = uiState.isDynamicUiEnabled,
                            onToggleDynamicUi = uiState.onToggleDynamicUi,
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
                            heartbeatServiceEntries = uiState.heartbeatServiceEntries,
                            heartbeatSelectedInstanceId = uiState.heartbeatSelectedInstanceId,
                            onToggleHeartbeat = uiState.onToggleHeartbeat,
                            onChangeInterval = uiState.onChangeHeartbeatInterval,
                            onChangeActiveHours = uiState.onChangeHeartbeatActiveHours,
                            onSaveHeartbeatPrompt = uiState.onSaveHeartbeatPrompt,
                            onChangeHeartbeatService = uiState.onChangeHeartbeatService,
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
                    DynamicUiToggle(
                        isDynamicUiEnabled = uiState.isDynamicUiEnabled,
                        onToggleDynamicUi = uiState.onToggleDynamicUi,
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
                        heartbeatServiceEntries = uiState.heartbeatServiceEntries,
                        heartbeatSelectedInstanceId = uiState.heartbeatSelectedInstanceId,
                        onToggleHeartbeat = uiState.onToggleHeartbeat,
                        onChangeInterval = uiState.onChangeHeartbeatInterval,
                        onChangeActiveHours = uiState.onChangeHeartbeatActiveHours,
                        onSaveHeartbeatPrompt = uiState.onSaveHeartbeatPrompt,
                        onChangeHeartbeatService = uiState.onChangeHeartbeatService,
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
                    modifier = Modifier.handCursor(),
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
            modifier = Modifier.handCursor(),
        ) {
            Text(stringResource(Res.string.settings_export))
        }
        OutlinedButton(
            onClick = {
                importResult = null
                filePickerLauncher?.launch()
            },
            modifier = Modifier.handCursor(),
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
            val importScrollState = rememberScrollState()
            Box {
                Column(modifier = Modifier.verticalScroll(importScrollState)) {
                    Row(
                        verticalAlignment = CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { replace = !replace }
                            .handCursor(),
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
                            modifier = Modifier.handCursor(),
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
                                .handCursor()
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
                VerticalScrollbarForScroll(
                    scrollState = importScrollState,
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedSections, replace) },
                enabled = selectedSections.isNotEmpty(),
                modifier = Modifier.handCursor(),
            ) {
                Text(stringResource(Res.string.settings_import))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.handCursor(),
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
    ImportSection.CONVERSATIONS -> stringResource(Res.string.settings_import_section_conversations)
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
private fun TerminalTabContent(
    sandboxState: SandboxUiState,
    onToggleSandbox: (Boolean) -> Unit,
    onSetupSandbox: () -> Unit = {},
    onCancelSandbox: () -> Unit = {},
    onResetSandbox: () -> Unit,
    onInstallPackages: () -> Unit,
    previewLines: List<TerminalLine> = emptyList(),
    modifier: Modifier = Modifier,
) {
    if (sandboxState.sandboxReady) {
        val isPreview = LocalInspectionMode.current
        val sandboxController: SandboxController? = if (!isPreview) koinInject() else null
        var showResetDialog by remember { mutableStateOf(false) }
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Alpine Linux",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        if (sandboxState.sandboxDiskUsageMB > 0) {
                            Text(
                                text = stringResource(Res.string.settings_sandbox_disk_usage, sandboxState.sandboxDiskUsageMB),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Switch(
                        checked = sandboxState.isSandboxEnabled,
                        onCheckedChange = onToggleSandbox,
                    )
                }

                if (sandboxState.isWorking) {
                    SandboxProgressRow(null, sandboxState.sandboxStatusText, onCancelSandbox)
                }

                Spacer(Modifier.height(4.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!sandboxState.sandboxPackagesInstalled && !sandboxState.isWorking) {
                        OutlinedButton(onClick = onInstallPackages, modifier = Modifier.handCursor()) {
                            Text(stringResource(Res.string.settings_sandbox_install_packages))
                        }
                    }
                    OutlinedButton(onClick = { showResetDialog = true }, modifier = Modifier.handCursor()) {
                        Text(stringResource(Res.string.settings_sandbox_uninstall))
                    }
                }
            }

            if (showResetDialog) {
                AlertDialog(
                    onDismissRequest = { showResetDialog = false },
                    title = { Text(stringResource(Res.string.settings_sandbox_uninstall)) },
                    text = { Text(stringResource(Res.string.settings_sandbox_uninstall_confirm)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showResetDialog = false
                                onResetSandbox()
                            },
                            modifier = Modifier.handCursor(),
                        ) {
                            Text(stringResource(Res.string.settings_sandbox_uninstall))
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showResetDialog = false },
                            modifier = Modifier.handCursor(),
                        ) {
                            Text(stringResource(Res.string.settings_sandbox_cancel))
                        }
                    },
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(12.dp),
                color = TerminalDarkBg,
                tonalElevation = 2.dp,
            ) {
                TerminalContent(
                    sandboxController = sandboxController,
                    modifier = Modifier.fillMaxSize(),
                    darkBackground = true,
                    initialLines = previewLines,
                )
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxWidth()) {
            SettingsCard {
                Text(
                    text = "Alpine Linux",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = stringResource(Res.string.settings_sandbox_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (sandboxState.sandboxProgress != null) {
                    SandboxProgressRow(sandboxState.sandboxProgress, sandboxState.sandboxStatusText, onCancelSandbox)
                } else if (sandboxState.isWorking) {
                    SandboxProgressRow(null, sandboxState.sandboxStatusText, onCancelSandbox)
                } else if (sandboxState.hasError) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = sandboxState.sandboxStatusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                if (!sandboxState.isWorking) {
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onSetupSandbox, modifier = Modifier.handCursor()) {
                        Text(stringResource(Res.string.settings_sandbox_install))
                    }
                }
            }
        }
    }
}

@Composable
private fun SandboxProgressRow(progress: Float?, statusText: String, onCancel: () -> Unit) {
    Spacer(Modifier.height(8.dp))
    if (progress != null) {
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
    } else {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(4.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = CenterVertically,
    ) {
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onCancel, modifier = Modifier.handCursor()) {
            Text(stringResource(Res.string.settings_sandbox_cancel))
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
            modifier = Modifier.align(Alignment.CenterHorizontally).handCursor(),
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
        modifier = Modifier.fillMaxWidth().handCursor(),
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
                    TextButton(onClick = onRefresh, modifier = Modifier.handCursor()) {
                        Text(stringResource(Res.string.settings_mcp_refresh))
                    }
                    TextButton(onClick = onRemove, modifier = Modifier.handCursor()) {
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

private data class HeaderEntry(val key: String = "Authorization", val value: String = "")

@Composable
private fun AddMcpServerDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, Map<String, String>) -> Unit,
    onAddPopular: (PopularMcpServer) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    val headers = remember { mutableStateListOf(HeaderEntry()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        val mcpScrollState = rememberScrollState()
        Box {
            Column(
                modifier = Modifier
                    .verticalScroll(mcpScrollState)
                    .padding(16.dp),
            ) {
                Text(
                    text = stringResource(Res.string.settings_mcp_add_server),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(16.dp))

                KaiOutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.settings_mcp_server_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                KaiOutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(Res.string.settings_mcp_server_url)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))

                headers.forEachIndexed { index, entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        KaiOutlinedTextField(
                            value = entry.key,
                            onValueChange = { headers[index] = entry.copy(key = it) },
                            label = { Text(stringResource(Res.string.settings_mcp_header_key)) },
                            singleLine = true,
                            modifier = Modifier.weight(0.4f),
                        )
                        Spacer(Modifier.width(8.dp))
                        KaiOutlinedTextField(
                            value = entry.value,
                            onValueChange = { headers[index] = entry.copy(value = it) },
                            label = { Text(stringResource(Res.string.settings_mcp_header_value)) },
                            singleLine = true,
                            modifier = Modifier.weight(0.6f),
                        )
                        IconButton(
                            onClick = { headers.removeAt(index) },
                            modifier = Modifier.handCursor(),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = stringResource(Res.string.settings_mcp_remove),
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }

                TextButton(
                    onClick = { headers.add(HeaderEntry(key = "", value = "")) },
                    modifier = Modifier.handCursor(),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(Res.string.settings_mcp_add_header))
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = {
                            val headerMap = headers
                                .filter { it.key.isNotBlank() && it.value.isNotBlank() }
                                .associate { it.key.trim() to it.value.trim() }
                            onAdd(name, url, headerMap)
                        },
                        enabled = name.isNotBlank() && url.isNotBlank(),
                        modifier = Modifier.handCursor(),
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
                                .handCursor(),
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
            VerticalScrollbarForScroll(
                scrollState = mcpScrollState,
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            )
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
            .handCursor(),
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
            modifier = Modifier.fillMaxWidth().height(250.dp),
            value = editedText,
            onValueChange = { if (it.length <= maxChars) editedText = it },
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
    isMemoryEnabled: Boolean,
    onToggleMemory: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ToggleableHeadline(
            title = stringResource(Res.string.settings_memories),
            description = stringResource(Res.string.settings_memories_description),
            checked = isMemoryEnabled,
            onCheckedChange = onToggleMemory,
        )
        Spacer(Modifier.height(12.dp))

        if (isMemoryEnabled) {
            memories.forEach { memory ->
                SettingsListItem(
                    title = memory.key,
                    subtitle = memory.content,
                    onDelete = { onDeleteMemory(memory.key) },
                    deleteContentDescription = stringResource(Res.string.settings_memories_delete),
                    subtitleMaxLines = 3,
                )
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
        ToggleableHeadline(
            title = stringResource(Res.string.settings_scheduled_tasks),
            description = stringResource(Res.string.settings_scheduled_tasks_description),
            checked = isSchedulingEnabled,
            onCheckedChange = onToggleScheduling,
        )
        Spacer(Modifier.height(12.dp))

        if (isSchedulingEnabled && tasks.isNotEmpty()) {
            tasks.forEach { task ->
                val subtitle = if (task.cron != null) {
                    "${task.status} - ${describeCron(task.cron)}"
                } else {
                    val scheduledTime = Instant.fromEpochMilliseconds(task.scheduledAtEpochMs)
                        .toLocalDateTime(TimeZone.currentSystemDefault())
                    "${task.status} - $scheduledTime"
                }
                SettingsListItem(
                    title = task.description,
                    subtitle = subtitle,
                    onDelete = { onCancelTask(task.id) },
                    deleteContentDescription = stringResource(Res.string.settings_scheduled_tasks_cancel),
                )
                Spacer(Modifier.height(8.dp))
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
        ToggleableHeadline(
            title = stringResource(Res.string.settings_daemon_mode),
            description = stringResource(Res.string.settings_daemon_mode_description),
            checked = isDaemonEnabled,
            onCheckedChange = onToggleDaemon,
        )
    }
}

@Composable
private fun DynamicUiToggle(
    isDynamicUiEnabled: Boolean,
    onToggleDynamicUi: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ToggleableHeadline(
            title = stringResource(Res.string.settings_dynamic_ui),
            description = stringResource(Res.string.settings_dynamic_ui_description),
            checked = isDynamicUiEnabled,
            onCheckedChange = onToggleDynamicUi,
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
    heartbeatServiceEntries: ImmutableList<ServiceEntry>,
    heartbeatSelectedInstanceId: String?,
    onToggleHeartbeat: (Boolean) -> Unit,
    onChangeInterval: (Int) -> Unit,
    onChangeActiveHours: (Int, Int) -> Unit,
    onSaveHeartbeatPrompt: (String) -> Unit,
    onChangeHeartbeatService: (String?) -> Unit,
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
private fun EmailSection(
    isEmailEnabled: Boolean,
    emailAccounts: ImmutableList<EmailAccount>,
    pollIntervalMinutes: Int,
    onToggleEmail: (Boolean) -> Unit,
    onRemoveAccount: (String) -> Unit,
    onChangePollInterval: (Int) -> Unit,
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

                for (account in emailAccounts) {
                    SettingsListItem(
                        title = account.email,
                        subtitle = "${account.imapHost}:${account.imapPort}",
                        onDelete = { onRemoveAccount(account.id) },
                        deleteContentDescription = stringResource(Res.string.settings_email_remove),
                    )
                    Spacer(Modifier.height(8.dp))
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
        KaiSlider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onChangeUiScale(sliderValue) },
            valueRange = 0.5f..2.0f,
            steps = steps,
        )
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
