@file:OptIn(
    ExperimentalFoundationApi::class,
)

package com.inspiredandroid.kai.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.BottomCenter
import androidx.compose.ui.Alignment.Companion.CenterEnd
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.inspiredandroid.kai.BackIcon
import com.inspiredandroid.kai.data.Service
import com.inspiredandroid.kai.getBackgroundDispatcher
import com.inspiredandroid.kai.onDragAndDropEventDropped
import com.inspiredandroid.kai.ui.chat.composables.BotMessage
import com.inspiredandroid.kai.ui.chat.composables.ChatHistorySheet
import com.inspiredandroid.kai.ui.chat.composables.CircleIconButton
import com.inspiredandroid.kai.ui.chat.composables.EmptyState
import com.inspiredandroid.kai.ui.chat.composables.ErrorMessage
import com.inspiredandroid.kai.ui.chat.composables.HeartbeatBanner
import com.inspiredandroid.kai.ui.chat.composables.QuestionInput
import com.inspiredandroid.kai.ui.chat.composables.ServiceSelector
import com.inspiredandroid.kai.ui.chat.composables.TopBar
import com.inspiredandroid.kai.ui.chat.composables.TrailingIcon
import com.inspiredandroid.kai.ui.chat.composables.UserMessage
import com.inspiredandroid.kai.ui.chat.composables.WaitingResponseRow
import com.inspiredandroid.kai.ui.components.LogoAnimation
import com.inspiredandroid.kai.ui.components.VerticalScrollbarForList
import com.inspiredandroid.kai.ui.dynamicui.FrozenSubmission
import com.inspiredandroid.kai.ui.dynamicui.KaiUiRenderer
import com.inspiredandroid.kai.ui.dynamicui.toSpeakableText
import com.inspiredandroid.kai.ui.handCursor
import com.inspiredandroid.kai.ui.markdown.KaiUiBlock
import com.inspiredandroid.kai.ui.markdown.parseMarkdown
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.fallback_answered_by
import kai.composeapp.generated.resources.ic_stop
import kai.composeapp.generated.resources.interactive_back_content_description
import kai.composeapp.generated.resources.interactive_exit_content_description
import kai.composeapp.generated.resources.interactive_title
import kai.composeapp.generated.resources.interactive_ui_parsing_failed
import kai.composeapp.generated.resources.interactive_welcome_subtitle
import kai.composeapp.generated.resources.interactive_welcome_title
import kai.composeapp.generated.resources.scroll_to_bottom_content_description
import kai.composeapp.generated.resources.snackbar_conversation_deleted
import kai.composeapp.generated.resources.snackbar_undo
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import nl.marc_apps.tts.TextToSpeechInstance
import nl.marc_apps.tts.errors.TextToSpeechSynthesisInterruptedError
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ChatScreen(
    viewModel: ChatViewModel = koinViewModel(),
    textToSpeech: TextToSpeechInstance?,
    onNavigateToSettings: () -> Unit,
    navigationTabBar: (@Composable () -> Unit)? = null,
) {
    val uiState by viewModel.state.collectAsState()

    ChatScreenContent(
        uiState = uiState,
        textToSpeech = textToSpeech,
        onNavigateToSettings = onNavigateToSettings,
        navigationTabBar = navigationTabBar,
    )
}

@Composable
fun ChatScreenContent(
    uiState: ChatUiState,
    textToSpeech: TextToSpeechInstance? = null,
    onNavigateToSettings: () -> Unit = {},
    navigationTabBar: (@Composable () -> Unit)? = null,
) {
    if (uiState.isInteractiveMode) {
        InteractiveModeScreen(uiState = uiState)
    } else {
        ChatModeScreen(
            uiState = uiState,
            textToSpeech = textToSpeech,
            onNavigateToSettings = onNavigateToSettings,
            navigationTabBar = navigationTabBar,
        )
    }
}

// --- Interactive Mode ---

