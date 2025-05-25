@file:OptIn(
    ExperimentalFoundationApi::class,
)

package com.inspiredandroid.kai.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.inspiredandroid.kai.getBackgroundDispatcher
import com.inspiredandroid.kai.onDragAndDropEventDropped
import com.inspiredandroid.kai.openUrl
import com.inspiredandroid.kai.outlineTextFieldColors
import com.mikepenz.markdown.m3.Markdown
import io.github.alexzhirkevich.compottie.Compottie
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.core.PickerMode
import io.github.vinceglb.filekit.core.PickerType
import io.github.vinceglb.filekit.core.PlatformFile
import io.github.vinceglb.filekit.core.extension
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.ic_add
import kai.composeapp.generated.resources.ic_copy
import kai.composeapp.generated.resources.ic_delete_forever
import kai.composeapp.generated.resources.ic_file
import kai.composeapp.generated.resources.ic_flag
import kai.composeapp.generated.resources.ic_image
import kai.composeapp.generated.resources.ic_refresh
import kai.composeapp.generated.resources.ic_settings
import kai.composeapp.generated.resources.ic_stop
import kai.composeapp.generated.resources.ic_up
import kai.composeapp.generated.resources.ic_volume_off
import kai.composeapp.generated.resources.ic_volume_up
import kotlinx.coroutines.launch
import nl.marc_apps.tts.TextToSpeechInstance
import nl.marc_apps.tts.errors.TextToSpeechSynthesisInterruptedError
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.vectorResource
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
            setIsSpeaking = uiState.setIsSpeaking,
            isChatHistoryEmpty = uiState.history.isEmpty(),
            clearHistory = uiState.clearHistory,
            toggleSpeechOutput = uiState.toggleSpeechOutput,
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
                                    uiState.setFile(file)
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
                    val componentScope = rememberCoroutineScope()
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        state = rememberLazyListState().apply {
                            LaunchedEffect(uiState.history.size) {
                                if (uiState.history.isNotEmpty()) {
                                    animateScrollToItem(uiState.history.lastIndex)
                                    if (uiState.isSpeechOutputEnabled && uiState.history.last().role == History.Role.ASSISTANT) {
                                        val contentId = uiState.history.last().id
                                        val content = uiState.history.last().content
                                        componentScope.launch(getBackgroundDispatcher()) {
                                            textToSpeech?.stop()
                                            uiState.setIsSpeaking(true, contentId)
                                            try {
                                                textToSpeech?.say(content)
                                            } catch (ignore: TextToSpeechSynthesisInterruptedError) { }
                                        }
                                    }
                                }
                            }
                        },
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
                                        uiState.setIsSpeaking(it, history.id)
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
                                Error(error = error, retry = uiState.retry)
                            }
                        }
                    }
                }

                QuestionInput(
                    file = uiState.file,
                    setFile = uiState.setFile,
                    ask = uiState.ask,
                    allowFileAttachment = uiState.allowFileAttachment,
                )
            }
        }
    }
}

