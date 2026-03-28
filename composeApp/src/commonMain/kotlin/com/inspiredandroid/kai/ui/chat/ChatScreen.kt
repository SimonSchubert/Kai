@file:OptIn(
    ExperimentalFoundationApi::class,
)

package com.inspiredandroid.kai.ui.chat

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.BottomCenter
import androidx.compose.ui.Alignment.Companion.CenterEnd
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draw.blur
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.inspiredandroid.kai.getBackgroundDispatcher
import com.inspiredandroid.kai.onDragAndDropEventDropped
import com.inspiredandroid.kai.stripMarkdownForTts
import com.inspiredandroid.kai.ui.chat.composables.BotMessage
import com.inspiredandroid.kai.ui.chat.composables.ChatHistorySheet
import com.inspiredandroid.kai.ui.chat.composables.EmptyState
import com.inspiredandroid.kai.ui.chat.composables.ErrorMessage
import com.inspiredandroid.kai.ui.chat.composables.HeartbeatBanner
import com.inspiredandroid.kai.ui.chat.composables.QuestionInput
import com.inspiredandroid.kai.ui.chat.composables.TopBar
import com.inspiredandroid.kai.ui.chat.composables.UserMessage
import com.inspiredandroid.kai.ui.chat.composables.WaitingResponseRow
import com.inspiredandroid.kai.ui.components.VerticalScrollbarForList
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.fallback_answered_by
import kai.composeapp.generated.resources.scroll_to_bottom_content_description
import kai.composeapp.generated.resources.snackbar_conversation_deleted
import kai.composeapp.generated.resources.snackbar_undo
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
                isLoading = uiState.isLoading,
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

            SelectionContainer(Modifier.weight(1f)) {
                var isDropping by remember {
                    mutableStateOf(false)
                }
                Column(
                    Modifier
                        .fillMaxSize()
                        .blur(radius = if (isDropping) 4.dp else 0.dp)
                        .dragAndDropTarget(
                            shouldStartDragAndDrop = { uiState.allowFileAttachment },
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
                                        uiState.actions.setFile(file)
                                        isDropping = false
                                        return file != null
                                    }
                                }
                            },
                        ),
                ) {
                    if (uiState.history.isEmpty()) {
                        EmptyState(Modifier.fillMaxWidth().weight(1f), uiState.showPrivacyInfo)
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
                                            textToSpeech?.say(lastMessage.content.stripMarkdownForTts())
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

                        val lastAssistantId = remember(uiState.history, uiState.isLoading) {
                            if (uiState.isLoading) {
                                null
                            } else {
                                uiState.history.lastOrNull { it.role == History.Role.ASSISTANT && it.content.isNotEmpty() && !it.isThinking }?.id
                            }
                        }
                        val executingTools = remember(uiState.history) {
                            uiState.history
                                .filter { it.role == History.Role.TOOL_EXECUTING }
                                .map { it.id to (it.toolName ?: "tool") }
                                .toImmutableList()
                        }

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
                                items(uiState.history, key = { it.id }) { history ->
                                    when (history.role) {
                                        History.Role.USER -> UserMessage(
                                            message = history.content,
                                            imageData = history.data,
                                            mimeType = history.mimeType,
                                            fileName = history.fileName,
                                        )

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
                                if (uiState.isLoading) {
                                    item(key = "loading") {
                                        WaitingResponseRow(
                                            executingTools = executingTools,
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
                                        .pointerHoverIcon(PointerIcon.Hand),
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
                file = uiState.file,
                setFile = uiState.actions.setFile,
                ask = uiState.actions.ask,
                allowFileAttachment = uiState.allowFileAttachment,
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
