@file:OptIn(
    ExperimentalFoundationApi::class,
)

package com.inspiredandroid.kai.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
import com.inspiredandroid.kai.getBackgroundDispatcher
import com.inspiredandroid.kai.onDragAndDropEventDropped
import com.inspiredandroid.kai.ui.chat.composables.BotMessage
import com.inspiredandroid.kai.ui.chat.composables.EmptyState
import com.inspiredandroid.kai.ui.chat.composables.ErrorMessage
import com.inspiredandroid.kai.ui.chat.composables.QuestionInput
import com.inspiredandroid.kai.ui.chat.composables.TopBar
import com.inspiredandroid.kai.ui.chat.composables.UserMessage
import kotlinx.coroutines.launch
import nl.marc_apps.tts.TextToSpeechInstance
import nl.marc_apps.tts.errors.TextToSpeechSynthesisInterruptedError
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ChatScreen(
    viewModel: ChatViewModel = koinViewModel(),
    textToSpeech: TextToSpeechInstance? = null,
    onNavigateToSettings: () -> Unit = {},
) {
    val uiState by viewModel.state.collectAsState()

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding().statusBarsPadding().imePadding()) {
        TopBar(
            textToSpeech = textToSpeech,
            isLoading = uiState.isLoading,
            isSpeechOutputEnabled = uiState.isSpeechOutputEnabled,
            isSpeaking = uiState.isSpeaking,
            actions = uiState.actions,
            isChatHistoryEmpty = uiState.history.isEmpty(),
            onNavigateToSettings = onNavigateToSettings,
        )

        SelectionContainer {
            var isDropping by remember {
                mutableStateOf(false)
            }
            Column(
                Modifier
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
                    EmptyState(Modifier.fillMaxWidth().weight(1f), uiState.isUsingSharedKey)
                } else {
                    val listState = rememberLazyListState()
                    val componentScope = rememberCoroutineScope()

                    LaunchedEffect(uiState.history.size) {
                        if (uiState.history.isNotEmpty()) {
                            listState.animateScrollToItem(uiState.history.lastIndex)
                            if (uiState.isSpeechOutputEnabled && uiState.history.last().role == History.Role.ASSISTANT) {
                                val contentId = uiState.history.last().id
                                val content = uiState.history.last().content
                                componentScope.launch(getBackgroundDispatcher()) {
                                    textToSpeech?.stop()
                                    uiState.actions.setIsSpeaking(true, contentId)
                                    try {
                                        textToSpeech?.say(content)
                                    } catch (ignore: TextToSpeechSynthesisInterruptedError) {
                                    }
                                }
                            }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        state = listState,
                        horizontalAlignment = CenterHorizontally,
                    ) {
                        items(uiState.history, key = { it.id }) { history ->
                            when (history.role) {
                                History.Role.USER -> UserMessage(history.content)
                                History.Role.ASSISTANT -> BotMessage(
                                    message = history.content,
                                    textToSpeech = textToSpeech,
                                    isSpeaking = uiState.isSpeaking && uiState.isSpeakingContentId == history.id,
                                    setIsSpeaking = {
                                        uiState.actions.setIsSpeaking(it, history.id)
                                    },
                                )
                            }
                        }
                        if (uiState.isLoading) {
                            item(key = "loading") {
                                CircularProgressIndicator(
                                    modifier = Modifier.padding(16.dp),
                                )
                            }
                        }
                        uiState.error?.let { error ->
                            item(key = "error") {
                                ErrorMessage(error = error, retry = uiState.actions.retry)
                            }
                        }
                    }
                }

                QuestionInput(
                    file = uiState.file,
                    setFile = uiState.actions.setFile,
                    ask = uiState.actions.ask,
                    allowFileAttachment = uiState.allowFileAttachment,
                )
            }
        }
    }
}
