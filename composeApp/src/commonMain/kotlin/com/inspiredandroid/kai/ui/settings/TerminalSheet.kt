@file:OptIn(ExperimentalMaterial3Api::class)

package com.inspiredandroid.kai.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inspiredandroid.kai.CommandHandle
import com.inspiredandroid.kai.SandboxController
import com.inspiredandroid.kai.ui.handCursor
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.terminal_help_text
import kai.composeapp.generated.resources.terminal_input_placeholder
import kai.composeapp.generated.resources.terminal_run_content_description
import kai.composeapp.generated.resources.terminal_stop_content_description
import kai.composeapp.generated.resources.terminal_title
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

internal val TerminalDarkBg = Color(0xFF1E1E1E)

private data class TerminalColors(
    val bg: Color,
    val inputBg: Color,
    val text: Color,
    val prompt: Color,
    val error: Color,
    val dimText: Color,
)

private fun monoStyle(size: TextUnit, color: Color = Color.Unspecified) = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = size,
    color = color,
)

@Composable
private fun terminalColors(darkBackground: Boolean = false): TerminalColors {
    if (darkBackground) {
        return TerminalColors(
            bg = TerminalDarkBg,
            inputBg = Color(0xFF252525),
            text = Color(0xFFD4D4D4),
            prompt = Color(0xFF6CB6FF),
            error = Color(0xFFF48771),
            dimText = Color(0xFF666666),
        )
    }
    val colorScheme = MaterialTheme.colorScheme
    return TerminalColors(
        bg = colorScheme.background,
        inputBg = colorScheme.surface,
        text = colorScheme.onBackground,
        prompt = colorScheme.primary,
        error = colorScheme.error,
        dimText = colorScheme.onBackground.copy(alpha = 0.4f),
    )
}

