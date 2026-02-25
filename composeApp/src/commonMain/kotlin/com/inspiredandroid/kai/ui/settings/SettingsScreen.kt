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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
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
import androidx.compose.ui.unit.dp
import com.inspiredandroid.kai.BackIcon
import com.inspiredandroid.kai.Version
import com.inspiredandroid.kai.data.Identity
import com.inspiredandroid.kai.data.PredefinedIdentities
import com.inspiredandroid.kai.data.Service
import com.inspiredandroid.kai.network.tools.ToolInfo
import com.inspiredandroid.kai.ui.outlineTextFieldColors
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.github_mark
import kai.composeapp.generated.resources.ic_arrow_drop_down
import kai.composeapp.generated.resources.identity_creative_writer_name
import kai.composeapp.generated.resources.identity_creative_writer_prompt
import kai.composeapp.generated.resources.identity_short_and_direct_name
import kai.composeapp.generated.resources.identity_short_and_direct_prompt
import kai.composeapp.generated.resources.identity_tutor_name
import kai.composeapp.generated.resources.identity_tutor_prompt
import kai.composeapp.generated.resources.settings_ai_mistakes_warning
import kai.composeapp.generated.resources.settings_api_key_label
import kai.composeapp.generated.resources.settings_api_key_optional_label
import kai.composeapp.generated.resources.settings_base_url_label
import kai.composeapp.generated.resources.settings_become_sponsor
import kai.composeapp.generated.resources.settings_business_partnerships
import kai.composeapp.generated.resources.settings_business_partnerships_description
import kai.composeapp.generated.resources.settings_contact_sponsorship
import kai.composeapp.generated.resources.settings_free_tier_description
import kai.composeapp.generated.resources.settings_free_tier_title
import kai.composeapp.generated.resources.settings_identity
import kai.composeapp.generated.resources.settings_identity_add_custom
import kai.composeapp.generated.resources.settings_identity_create
import kai.composeapp.generated.resources.settings_identity_delete
import kai.composeapp.generated.resources.settings_identity_description
import kai.composeapp.generated.resources.settings_identity_edit
import kai.composeapp.generated.resources.settings_identity_name
import kai.composeapp.generated.resources.settings_identity_none
import kai.composeapp.generated.resources.settings_identity_openclaw_disabled
import kai.composeapp.generated.resources.settings_identity_reset
import kai.composeapp.generated.resources.settings_identity_save
import kai.composeapp.generated.resources.settings_identity_system_prompt
import kai.composeapp.generated.resources.settings_model_label
import kai.composeapp.generated.resources.settings_openai_compatible_or_other_service
import kai.composeapp.generated.resources.settings_openai_compatible_providers
import kai.composeapp.generated.resources.settings_openai_compatible_setup_ollama
import kai.composeapp.generated.resources.settings_show_topics
import kai.composeapp.generated.resources.settings_show_topics_description
import kai.composeapp.generated.resources.settings_sign_in_copy_api_key_from
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
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

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
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp).widthIn(max = 500.dp),
            horizontalAlignment = CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))

            when (uiState.currentTab) {
                SettingsTab.General -> {
                    GeneralContent(
                        showTopics = uiState.showTopics,
                        onToggleShowTopics = uiState.onToggleShowTopics,
                        identities = uiState.identities,
                        selectedIdentity = uiState.selectedIdentity,
                        onSelectIdentity = uiState.onSelectIdentity,
                        onSaveIdentity = uiState.onSaveIdentity,
                        onDeleteIdentity = uiState.onDeleteIdentity,
                        onResetIdentity = uiState.onResetIdentity,
                        isOpenClaw = uiState.currentService == Service.OpenClaw,
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

                        Service.OpenClaw -> {
                            OpenClawSettings(
                                gatewayUrl = uiState.baseUrl,
                                onChangeGatewayUrl = uiState.onChangeBaseUrl,
                                token = uiState.apiKey,
                                onChangeToken = uiState.onChangeApiKey,
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
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        SegmentedButton(
            selected = currentTab == SettingsTab.General,
            onClick = { onSelectTab(SettingsTab.General) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
        ) {
            Text(stringResource(Res.string.settings_tab_general))
        }
        SegmentedButton(
            selected = currentTab == SettingsTab.Services,
            onClick = { onSelectTab(SettingsTab.Services) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
        ) {
            Text(stringResource(Res.string.settings_tab_services))
        }
        SegmentedButton(
            selected = currentTab == SettingsTab.Tools,
            onClick = { onSelectTab(SettingsTab.Tools) },
            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
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
        trailingIcon = if (apiKeyFocused && apiKey.isNotEmpty()) {
            {
                IconButton(
                    onClick = { onChangeApiKey("") },
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            null
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
        trailingIcon = if (baseUrlFocused && baseUrl.isNotEmpty()) {
            {
                IconButton(
                    onClick = { onChangeBaseUrl("") },
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            null
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
        trailingIcon = if (apiKeyFocused && apiKey.isNotEmpty()) {
            {
                IconButton(
                    onClick = { onChangeApiKey("") },
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            null
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
private fun OpenClawSettings(
    gatewayUrl: String,
    onChangeGatewayUrl: (String) -> Unit,
    token: String,
    onChangeToken: (String) -> Unit,
    connectionStatus: ConnectionStatus,
) {
    var baseUrlFocused by remember { mutableStateOf(false) }
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth().onFocusChanged { baseUrlFocused = it.isFocused },
        value = gatewayUrl,
        onValueChange = onChangeGatewayUrl,
        label = {
            Text(
                "Gateway URL",
                color = MaterialTheme.colorScheme.onBackground,
            )
        },
        colors = outlineTextFieldColors(),
        singleLine = true,
        trailingIcon = if (baseUrlFocused && gatewayUrl.isNotEmpty()) {
            {
                IconButton(
                    onClick = { onChangeGatewayUrl("") },
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            null
        },
    )

    Spacer(Modifier.height(8.dp))

    var tokenFocused by remember { mutableStateOf(false) }
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth().onFocusChanged { tokenFocused = it.isFocused },
        value = token,
        onValueChange = onChangeToken,
        label = {
            Text(
                "Token",
                color = MaterialTheme.colorScheme.onBackground,
            )
        },
        colors = outlineTextFieldColors(),
        singleLine = true,
        trailingIcon = if (tokenFocused && token.isNotEmpty()) {
            {
                IconButton(
                    onClick = { onChangeToken("") },
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            null
        },
    )

    Spacer(Modifier.height(8.dp))

    ConnectionStatusIndicator(connectionStatus)

    Spacer(Modifier.height(12.dp))

    val linkColor = MaterialTheme.colorScheme.primary
    val setupText = remember(linkColor) {
        buildAnnotatedString {
            append("1. Setup Tailscale (")
            withLink(LinkAnnotation.Url(url = "https://tailscale.com")) {
                withStyle(style = SpanStyle(color = linkColor)) {
                    append("tailscale.com")
                }
            }
            append(") on both your openclaw server and this device.\n\n")
            append("2. Add the following to ~/.openclaw/openclaw.json to enable the HTTP chat endpoint:\n")
        }
    }
    Text(
        setupText,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(8.dp))

    SelectionContainer {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "\"gateway\": {\n  \"http\": {\n    \"endpoints\": {\n      \"chatCompletions\": {\n        \"enabled\": true\n      }\n    }\n  }\n}",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Spacer(Modifier.height(8.dp))

    Text(
        text = "3. Use your server\u2019s Tailscale IP as the Gateway URL (e.g. http://<tailscale-ip>:18789).\n\n" +
            "4. Copy your token from gateway.auth.token in ~/.openclaw/openclaw.json and paste it above.",
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
private fun GeneralContent(
    showTopics: Boolean,
    onToggleShowTopics: (Boolean) -> Unit,
    identities: List<Identity>,
    selectedIdentity: Identity?,
    onSelectIdentity: (String) -> Unit,
    onSaveIdentity: (Identity) -> Unit,
    onDeleteIdentity: (String) -> Unit,
    onResetIdentity: (String) -> Unit,
    isOpenClaw: Boolean = false,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Identity section
        if (isOpenClaw) {
            Text(
                text = stringResource(Res.string.settings_identity),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.settings_identity_openclaw_disabled),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            IdentitySection(
                identities = identities,
                selectedIdentity = selectedIdentity,
                onSelectIdentity = onSelectIdentity,
                onSaveIdentity = onSaveIdentity,
                onDeleteIdentity = onDeleteIdentity,
                onResetIdentity = onResetIdentity,
            )
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider(thickness = 0.5.dp)
        Spacer(Modifier.height(16.dp))

        // Show topics toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.settings_show_topics),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = stringResource(Res.string.settings_show_topics_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.width(16.dp))

            Switch(
                checked = showTopics,
                onCheckedChange = onToggleShowTopics,
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
            )
        }
    }
}

@Composable
private fun IdentitySection(
    identities: List<Identity>,
    selectedIdentity: Identity?,
    onSelectIdentity: (String) -> Unit,
    onSaveIdentity: (Identity) -> Unit,
    onDeleteIdentity: (String) -> Unit,
    onResetIdentity: (String) -> Unit,
) {
    var showEditSheet by remember { mutableStateOf(false) }
    var editingIdentity by remember { mutableStateOf<Identity?>(null) }

    Text(
        text = stringResource(Res.string.settings_identity),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = stringResource(Res.string.settings_identity_description),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))

    identities.forEach { identity ->
        IdentityItem(
            identity = identity,
            isSelected = identity.id == selectedIdentity?.id,
            onClick = { onSelectIdentity(identity.id) },
            onEdit = {
                editingIdentity = identity
                showEditSheet = true
            },
        )
    }

    Spacer(Modifier.height(8.dp))

    TextButton(
        onClick = {
            editingIdentity = null
            showEditSheet = true
        },
        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
    ) {
        Text(stringResource(Res.string.settings_identity_add_custom))
    }

    if (showEditSheet) {
        IdentityEditSheet(
            identity = editingIdentity,
            onDismiss = { showEditSheet = false },
            onSave = { identity ->
                onSaveIdentity(identity)
                showEditSheet = false
            },
            onDelete = { id ->
                onDeleteIdentity(id)
                showEditSheet = false
            },
            onReset = { id ->
                onResetIdentity(id)
                showEditSheet = false
            },
        )
    }
}

@Composable
private fun localizedIdentityName(identity: Identity): String = when (identity.id) {
    "none" -> stringResource(Res.string.settings_identity_none)
    "short_and_direct" -> stringResource(Res.string.identity_short_and_direct_name)
    "creative_writer" -> stringResource(Res.string.identity_creative_writer_name)
    "tutor" -> stringResource(Res.string.identity_tutor_name)
    else -> identity.name
}

@Composable
private fun localizedIdentityPrompt(identity: Identity): String = when (identity.id) {
    "short_and_direct" -> stringResource(Res.string.identity_short_and_direct_prompt)
    "creative_writer" -> stringResource(Res.string.identity_creative_writer_prompt)
    "tutor" -> stringResource(Res.string.identity_tutor_prompt)
    else -> identity.systemPrompt
}

@Composable
private fun IdentityItem(
    identity: Identity,
    isSelected: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    CircleShape,
                )
                .then(
                    if (!isSelected) {
                        Modifier.background(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            CircleShape,
                        )
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = localizedIdentityName(identity),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            val prompt = localizedIdentityPrompt(identity)
            if (prompt.isNotEmpty()) {
                Text(
                    text = prompt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (identity.id != "none") {
            IconButton(
                onClick = onEdit,
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
            ) {
                Icon(
                    imageVector = vectorResource(Res.drawable.ic_arrow_drop_down),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalTime::class)
@Composable
private fun IdentityEditSheet(
    identity: Identity?,
    onDismiss: () -> Unit,
    onSave: (Identity) -> Unit,
    onDelete: (String) -> Unit,
    onReset: (String) -> Unit,
) {
    val isCreating = identity == null
    val isPredefined = identity?.isPredefined == true
    val displayName = if (identity != null) localizedIdentityName(identity) else ""
    val displayPrompt = if (identity != null) localizedIdentityPrompt(identity) else ""
    var name by remember { mutableStateOf(displayName) }
    var systemPrompt by remember { mutableStateOf(displayPrompt) }
    val maxChars = 2000

    ModalBottomSheet(
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
        ) {
            Text(
                text = if (isCreating) {
                    stringResource(Res.string.settings_identity_create)
                } else {
                    stringResource(Res.string.settings_identity_edit)
                },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(Modifier.height(16.dp))

            if (!isPredefined) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = name,
                    onValueChange = { name = it },
                    label = {
                        Text(
                            stringResource(Res.string.settings_identity_name),
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    },
                    colors = outlineTextFieldColors(),
                    singleLine = true,
                )
                Spacer(Modifier.height(12.dp))
            }

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                value = systemPrompt,
                onValueChange = { if (it.length <= maxChars) systemPrompt = it },
                label = {
                    Text(
                        stringResource(Res.string.settings_identity_system_prompt),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                },
                colors = outlineTextFieldColors(),
            )

            Text(
                text = "${systemPrompt.length}/$maxChars",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End,
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (!isCreating) {
                    if (isPredefined) {
                        if (displayPrompt != systemPrompt) {
                            TextButton(
                                onClick = { onReset(identity.id) },
                                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                            ) {
                                Text(stringResource(Res.string.settings_identity_reset))
                            }
                        } else {
                            Spacer(Modifier.width(1.dp))
                        }
                    } else {
                        TextButton(
                            onClick = { onDelete(identity.id) },
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                        ) {
                            Text(
                                stringResource(Res.string.settings_identity_delete),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                Button(
                    onClick = {
                        val savedIdentity = if (isCreating) {
                            Identity(
                                id = "custom_${name.lowercase().replace(" ", "_")}_${Clock.System.now().toEpochMilliseconds()}",
                                name = name.trim(),
                                systemPrompt = systemPrompt.trim(),
                                isPredefined = false,
                            )
                        } else {
                            identity.copy(
                                name = if (isPredefined) identity.name else name.trim(),
                                systemPrompt = systemPrompt.trim(),
                            )
                        }
                        onSave(savedIdentity)
                    },
                    enabled = systemPrompt.isNotBlank() && (isPredefined || name.isNotBlank()),
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                ) {
                    Text(stringResource(Res.string.settings_identity_save))
                }
            }
        }
    }
}