@Composable
private fun TopBar(
    textToSpeech: TextToSpeechInstance? = null,
    isLoading: Boolean,
    isSpeechOutputEnabled: Boolean,
    isSpeaking: Boolean,
    setIsSpeaking: (Boolean, String) -> Unit,
    isChatHistoryEmpty: Boolean,
    clearHistory: () -> Unit,
    toggleSpeechOutput: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    Row {
        if (!isChatHistoryEmpty) {
            IconButton(
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                onClick = {
                    if (isSpeechOutputEnabled && isSpeaking) {
                        setIsSpeaking(false, "")
                        textToSpeech?.stop()
                    }
                    clearHistory()
                },
                enabled = !isLoading,
            ) {
                Icon(
                    imageVector = vectorResource(Res.drawable.ic_delete_forever),
                    contentDescription = "Clear chat",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        if (textToSpeech != null) {
            IconButton(
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                onClick = {
                    if (isSpeechOutputEnabled && isSpeaking) {
                        setIsSpeaking(false, "")
                        textToSpeech.stop()
                    }
                    toggleSpeechOutput()
                },
            ) {
                Icon(
                    imageVector =
                    if (isSpeechOutputEnabled) {
                        vectorResource(Res.drawable.ic_volume_up)
                    } else {
                        vectorResource(Res.drawable.ic_volume_off)
                    },
                    contentDescription = "Toggle speech output",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        IconButton(
            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
            onClick = onNavigateToSettings,
        ) {
            Icon(
                imageVector = vectorResource(Res.drawable.ic_settings),
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun BotMessage(message: String, textToSpeech: TextToSpeechInstance?, isSpeaking: Boolean, setIsSpeaking: (Boolean) -> Unit) {
    Markdown(
        message,
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp)
            .padding(top = 16.dp),
    )
    Row(Modifier.padding(horizontal = 8.dp)) {
        if (textToSpeech != null) {
            val componentScope = rememberCoroutineScope()
            SmallIconButton(
                iconResource = if (isSpeaking) Res.drawable.ic_stop else Res.drawable.ic_volume_up,
                contentDescription = "Speech",
                onClick = {
                    componentScope.launch(getBackgroundDispatcher()) {
                        textToSpeech.stop()
                        if (isSpeaking) {
                            setIsSpeaking(false)
                        } else {
                            setIsSpeaking(true)
                            try {
                                textToSpeech.say(
                                    text = message,
                                )
                            } catch (ignore: TextToSpeechSynthesisInterruptedError) { }
                            setIsSpeaking(false)
                        }
                    }
                },
            )
        }
        val clipboardManager = LocalClipboardManager.current
        SmallIconButton(
            iconResource = Res.drawable.ic_copy,
            contentDescription = "Copy",
            onClick = {
                clipboardManager.setText(
                    annotatedString = buildAnnotatedString {
                        append(message)
                    },
                )
            },
        )
        SmallIconButton(
            iconResource = Res.drawable.ic_flag,
            contentDescription = "Flag content",
            onClick = {
                openUrl("https://form.jotform.com/250014908169355")
            },
        )
        Spacer(Modifier.weight(1f))
    }
}

@Composable
fun SmallIconButton(
    iconResource: DrawableResource,
    contentDescription: String? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand).size(36.dp).clip(CircleShape).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            modifier = Modifier.size(20.dp),
            painter = painterResource(iconResource),
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun UserMessage(
    message: String,
) {
    Row(Modifier.padding(16.dp)) {
        Spacer(Modifier.weight(1f))
        Text(
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f),
                    RoundedCornerShape(8.dp),
                )
                .padding(16.dp),
            text = message,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun Error(
    error: String,
    retry: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = CenterHorizontally,
    ) {
        Text(
            text = error,
        )
        Spacer(Modifier.height(8.dp))
        IconButton(
            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
            onClick = retry,
        ) {
            Icon(
                imageVector = vectorResource(Res.drawable.ic_refresh),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier, isUsingSharedKey: Boolean) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = CenterHorizontally,
    ) {
        val composition by rememberLottieComposition {
            LottieCompositionSpec.JsonString(
                Res.readBytes("files/lottie_loading.json").decodeToString(),
            )
        }
        Image(
            modifier = Modifier.size(128.dp),
            painter = rememberLottiePainter(
                composition = composition,
                iterations = Compottie.IterateForever,
                speed = 0.6f,
            ),
            contentDescription = null,
        )
        Text(
            text = "Welcome to Kai",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (isUsingSharedKey) {
            Text(
                text = "You are using a limited and shared api key. Go to settings to change it.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun QuestionInput(
    file: PlatformFile?,
    setFile: (PlatformFile?) -> Unit,
    ask: (String) -> Unit,
    allowFileAttachment: Boolean,
) {
    val focusManager = LocalFocusManager.current

    if (file != null) {
        val icon = when (file.extension) {
            "jpg", "jpeg", "png", "gif" -> Res.drawable.ic_image
            else -> Res.drawable.ic_file
        }
        SuggestionChip(
            modifier = Modifier
                .pointerHoverIcon(PointerIcon.Hand)
                .padding(start = 16.dp),
            onClick = { setFile(null) },
            icon = {
                Icon(
                    modifier = Modifier.size(16.dp),
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            },
            label = {
                DisableSelection {
                    Text(
                        modifier = Modifier
                            .pointerHoverIcon(PointerIcon.Hand),
                        text = file.name,
                    )
                }
            },
        )
    }

    var textState by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }

    fun submitQuestion() {
        val text = textState.text
        if (text.isNotBlank()) {
            ask(text.trim())
            focusManager.clearFocus()
            textState = TextFieldValue("")
            setFile(null)
        }
    }

    val trailingIconView = @Composable {
        Box(
            modifier = Modifier
                .padding(end = 6.dp)
                .size(42.dp)
                .pointerHoverIcon(PointerIcon.Hand)
                .clip(CircleShape)
                .background(brush = Brush.horizontalGradient(listOf(Color(0xff6200ee), Color(0xff8063C5))), CircleShape)
                .clickable {
                    submitQuestion()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                vectorResource(Res.drawable.ic_up),
                modifier = Modifier.size(32.dp),
                contentDescription = null,
                tint = Color.White,
            )
        }
    }
    val launcher = rememberFilePickerLauncher(
        type = PickerType.ImageAndVideo,
        mode = PickerMode.Single,
        title = "Pick media",
    ) {
        setFile(it)
    }
    val leadingIconView = @Composable {
        Box(
            modifier = Modifier
                .padding(start = 6.dp)
                .size(42.dp)
                .pointerHoverIcon(PointerIcon.Hand)
                .clip(CircleShape)
                .clickable {
                    launcher.launch()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                vectorResource(Res.drawable.ic_add),
                modifier = Modifier.size(32.dp),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
    }

    val focusRequester = remember { FocusRequester() }
    TextField(
        value = textState,
        onValueChange = {
            textState = it
        },
        modifier = Modifier
            .focusRequester(focusRequester)
            .padding(16.dp)
            .heightIn(max = 120.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .border(
                BorderStroke(width = 2.dp, brush = Brush.horizontalGradient(listOf(Color(0xff6200ee), Color(0xff8063C5)))),
                shape = RoundedCornerShape(28.dp),
            )
            .onKeyEvent {
                return@onKeyEvent if (it.key.keyCode == Key.Enter.keyCode && it.type == KeyEventType.KeyUp) {
                    if (it.isShiftPressed) {
                        try {
                            val textToInsert = "\n"
                            val newText = textState.text.substring(0, textState.selection.start) +
                                textToInsert +
                                textState.text.substring(textState.selection.end)
                            textState = textState.copy(
                                text = newText,
                                selection = TextRange(textState.selection.start + textToInsert.length),
                            )
                        } catch (ignore: Exception) {}
                    } else {
                        submitQuestion()
                    }
                    true
                } else {
                    false
                }
            },
        colors = outlineTextFieldColors(),
        placeholder = {
            Text(
                "Ask a question",
                color = MaterialTheme.colorScheme.onBackground,
            )
        },
        trailingIcon = if (textState.text.isNotBlank()) trailingIconView else null,
        keyboardActions = KeyboardActions(onSend = {
            submitQuestion()
        }),
        leadingIcon = if (allowFileAttachment) leadingIconView else null,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
    )
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}