@Composable
fun TerminalContent(
    sandboxController: SandboxController?,
    modifier: Modifier = Modifier,
    showHeader: Boolean = false,
    darkBackground: Boolean = false,
    initialLines: List<TerminalLine> = emptyList(),
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val outputLines = remember { mutableStateListOf<TerminalLine>().apply { addAll(initialLines) } }
    var inputText by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(false) }
    var activeHandle by remember { mutableStateOf<CommandHandle?>(null) }
    val colors = terminalColors(darkBackground)
    val focusRequester = remember { FocusRequester() }
    val canSubmit = sandboxController != null && inputText.isNotBlank()
    val canCancel = isRunning && activeHandle != null && inputText.isBlank()
    val isInputEnabled = sandboxController != null
    val submitInput = {
        val controller = sandboxController
        val running = isRunning
        val handle = activeHandle
        if (controller != null && inputText.isNotBlank()) {
            val line = inputText
            inputText = ""
            if (running && handle != null) {
                outputLines.add(TerminalLine.Output(line))
                scope.launch { handle.writeInput(line) }
            } else if (!running) {
                scope.launch {
                    runCommand(
                        command = line.trim(),
                        outputLines = outputLines,
                        sandboxController = controller,
                        setRunning = { isRunning = it },
                        setHandle = { activeHandle = it },
                    )
                }
            }
        }
    }
    val cancelRunning: () -> Unit = { activeHandle?.cancel() }

    LaunchedEffect(Unit) {
        if (sandboxController != null) {
            focusRequester.requestFocus()
        }
    }

    Column(
        modifier = modifier
            .then(if (showHeader) Modifier.background(colors.bg) else Modifier)
            .imePadding(),
    ) {
        if (showHeader) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.inputBg)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.terminal_title),
                    style = monoStyle(16.sp, colors.prompt),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Alpine Linux",
                    style = monoStyle(12.sp, colors.text.copy(alpha = 0.5f)),
                )
            }
        }

        SelectionContainer(
            modifier = Modifier.weight(1f),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                state = listState,
            ) {
                if (outputLines.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(Res.string.terminal_help_text),
                            style = monoStyle(13.sp, colors.dimText),
                        )
                    }
                }
                items(
                    items = outputLines,
                    contentType = { it::class },
                ) { line ->
                    when (line) {
                        is TerminalLine.Command -> {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "$ ${line.text}",
                                style = monoStyle(13.sp, colors.prompt),
                            )
                        }

                        is TerminalLine.Output -> {
                            Text(
                                text = parseAnsiToAnnotatedString(line.text, colors.text),
                                style = monoStyle(13.sp),
                            )
                        }

                        is TerminalLine.Error -> {
                            Text(
                                text = parseAnsiToAnnotatedString(line.text, colors.error),
                                style = monoStyle(13.sp),
                            )
                        }
                    }
                }
                if (isRunning) {
                    item {
                        Spacer(Modifier.height(4.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = colors.prompt,
                        )
                    }
                }
            }
        }

        LaunchedEffect(outputLines.size, isRunning) {
            val lastIndex = outputLines.lastIndex + if (isRunning) 1 else 0
            if (lastIndex < 0) return@LaunchedEffect
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            // Don't yank the user back if they've scrolled up to read older output.
            if (lastVisible >= layout.totalItemsCount - 2) {
                listState.scrollToItem(lastIndex)
            }
        }

        androidx.compose.material3.HorizontalDivider(
            color = colors.dimText.copy(alpha = 0.2f),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$",
                style = monoStyle(14.sp, colors.prompt),
                modifier = Modifier.padding(start = 8.dp),
            )
            Spacer(Modifier.width(8.dp))
            TextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f).then(
                    if (sandboxController != null) Modifier.focusRequester(focusRequester) else Modifier,
                ),
                enabled = isInputEnabled,
                textStyle = monoStyle(14.sp, colors.text),
                placeholder = {
                    Text(
                        text = stringResource(Res.string.terminal_input_placeholder),
                        style = monoStyle(14.sp, colors.dimText),
                    )
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    cursorColor = colors.prompt,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(
                    onGo = { submitInput() },
                ),
                singleLine = true,
            )
            IconButton(
                onClick = {
                    when {
                        canSubmit -> submitInput()
                        canCancel -> cancelRunning()
                    }
                },
                enabled = canSubmit || canCancel,
                modifier = Modifier.handCursor(),
            ) {
                Icon(
                    imageVector = if (canCancel) Icons.Filled.Stop else Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(
                        if (canCancel) {
                            Res.string.terminal_stop_content_description
                        } else {
                            Res.string.terminal_run_content_description
                        },
                    ),
                    tint = if (canSubmit || canCancel) colors.prompt else colors.dimText,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

private const val MAX_OUTPUT_LINES = 500
private const val STREAM_BUFFER_CAPACITY = 256
private const val STREAM_FLUSH_INTERVAL_MS = 32L
private const val STREAM_FLUSH_BATCH_MAX = 200

private suspend fun runCommand(
    command: String,
    outputLines: MutableList<TerminalLine>,
    sandboxController: SandboxController,
    setRunning: (Boolean) -> Unit,
    setHandle: (CommandHandle?) -> Unit,
) {
    if (command == "clear") {
        outputLines.clear()
        return
    }
    outputLines.add(TerminalLine.Command(command))
    setRunning(true)

    // Buffered channel with DROP_OLDEST so a runaway producer (e.g. `yes`)
    // can't starve the UI or grow memory without bound. The drain loop
    // flushes on a fixed cadence and prunes to MAX_OUTPUT_LINES each tick.
    val channel = Channel<TerminalLine>(
        capacity = STREAM_BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    var handle: CommandHandle? = null
    try {
        coroutineScope {
            val drainJob = launch { drainStreamedLines(channel, outputLines) }
            val h = sandboxController.executeCommandStreaming(
                command = command,
                onStdout = { line -> channel.trySend(TerminalLine.Output(line)) },
                onStderr = { line -> channel.trySend(TerminalLine.Error(line)) },
            )
            handle = h
            setHandle(h)
            try {
                h.awaitExit()
            } finally {
                channel.close()
                drainJob.join()
            }
        }
        if (handle?.isCancelled() == true) {
            outputLines.add(TerminalLine.Output("^C"))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        outputLines.add(TerminalLine.Error(e.message ?: "Command failed"))
    } finally {
        setHandle(null)
        pruneOutput(outputLines)
        setRunning(false)
    }
}

private suspend fun drainStreamedLines(
    channel: Channel<TerminalLine>,
    outputLines: MutableList<TerminalLine>,
) {
    while (true) {
        val batch = ArrayList<TerminalLine>(STREAM_FLUSH_BATCH_MAX)
        var closed = false
        while (batch.size < STREAM_FLUSH_BATCH_MAX) {
            val result = channel.tryReceive()
            if (result.isSuccess) {
                batch.add(result.getOrThrow())
            } else {
                if (result.isClosed) closed = true
                break
            }
        }
        if (batch.isNotEmpty()) {
            outputLines.addAll(batch)
            pruneOutput(outputLines)
        }
        if (closed) break
        delay(STREAM_FLUSH_INTERVAL_MS)
    }
}

private fun pruneOutput(outputLines: MutableList<TerminalLine>) {
    val excess = outputLines.size - MAX_OUTPUT_LINES
    if (excess > 0) {
        outputLines.subList(0, excess).clear()
    }
}

sealed interface TerminalLine {
    data class Command(val text: String) : TerminalLine
    data class Output(val text: String) : TerminalLine
    data class Error(val text: String) : TerminalLine
}