@Composable
private fun InteractiveModeScreen(uiState: ChatUiState) {
    val snackbarHostState = remember { SnackbarHostState() }

    // Intercept system back to exit interactive mode instead of closing the app
    com.inspiredandroid.kai.PlatformBackHandler(enabled = true) {
        uiState.actions.exitInteractiveMode()
    }

    val hasAssistantResponse = remember(uiState.history) {
        uiState.history.any { it.role == History.Role.ASSISTANT }
    }
    var inputExpanded by remember { mutableStateOf(true) }
    LaunchedEffect(hasAssistantResponse, uiState.history.size) {
        if (hasAssistantResponse) inputExpanded = false
    }
    val showFullInput = inputExpanded && !uiState.isLoading

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .statusBarsPadding()
            .imePadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            // Top bar with back and close
            InteractiveModeTopBar(
                onBack = uiState.actions.goBackInteractiveMode,
                onExit = uiState.actions.exitInteractiveMode,
                isLoading = uiState.isLoading,
                showBack = hasAssistantResponse,
            )

            Box(
                modifier = Modifier.fillMaxSize(),
            ) {
                // Content area fills remaining space
                if (!hasAssistantResponse && !uiState.isLoading) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        LogoAnimation()
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = stringResource(Res.string.interactive_welcome_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = stringResource(Res.string.interactive_welcome_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                } else {
                    SelectionContainer {
                        InteractiveModeContent(
                            uiState = uiState,
                            modifier = Modifier.fillMaxSize(),
                            bottomPadding = 88.dp,
                        )
                    }
                }

                // Full QuestionInput stays in the column flow
                if (showFullInput) {
                    QuestionInput(
                        modifier = Modifier.align(Alignment.BottomEnd),
                        files = uiState.files,
                        addFile = uiState.actions.addFile,
                        removeFile = uiState.actions.removeFile,
                        ask = {
                            inputExpanded = false
                            uiState.actions.ask(it)
                        },
                        supportedFileExtensions = uiState.supportedFileExtensions,
                        isLoading = uiState.isLoading,
                        cancel = uiState.actions.cancel,
                        availableServices = uiState.availableServices,
                        onSelectService = uiState.actions.selectService,
                    )
                }
            }
        }

        // Collapsed pill floats over content at the bottom-end
        if (!showFullInput) {
            val gradientBrush = com.inspiredandroid.kai.ui.gradientBrush
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .height(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(28.dp))
                    .border(
                        BorderStroke(2.dp, gradientBrush),
                        RoundedCornerShape(28.dp),
                    )
                    .handCursor()
                    .padding(horizontal = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (uiState.isLoading) {
                    TrailingIcon(
                        icon = Res.drawable.ic_stop,
                        onClick = { uiState.actions.cancel() },
                    )
                } else {
                    CircleIconButton(
                        icon = Icons.Default.Edit,
                        onClick = { inputExpanded = true },
                    )
                }
                if (uiState.availableServices.size > 1) {
                    ServiceSelector(
                        services = uiState.availableServices,
                        onSelectService = uiState.actions.selectService,
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(BottomCenter).padding(bottom = 80.dp),
        ) { data ->
            Snackbar(snackbarData = data)
        }
    }
}

@Composable
private fun InteractiveModeTopBar(
    onBack: () -> Unit,
    onExit: () -> Unit,
    isLoading: Boolean,
    showBack: Boolean,
) {
    val iconColor = MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        if (showBack) {
            IconButton(
                onClick = onBack,
                enabled = !isLoading,
                modifier = Modifier.handCursor(),
            ) {
                Icon(
                    BackIcon,
                    contentDescription = stringResource(Res.string.interactive_back_content_description),
                    tint = iconColor,
                )
            }
        } else {
            // Placeholder to keep close button aligned right
            Spacer(Modifier.size(48.dp))
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = stringResource(Res.string.interactive_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.weight(1f))
        IconButton(
            onClick = onExit,
            modifier = Modifier.handCursor(),
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(Res.string.interactive_exit_content_description),
                tint = iconColor,
            )
        }
    }
}

@Composable
private fun InteractiveModeContent(
    uiState: ChatUiState,
    modifier: Modifier = Modifier,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val lastAssistant = remember(uiState.history) { uiState.history.lastRenderedAssistant() }

    Box(modifier.fillMaxWidth()) {
        if (uiState.isLoading && lastAssistant == null) {
            // First load — show centered loading
            Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                WaitingResponseRow(
                    executingTools = remember { kotlinx.collections.immutable.persistentListOf() },
                )
            }
        } else if (lastAssistant != null) {
            val contentId = lastAssistant.id
            AnimatedContent(
                targetState = contentId,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                modifier = Modifier.fillMaxSize(),
            ) { _ ->
                val blocks = remember(lastAssistant.content) { parseMarkdown(lastAssistant.content).blocks }
                val uiBlocks = blocks.filterIsInstance<KaiUiBlock>()

                if (uiBlocks.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp + bottomPadding),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (block in uiBlocks) {
                            KaiUiRenderer(
                                node = block.node,
                                isInteractive = !uiState.isLoading,
                                onCallback = { event, data ->
                                    uiState.actions.submitUiCallback(event, data)
                                },
                                wrapInCard = false,
                            )
                        }
                    }
                } else if (uiState.error == null) {
                    // AI responded with no valid kai-ui AND there's no API error underneath —
                    // this is a genuine parse failure (retries exhausted). When an API error is
                    // set, the ErrorMessage overlay below takes over with the correct message.
                    Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        Column(horizontalAlignment = CenterHorizontally) {
                            Text(
                                text = stringResource(Res.string.interactive_ui_parsing_failed),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        // Error state
        uiState.error?.let { error ->
            Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                ErrorMessage(error = error, retry = uiState.actions.retry)
            }
        }
    }
}

// --- Regular Chat Mode ---

@Composable
private fun ChatModeScreen(
    uiState: ChatUiState,
    textToSpeech: TextToSpeechInstance?,
    onNavigateToSettings: () -> Unit,
    navigationTabBar: (@Composable () -> Unit)?,
) {
    var showHistorySheet by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }

    val conversationDeletedMsg = stringResource(Res.string.snackbar_conversation_deleted)
    val undoLabel = stringResource(Res.string.snackbar_undo)

    LaunchedEffect(uiState.snackbarMessage) {
        val resource = uiState.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(getString(resource))
        uiState.actions.clearSnackbar()
    }

    LaunchedEffect(uiState.pendingConversationDeletion) {
        val pendingId = uiState.pendingConversationDeletion ?: return@LaunchedEffect
        snackbarHostState.currentSnackbarData?.dismiss()
        val result = snackbarHostState.showSnackbar(
            message = conversationDeletedMsg,
            actionLabel = undoLabel,
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) {
            uiState.actions.undoDeleteConversation()
        }
    }

    val filteredConversations = remember(uiState.savedConversations, uiState.pendingConversationDeletion) {
        val pendingId = uiState.pendingConversationDeletion
        if (pendingId != null) uiState.savedConversations.filter { it.id != pendingId }.toImmutableList() else uiState.savedConversations
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding().statusBarsPadding().imePadding()) {
        Column(Modifier.fillMaxSize()) {
            TopBar(
                textToSpeech = textToSpeech,
                isSpeechOutputEnabled = uiState.isSpeechOutputEnabled,
                isSpeaking = uiState.isSpeaking,
                actions = uiState.actions,
                isChatHistoryEmpty = uiState.history.isEmpty(),
                hasSavedConversations = filteredConversations.any { it.id != uiState.currentConversationId },
                onNavigateToSettings = onNavigateToSettings,
                onShowHistory = {
                    keyboardController?.hide()
                    showHistorySheet = true
                },
                navigationTabBar = navigationTabBar,
            )

            HeartbeatBanner(
                visible = uiState.hasUnreadHeartbeat,
                onTap = {
                    uiState.heartbeatConversationId?.let { uiState.actions.loadConversation(it) }
                    uiState.actions.clearUnreadHeartbeat()
                },
                onDismiss = {
                    uiState.actions.clearUnreadHeartbeat()
                },
            )

            uiState.warning?.let { warning ->
                Text(
                    text = stringResource(warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            Box(Modifier.weight(1f)) {
                var isDropping by remember {
                    mutableStateOf(false)
                }
                Column(
                    Modifier
                        .fillMaxSize()
                        .blur(radius = if (isDropping) 4.dp else 0.dp)
                        .dragAndDropTarget(
                            shouldStartDragAndDrop = { uiState.supportedFileExtensions.isNotEmpty() },
                            target = remember {
                                object : DragAndDropTarget {
                                    override fun onEntered(event: DragAndDropEvent) {
                                        super.onEntered(event)
                                        isDropping = true
                                    }
                                    override fun onExited(event: DragAndDropEvent) {
                                        super.onExited(event)
                                        isDropping = false
                                    }
                                    override fun onDrop(event: DragAndDropEvent): Boolean {
                                        val file = onDragAndDropEventDropped(event)
                                        if (file != null) uiState.actions.addFile(file)
                                        isDropping = false
                                        return file != null
                                    }
                                }
                            },
                        ),
                ) {
                    if (uiState.history.isEmpty()) {
                        // Interactive UI mode isn't offered on on-device LiteRT: the kai-ui
                        // component schema is too large for small Gemma models to coherently
                        // attend to, and even the minimal variant we tried was unreliable.
                        val primaryIsOnDevice = uiState.availableServices
                            .firstOrNull()
                            ?.let { Service.fromId(it.serviceId).isOnDevice } == true
                        EmptyState(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            isUsingSharedKey = uiState.showPrivacyInfo,
                            onStartInteractiveMode = uiState.actions.enterInteractiveMode
                                .takeUnless { primaryIsOnDevice },
                        )
                    } else {
                        val listState = rememberLazyListState()
                        val componentScope = rememberCoroutineScope()

                        LaunchedEffect(uiState.history.size) {
                            // Capture history at effect start to prevent race conditions
                            val history = uiState.history
                            if (history.isNotEmpty()) {
                                listState.scrollToItem(history.lastIndex)
                                val lastMessage = history.last()
                                if (uiState.isSpeechOutputEnabled && lastMessage.role == History.Role.ASSISTANT) {
                                    componentScope.launch(getBackgroundDispatcher()) {
                                        textToSpeech?.stop()
                                        uiState.actions.setIsSpeaking(true, lastMessage.id)
                                        try {
                                            textToSpeech?.say(lastMessage.content.toSpeakableText())
                                        } catch (_: TextToSpeechSynthesisInterruptedError) {
                                            // Speech was interrupted by user
                                        } catch (_: Exception) {
                                            // Handle TTS errors gracefully (service failure, audio issues, etc.)
                                        } finally {
                                            uiState.actions.setIsSpeaking(false, lastMessage.id)
                                        }
                                    }
                                }
                            }
                        }

                        val lastAssistantId = remember(uiState.history) { uiState.history.lastRenderedAssistant()?.id }
                        val lastUserId = remember(uiState.history) {
                            uiState.history.lastOrNull { it.role == History.Role.USER }?.id
                        }
                        // Freeze the bot's kai-ui in place while a submission is pending so the
                        // layout doesn't shift on click. The user's SubmittedUiMessage is skipped
                        // below to avoid rendering the same buttons twice.
                        val pendingFrozen = remember(uiState.history, uiState.isLoading) {
                            if (!uiState.isLoading) return@remember null
                            val lastUser = uiState.history.lastOrNull { it.role == History.Role.USER }
                            val submission = lastUser?.uiSubmission ?: return@remember null
                            FrozenSubmission(
                                values = submission.values,
                                pressedEvent = submission.pressedEvent,
                                isPending = true,
                            )
                        }
                        val executingToolsState = rememberExecutingTools(uiState.history)

                        val showScrollToBottom by remember {
                            derivedStateOf {
                                val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                                lastVisibleItem != null && lastVisibleItem.index < listState.layoutInfo.totalItemsCount - 1
                            }
                        }

                        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                state = listState,
                                horizontalAlignment = CenterHorizontally,
                            ) {
                                items(uiState.history, key = { it.id }, contentType = { it.role }) { history ->
                                    when (history.role) {
                                        History.Role.USER -> {
                                            val isLastUser = history.id == lastUserId
                                            // When the bot card above is showing the pending frozen state,
                                            // skip this user entry — otherwise the buttons render twice.
                                            if (!(isLastUser && pendingFrozen != null)) {
                                                UserMessage(
                                                    message = history.content,
                                                    attachments = history.attachments,
                                                    uiSubmission = history.uiSubmission,
                                                    isPendingSubmission = uiState.isLoading && isLastUser,
                                                    onResubmit = if (history.uiSubmission != null && !uiState.isLoading) {
                                                        { event, data -> uiState.actions.resubmit(history.id, event, data) }
                                                    } else {
                                                        null
                                                    },
                                                )
                                            }
                                        }

                                        History.Role.ASSISTANT -> {
                                            // Skip thinking messages unless it's the last assistant message
                                            // (i.e. the model only returned reasoning with no content)
                                            if (history.content.isNotEmpty() && !history.isThinking) {
                                                val isLastAssistant = history.id == lastAssistantId
                                                BotMessage(
                                                    message = history.content,
                                                    textToSpeech = textToSpeech,
                                                    isSpeaking = uiState.isSpeaking && uiState.isSpeakingContentId == history.id,
                                                    setIsSpeaking = {
                                                        uiState.actions.setIsSpeaking(it, history.id)
                                                    },
                                                    onRegenerate = if (isLastAssistant) uiState.actions.regenerate else null,
                                                    isInteractive = isLastAssistant && !uiState.isLoading,
                                                    onUiCallback = { event, data ->
                                                        uiState.actions.submitUiCallback(event, data)
                                                    },
                                                    pendingFrozen = if (isLastAssistant) pendingFrozen else null,
                                                )
                                                if (history.fallbackServiceName != null) {
                                                    androidx.compose.material3.Text(
                                                        text = stringResource(Res.string.fallback_answered_by, history.fallbackServiceName),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                                                    )
                                                }
                                            }
                                        }

                                        History.Role.TOOL_EXECUTING -> {
                                            // Rendered in WaitingResponseRow below
                                        }

                                        History.Role.TOOL -> {
                                            // Don't show completed tool results in UI
                                        }
                                    }
                                }
                                // Skip the generic "thinking" row during a pending kai-ui submission — the
                                // pressed button's pulse already signals work in flight. Keep it for tool
                                // activity so tool feedback isn't lost.
                                val showWaitingRow = uiState.isLoading &&
                                    (pendingFrozen == null || executingToolsState.tools.isNotEmpty())
                                if (showWaitingRow) {
                                    item(key = "loading") {
                                        WaitingResponseRow(
                                            executingTools = executingToolsState.tools,
                                            isStatusOnly = executingToolsState.isStatusOnly,
                                        )
                                    }
                                }
                                uiState.error?.let { error ->
                                    item(key = "error") {
                                        ErrorMessage(error = error, retry = uiState.actions.retry)
                                    }
                                }
                            }

                            VerticalScrollbarForList(
                                listState = listState,
                                modifier = Modifier.align(CenterEnd).fillMaxHeight(),
                            )

                            androidx.compose.animation.AnimatedVisibility(
                                visible = showScrollToBottom,
                                modifier = Modifier.align(BottomCenter).padding(bottom = 8.dp),
                                enter = fadeIn() + scaleIn(),
                                exit = fadeOut() + scaleOut(),
                            ) {
                                SmallFloatingActionButton(
                                    modifier = Modifier
                                        .handCursor(),
                                    onClick = {
                                        componentScope.launch {
                                            val totalItems = listState.layoutInfo.totalItemsCount
                                            if (totalItems > 0) {
                                                listState.animateScrollToItem(totalItems - 1)
                                            }
                                        }
                                    },
                                ) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(Res.string.scroll_to_bottom_content_description))
                                }
                            }
                        }
                    }
                }
            }

            QuestionInput(
                files = uiState.files,
                addFile = uiState.actions.addFile,
                removeFile = uiState.actions.removeFile,
                ask = uiState.actions.ask,
                supportedFileExtensions = uiState.supportedFileExtensions,
                isLoading = uiState.isLoading,
                cancel = uiState.actions.cancel,
                availableServices = uiState.availableServices,
                onSelectService = uiState.actions.selectService,
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(BottomCenter).padding(bottom = 80.dp),
        ) { data ->
            Snackbar(snackbarData = data)
        }
    }

    if (showHistorySheet) {
        ChatHistorySheet(
            conversations = filteredConversations,
            currentConversationId = uiState.currentConversationId,
            actions = uiState.actions,
            onDismiss = { showHistorySheet = false },
        )
    }
}

private data class ExecutingToolsState(
    val tools: ImmutableList<Pair<String, String>>,
    val isStatusOnly: Boolean,
)

@Composable
private fun rememberExecutingTools(history: ImmutableList<History>): ExecutingToolsState = remember(history) {
    val executing = history.filter { it.role == History.Role.TOOL_EXECUTING }
    ExecutingToolsState(
        tools = executing.map { it.id to (it.toolName ?: "tool") }.toImmutableList(),
        isStatusOnly = executing.any { it.isStatusMessage },
    )
}
