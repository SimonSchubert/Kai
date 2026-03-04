@file:OptIn(ExperimentalMaterial3Api::class)

package com.inspiredandroid.kai.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.inspiredandroid.kai.BackIcon
import com.inspiredandroid.kai.Version
import com.inspiredandroid.kai.data.HeartbeatLogEntry
import com.inspiredandroid.kai.data.MemoryEntry
import com.inspiredandroid.kai.data.ScheduledTask
import com.inspiredandroid.kai.data.Service
import com.inspiredandroid.kai.data.TaskStatus
import com.inspiredandroid.kai.network.tools.ToolInfo
import com.inspiredandroid.kai.ui.outlineTextFieldColors
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.default_soul
import kai.composeapp.generated.resources.github_mark
import kai.composeapp.generated.resources.ic_arrow_drop_down
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
import kai.composeapp.generated.resources.settings_free_tier_description
import kai.composeapp.generated.resources.settings_free_tier_title
import kai.composeapp.generated.resources.settings_heartbeat
import kai.composeapp.generated.resources.settings_heartbeat_default_prompt
import kai.composeapp.generated.resources.settings_heartbeat_description
import kai.composeapp.generated.resources.settings_heartbeat_prompt_label
import kai.composeapp.generated.resources.settings_heartbeat_recent
import kai.composeapp.generated.resources.settings_memories
import kai.composeapp.generated.resources.settings_memories_delete
import kai.composeapp.generated.resources.settings_memories_description
import kai.composeapp.generated.resources.settings_model_label
import kai.composeapp.generated.resources.settings_openai_compatible_or_other_service
import kai.composeapp.generated.resources.settings_openai_compatible_providers
import kai.composeapp.generated.resources.settings_openai_compatible_setup_ollama
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
import kai.composeapp.generated.resources.settings_status_checking
import kai.composeapp.generated.resources.settings_status_connected
import kai.composeapp.generated.resources.settings_status_error
import kai.composeapp.generated.resources.settings_status_error_connection_failed
import kai.composeapp.generated.resources.settings_status_error_invalid_key
import kai.composeapp.generated.resources.settings_status_error_quota_exhausted
import kai.composeapp.generated.resources.settings_status_error_rate_limited
import kai.composeapp.generated.resources.settings_tab_general
import kai.composeapp.generated.resources.settings_tab_services
import kai.composeapp.generated.resources.settings_tab_tools
import kai.composeapp.generated.resources.settings_tools_description
import kai.composeapp.generated.resources.settings_tools_none_available
import kai.composeapp.generated.resources.settings_tools_title
import kai.composeapp.generated.resources.settings_version
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Instant

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = koinViewModel(),
    onNavigateBack: () -> Unit,
) {
    val uiState by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.onScreenVisible()
    }

    SettingsScreenContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
    )
}

