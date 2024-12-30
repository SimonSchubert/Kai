@file:OptIn(ExperimentalResourceApi::class)

package com.inspiredandroid.kai.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import io.github.alexzhirkevich.compottie.Compottie
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.ic_delete_forever
import kai.composeapp.generated.resources.ic_refresh
import kai.composeapp.generated.resources.ic_settings
import kai.composeapp.generated.resources.ic_up
import kai.composeapp.generated.resources.ic_volume_off
import kai.composeapp.generated.resources.ic_volume_up
import nl.marc_apps.tts.TextToSpeechInstance
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ChatScreen(
    viewModel: ChatViewModel = koinViewModel(),
    textToSpeech: TextToSpeechInstance? = null,
    onNavigateToSettings: () -> Unit = {},
) {
    val uiState by viewModel.state.collectAsState()

    Column(Modifier.fillMaxSize().navigationBarsPadding().statusBarsPadding().imePadding()) {
        TopBar(
            textToSpeech = textToSpeech,
            isLoading = uiState.isLoading,
            isSpeechOutputEnabled = uiState.isSpeechOutputEnabled,
            isChatHistoryEmpty = uiState.history.isEmpty(),
            clearHistory = uiState.clearHistory,
            toggleSpeechOutput = uiState.toggleSpeechOutput,
            onNavigateToSettings = onNavigateToSettings,
        )

        SelectionContainer {
            Column {
                if (uiState.history.isEmpty()) {
                    EmptyState(Modifier.fillMaxWidth().weight(1f), uiState.isUsingSharedKey)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        state = rememberLazyListState().apply {
                            LaunchedEffect(uiState.history.size) {
                                if (uiState.history.isNotEmpty()) {
                                    animateScrollToItem(uiState.history.lastIndex)
                                    if (uiState.isSpeechOutputEnabled && uiState.history.last().role == History.Role.ASSISTANT) {
                                        textToSpeech?.enqueue(
                                            uiState.history.last().content,
                                            clearQueue = true,
                                        )
                                    }
                                }
                            }
                        },
                        horizontalAlignment = CenterHorizontally,
                    ) {
                        items(uiState.history, key = { it.id }) { history ->
                            when (history.role) {
                                History.Role.USER -> UserMessage(history.content)
                                History.Role.ASSISTANT -> BotMessage(history.content)
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

                QuestionInput(ask = uiState.ask)
            }
        }
    }
}

@Composable
private fun TopBar(
    textToSpeech: TextToSpeechInstance? = null,
    isLoading: Boolean,
    isSpeechOutputEnabled: Boolean,
    isChatHistoryEmpty: Boolean,
    clearHistory: () -> Unit,
    toggleSpeechOutput: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    Row {
        if (!isChatHistoryEmpty) {
            IconButton(
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                onClick = clearHistory,
                enabled = !isLoading,
            ) {
                Icon(
                    imageVector = vectorResource(Res.drawable.ic_delete_forever),
                    contentDescription = "",
                )
            }
        }

        Spacer(Modifier.weight(1f))

        if (textToSpeech != null) {
            IconButton(
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                onClick = toggleSpeechOutput,
            ) {
                Icon(
                    imageVector =
                    if (isSpeechOutputEnabled) {
                        vectorResource(Res.drawable.ic_volume_up)
                    } else {
                        vectorResource(Res.drawable.ic_volume_off)
                    },
                    contentDescription = "",
                )
            }
        }

        IconButton(
            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
            onClick = onNavigateToSettings,
        ) {
            Icon(
                imageVector = vectorResource(Res.drawable.ic_settings),
                contentDescription = "",
            )
        }
    }
}

@Composable
private fun BotMessage(message: String) {
    MaterialTheme.typography.displayLarge
    Markdown(
        message,
        modifier = Modifier.fillMaxWidth()
            .padding(16.dp),
    )
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
                    Color.LightGray,
                    RoundedCornerShape(8.dp),
                )
                .padding(16.dp),
            text = message,
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
        Spacer(Modifier.height(16.dp))
        IconButton(
            onClick = retry,
        ) {
            Icon(
                imageVector = vectorResource(Res.drawable.ic_refresh),
                contentDescription = null,
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
        )
        if (isUsingSharedKey) {
            Text(
                text = "You are using a limited and shared api key. Go to settings to change it.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun QuestionInput(ask: (String) -> Unit) {
    val focusManager = LocalFocusManager.current

    var textState by remember { mutableStateOf(TextFieldValue("")) }

    fun submitQuestion() {
        val text = textState.text
        textState = TextFieldValue("")
        focusManager.clearFocus()
        ask(text.trim())
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

    val focusRequester = remember { FocusRequester() }
    TextField(
        value = textState,
        onValueChange = {
            if (textState.text != it.text) {
                textState = it
            }
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
            ).onKeyEvent {
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
        colors = TextFieldDefaults.textFieldColors(
            backgroundColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        placeholder = { Text("Ask a question") },
        trailingIcon = if (textState.text.isNotBlank()) trailingIconView else null,
        keyboardActions = KeyboardActions(onSend = {
            submitQuestion()
        }),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
    )
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}