@Composable
fun SettingsScreenContent(
    uiState: SettingsUiState,
    onNavigateBack: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding().statusBarsPadding().imePadding(), horizontalAlignment = CenterHorizontally) {
        TopBar(onNavigateBack = onNavigateBack)

        SettingsTabSelector(
            currentTab = uiState.currentTab,
            onSelectTab = uiState.onSelectTab,
        )

        if (uiState.currentTab == SettingsTab.Services) {
            ServiceSelection(uiState.services, uiState.currentService, uiState.onSelectService)
        }

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))

            Column(
                Modifier.widthIn(max = 500.dp).fillMaxWidth().padding(horizontal = 16.dp),
                horizontalAlignment = CenterHorizontally,
            ) {
                when (uiState.currentTab) {
                    SettingsTab.General -> {
                        if (uiState.showDaemonToggle) {
                            DaemonModeToggle(
                                isDaemonEnabled = uiState.isDaemonEnabled,
                                onToggleDaemon = uiState.onToggleDaemon,
                            )
                            Spacer(Modifier.height(24.dp))
                        }
                        SoulEditor(
                            soulText = uiState.soulText,
                            onSaveSoul = uiState.onSaveSoul,
                        )
                        Spacer(Modifier.height(24.dp))
                        MemoryList(
                            memories = uiState.memories,
                            onDeleteMemory = uiState.onDeleteMemory,
                            isMemoryEnabled = uiState.isMemoryEnabled,
                            onToggleMemory = uiState.onToggleMemory,
                        )
                        Spacer(Modifier.height(24.dp))
                        ScheduledTaskList(
                            tasks = uiState.scheduledTasks,
                            onCancelTask = uiState.onCancelTask,
                            isSchedulingEnabled = uiState.isSchedulingEnabled,
                            onToggleScheduling = uiState.onToggleScheduling,
                        )
                        Spacer(Modifier.height(24.dp))
                        HeartbeatSection(
                            isHeartbeatEnabled = uiState.isHeartbeatEnabled,
                            heartbeatIntervalMinutes = uiState.heartbeatIntervalMinutes,
                            heartbeatPrompt = uiState.heartbeatPrompt,
                            heartbeatLog = uiState.heartbeatLog,
                            onToggleHeartbeat = uiState.onToggleHeartbeat,
                            onSaveHeartbeatPrompt = uiState.onSaveHeartbeatPrompt,
                        )
                    }

                    SettingsTab.Services -> {
                        when (uiState.currentService) {
                            Service.Free -> {
                                FreeSettings()
                            }

                            Service.Gemini -> {
                                ServiceSettings(
                                    apiKey = uiState.apiKey,
                                    onChangeApiKey = uiState.onChangeApiKey,
                                    apiKeyUrl = "https://aistudio.google.com/apikey",
                                    apiKeyUrlDisplay = "aistudio.google.com/apikey",
                                    selectedModel = uiState.selectedModel,
                                    models = uiState.models,
                                    onSelectModel = uiState.onSelectModel,
                                    connectionStatus = uiState.connectionStatus,
                                )
                            }

                            Service.OpenAI -> {
                                ServiceSettings(
                                    apiKey = uiState.apiKey,
                                    onChangeApiKey = uiState.onChangeApiKey,
                                    apiKeyUrl = "https://platform.openai.com/api-keys",
                                    apiKeyUrlDisplay = "platform.openai.com/api-keys",
                                    selectedModel = uiState.selectedModel,
                                    models = uiState.models,
                                    onSelectModel = uiState.onSelectModel,
                                    connectionStatus = uiState.connectionStatus,
                                )
                            }

                            Service.DeepSeek -> {
                                ServiceSettings(
                                    apiKey = uiState.apiKey,
                                    onChangeApiKey = uiState.onChangeApiKey,
                                    apiKeyUrl = "https://platform.deepseek.com/api_keys",
                                    apiKeyUrlDisplay = "platform.deepseek.com/api_keys",
                                    selectedModel = uiState.selectedModel,
                                    models = uiState.models,
                                    onSelectModel = uiState.onSelectModel,
                                    connectionStatus = uiState.connectionStatus,
                                )
                            }

                            Service.Mistral -> {
                                ServiceSettings(
                                    apiKey = uiState.apiKey,
                                    onChangeApiKey = uiState.onChangeApiKey,
                                    apiKeyUrl = "https://console.mistral.ai/api-keys",
                                    apiKeyUrlDisplay = "console.mistral.ai/api-keys",
                                    selectedModel = uiState.selectedModel,
                                    models = uiState.models,
                                    onSelectModel = uiState.onSelectModel,
                                    connectionStatus = uiState.connectionStatus,
                                )
                            }

                            Service.Groq -> {
                                ServiceSettings(
                                    apiKey = uiState.apiKey,
                                    onChangeApiKey = uiState.onChangeApiKey,
                                    apiKeyUrl = "https://console.groq.com/keys",
                                    apiKeyUrlDisplay = "console.groq.com/keys",
                                    selectedModel = uiState.selectedModel,
                                    models = uiState.models,
                                    onSelectModel = uiState.onSelectModel,
                                    connectionStatus = uiState.connectionStatus,
                                    testTag = "api_key",
                                )
                            }

                            Service.XAI -> {
                                ServiceSettings(
                                    apiKey = uiState.apiKey,
                                    onChangeApiKey = uiState.onChangeApiKey,
                                    apiKeyUrl = "https://console.x.ai",
                                    apiKeyUrlDisplay = "console.x.ai",
                                    selectedModel = uiState.selectedModel,
                                    models = uiState.models,
                                    onSelectModel = uiState.onSelectModel,
                                    connectionStatus = uiState.connectionStatus,
                                )
                            }

                            Service.OpenRouter -> {
                                ServiceSettings(
                                    apiKey = uiState.apiKey,
                                    onChangeApiKey = uiState.onChangeApiKey,
                                    apiKeyUrl = "https://openrouter.ai/settings/keys",
                                    apiKeyUrlDisplay = "openrouter.ai/settings/keys",
                                    selectedModel = uiState.selectedModel,
                                    models = uiState.models,
                                    onSelectModel = uiState.onSelectModel,
                                    connectionStatus = uiState.connectionStatus,
                                )
                            }

                            Service.Nvidia -> {
                                ServiceSettings(
                                    apiKey = uiState.apiKey,
                                    onChangeApiKey = uiState.onChangeApiKey,
                                    apiKeyUrl = "https://build.nvidia.com/settings/api-keys",
                                    apiKeyUrlDisplay = "build.nvidia.com/settings/api-keys",
                                    selectedModel = uiState.selectedModel,
                                    models = uiState.models,
                                    onSelectModel = uiState.onSelectModel,
                                    connectionStatus = uiState.connectionStatus,
                                )
                            }

                            Service.OpenAICompatible -> {
                                OpenAICompatibleSettings(
                                    baseUrl = uiState.baseUrl,
                                    onChangeBaseUrl = uiState.onChangeBaseUrl,
                                    apiKey = uiState.apiKey,
                                    onChangeApiKey = uiState.onChangeApiKey,
                                    selectedModel = uiState.selectedModel,
                                    models = uiState.models,
                                    onSelectModel = uiState.onSelectModel,
                                    connectionStatus = uiState.connectionStatus,
                                )
                            }
                        }
                    }

                    SettingsTab.Tools -> {
                        ToolsContent(
                            tools = uiState.tools,
                            onToggleTool = uiState.onToggleTool,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
            }

            Spacer(Modifier.weight(1f))

            BottomInfo()
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
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val count = 3
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        SegmentedButton(
            selected = currentTab == SettingsTab.General,
            onClick = { onSelectTab(SettingsTab.General) },
            shape = SegmentedButtonDefaults.itemShape(index = if (isRtl) count - 1 else 0, count = count),
            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
        ) {
            Text(stringResource(Res.string.settings_tab_general))
        }
        SegmentedButton(
            selected = currentTab == SettingsTab.Services,
            onClick = { onSelectTab(SettingsTab.Services) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = count),
            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
        ) {
            Text(stringResource(Res.string.settings_tab_services))
        }
        SegmentedButton(
            selected = currentTab == SettingsTab.Tools,
            onClick = { onSelectTab(SettingsTab.Tools) },
            shape = SegmentedButtonDefaults.itemShape(index = if (isRtl) 0 else count - 1, count = count),
            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
        ) {
            Text(stringResource(Res.string.settings_tab_tools))
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

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(Res.string.settings_version, Version.appVersion),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.width(8.dp))

        val uriHandler = LocalUriHandler.current
        Icon(
            modifier = Modifier
                .clip(CircleShape)
                .size(20.dp)
                .clickable(onClick = {
                    uriHandler.openUri("https://github.com/SimonSchubert/Kai")
                })
                .pointerHoverIcon(PointerIcon.Hand),
            painter = painterResource(Res.drawable.github_mark),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun FreeSettings() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
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
private fun ServiceSettings(
    apiKey: String,
    onChangeApiKey: (String) -> Unit,
    apiKeyUrl: String,
    apiKeyUrlDisplay: String,
    selectedModel: SettingsModel?,
    models: List<SettingsModel>,
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

    if (connectionStatus == ConnectionStatus.Connected) {
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
    models: List<SettingsModel>,
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
    models: List<SettingsModel>,
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
                LazyVerticalGrid(
                    GridCells.Adaptive(300.dp),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(models, key = { it.id }) { model ->
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
            Text(
                text = model.subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
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
private fun ServiceSelection(services: List<Service>, currentService: Service, onChanged: (Service) -> Unit) {
    val selectedIndex = services.indexOf(currentService).coerceAtLeast(0)
    PrimaryScrollableTabRow(
        selectedTabIndex = selectedIndex,
        edgePadding = 0.dp,
        containerColor = MaterialTheme.colorScheme.background,
        divider = {},
    ) {
        services.forEach { service ->
            Tab(
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                selected = service == currentService,
                onClick = { onChanged(service) },
                text = {
                    Text(
                        service.displayName,
                        color = if (service == currentService) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun ToolsContent(
    tools: List<ToolInfo>,
    onToggleTool: (String, Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
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
            tools.forEach { tool ->
                ToolItem(
                    tool = tool,
                    onToggle = { enabled -> onToggleTool(tool.id, enabled) },
                )
            }
        }
    }
}

@Composable
private fun ToolItem(
    tool: ToolInfo,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tool.nameRes?.let { stringResource(it) } ?: tool.name,
                style = MaterialTheme.typography.bodyLarge,
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
            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
        )
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
                TextButton(onClick = {
                    showResetDialog = false
                    onSaveSoul("")
                    editedText = localizedDefault
                }) {
                    Text(stringResource(Res.string.settings_soul_reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(Res.string.settings_soul_reset_cancel))
                }
            },
        )
    }
}

@Composable
private fun MemoryList(
    memories: List<MemoryEntry>,
    onDeleteMemory: (String) -> Unit,
    isMemoryEnabled: Boolean,
    onToggleMemory: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
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
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = memory.key,
                            style = MaterialTheme.typography.bodyMedium,
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
                            imageVector = Icons.Default.Clear,
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
    tasks: List<ScheduledTask>,
    onCancelTask: (String) -> Unit,
    isSchedulingEnabled: Boolean,
    onToggleScheduling: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
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
                    verticalAlignment = Alignment.Top,
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
                    if (task.status == TaskStatus.PENDING) {
                        IconButton(
                            onClick = { onCancelTask(task.id) },
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = stringResource(Res.string.settings_scheduled_tasks_cancel),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
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
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
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
    heartbeatPrompt: String,
    heartbeatLog: List<HeartbeatLogEntry>,
    onToggleHeartbeat: (Boolean) -> Unit,
    onSaveHeartbeatPrompt: (String) -> Unit,
) {
    val defaultPrompt = stringResource(Res.string.settings_heartbeat_default_prompt)
    val displayText = heartbeatPrompt.ifEmpty { defaultPrompt }
    var editedText by remember(displayText) { mutableStateOf(displayText) }
    val hasChanges = editedText != displayText
    val maxChars = 4000

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.settings_heartbeat),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = isHeartbeatEnabled,
                onCheckedChange = onToggleHeartbeat,
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
            )
        }
        Text(
            text = stringResource(Res.string.settings_heartbeat_description, heartbeatIntervalMinutes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (isHeartbeatEnabled) {
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
                        Text(
                            text = formatHeartbeatTime(entry.timestampEpochMs),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun formatHeartbeatTime(epochMs: Long): String {
    val instant = kotlinx.datetime.Instant.fromEpochMilliseconds(epochMs)
    val local = instant.toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
    return "${local.dayOfMonth} ${local.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} ${local.hour}:${local.minute.toString().padStart(2, '0')}"
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
