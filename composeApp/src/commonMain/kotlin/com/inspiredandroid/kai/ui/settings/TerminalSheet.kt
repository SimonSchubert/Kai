@file:OptIn(ExperimentalMaterial3Api::class)

package com.inspiredandroid.kai.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inspiredandroid.kai.CommandHandle
import com.inspiredandroid.kai.SandboxController
import com.inspiredandroid.kai.SandboxSessions
import com.inspiredandroid.kai.TerminalLine
import com.inspiredandroid.kai.ui.handCursor
import com.inspiredandroid.kai.ui.sandbox.SandboxSessionViewModel
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.terminal_help_text
import kai.composeapp.generated.resources.terminal_input_placeholder
import kai.composeapp.generated.resources.terminal_run_content_description
import kai.composeapp.generated.resources.terminal_stop_content_description
import kai.composeapp.generated.resources.terminal_title
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.milliseconds

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
        inputBg = colorScheme.surfaceContainer,
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
    initialLines: ImmutableList<TerminalLine> = persistentListOf(),
    sessionViewModel: SandboxSessionViewModel? = null,
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val sessionInputText = sessionViewModel?.inputText?.collectAsStateWithLifecycle()?.value
    val sessionIsRunning = sessionViewModel?.isRunning?.collectAsStateWithLifecycle()?.value
    val sessionActiveHandle = sessionViewModel?.activeHandle?.collectAsStateWithLifecycle()?.value
    // Reading selectedSessionId here — even though we don't use the value
    // directly — forces this composable to recompose when the user picks a
    // different chip. Without it, switching between two idle sessions doesn't
    // change inputText/isRunning/activeHandle, so Compose wouldn't re-evaluate
    // the outputLines property getter and we'd keep rendering the previous
    // session's transcript.
    val selectedSessionId = sessionViewModel?.selectedSessionId?.collectAsStateWithLifecycle()?.value

    val outputLines = if (sessionViewModel != null) {
        @Suppress("UNUSED_EXPRESSION")
        selectedSessionId
        sessionViewModel.outputLines
    } else {
        remember { mutableStateListOf<TerminalLine>().apply { addAll(initialLines) } }
    }
    var localInputText by remember { mutableStateOf("") }
    val inputText: String = sessionInputText ?: localInputText
    val setInputText: (String) -> Unit = sessionViewModel?.let { vm ->
        { vm.setInputText(it) }
    } ?: { localInputText = it }
    var localIsRunning by remember { mutableStateOf(false) }
    val isRunning: Boolean = sessionIsRunning ?: localIsRunning
    var localActiveHandle by remember { mutableStateOf<CommandHandle?>(null) }
    val activeHandle: CommandHandle? = sessionActiveHandle ?: localActiveHandle

    // While the user is touching the output area we pause two things that
    // would otherwise unregister selectables mid-drag and crash SelectionManager
    // ("NoSuchElementException: Cannot find value for key …" inside
    // getSelectionLayout's LongIntMap): bounded-trim pruning and the
    // scroll-to-tail effect (off-screen items get disposed by LazyColumn).
    // Appends to the end are safe because they don't invalidate existing ids.
    val isInteractingWithOutput = remember { mutableStateOf(false) }

    val colors = terminalColors(darkBackground)
    val focusRequester = remember { FocusRequester() }
    val canSubmit = sandboxController != null && inputText.isNotBlank()
    val canCancel = isRunning && activeHandle != null && inputText.isBlank()
    val isInputEnabled = sandboxController != null
    val submitInput = {
        if (sessionViewModel != null) {
            sessionViewModel.submit()
        } else {
            val controller = sandboxController
            val running = localIsRunning
            val handle = localActiveHandle
            if (controller != null && localInputText.isNotBlank()) {
                val line = localInputText
                localInputText = ""
                if (running && handle != null) {
                    outputLines.add(TerminalLine.Output(line))
                    scope.launch { handle.writeInput(line) }
                } else if (!running) {
                    scope.launch {
                        runCommand(
                            command = line.trim(),
                            outputLines = outputLines,
                            sandboxController = controller,
                            setRunning = { localIsRunning = it },
                            setHandle = { localActiveHandle = it },
                            shouldPrune = { !isInteractingWithOutput.value },
                        )
                    }
                }
            }
        }
    }
    val cancelRunning: () -> Unit = {
        if (sessionViewModel != null) {
            sessionViewModel.cancelRunning()
        } else {
            localActiveHandle?.cancel()
        }
    }

    LaunchedEffect(Unit) {
        if (sandboxController != null) {
            focusRequester.requestFocus()
        }
    }

    // Jump to the tail whenever the session changes. The size-based auto-scroll
    // below covers per-append scrolling — keying this effect on outputLines too
    // would re-fire on every line and thrash listState during heavy bursts.
    val scrollPulse = sessionViewModel?.scrollToEndPulse?.collectAsStateWithLifecycle()?.value
    LaunchedEffect(scrollPulse) {
        val size = outputLines.size
        if (size > 0) listState.scrollToItem(size - 1)
    }

    // Mirror the touch flag onto the session shell so streaming-side
    // bounded-trim pauses too (the rendered list IS the shell's transcript in
    // session mode). On release, run a catch-up prune for the local
    // non-session list since drainStreamedLines's prune was suppressed.
    LaunchedEffect(isInteractingWithOutput.value, selectedSessionId, sandboxController) {
        val sid = selectedSessionId
        if (sandboxController != null && sid != null) {
            sandboxController.setTranscriptInteractive(sid, isInteractingWithOutput.value)
        }
        if (!isInteractingWithOutput.value && sessionViewModel == null) {
            val excess = outputLines.size - MAX_OUTPUT_LINES
            if (excess > 0) outputLines.subList(0, excess).clear()
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
            modifier = Modifier
                .weight(1f)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        // requireUnconsumed=false: don't fight SelectionContainer's
                        // own long-press detector. We never consume the change so
                        // the inner gesture detectors still receive everything.
                        awaitFirstDown(requireUnconsumed = false)
                        isInteractingWithOutput.value = true
                        try {
                            // Can't use waitForUpOrCancellation(): it treats
                            // consumption as cancellation, and SelectionContainer's
                            // long-press drag observer consumes move events — we'd
                            // flip the flag back to false mid-drag. Loop on raw
                            // pointer events until no pointer is pressed.
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.none { it.pressed }) break
                            }
                        } finally {
                            isInteractingWithOutput.value = false
                        }
                    }
                },
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

        // Re-key the auto-scroll on outputLines so switching sessions detaches
        // from the previous session's list and starts following the new one.
        // conflate() drops intermediate emissions while scrollToItem is in flight
        // — under heavy output, the per-line measure pass contends with the
        // background snapshot writer (proot stdio reader) on Compose's snapshot
        // locks, and queuing a scroll per emission can ANR the main thread.
        LaunchedEffect(listState, isRunning, outputLines) {
            snapshotFlow { outputLines.size }.conflate().collect { size ->
                if (size == 0) return@collect
                // While the user is dragging to select, scrolling to the tail
                // disposes off-screen Texts and unregisters their selectables,
                // crashing SelectionManager mid-drag.
                if (isInteractingWithOutput.value) return@collect
                val layout = listState.layoutInfo
                val total = layout.totalItemsCount
                if (total == 0) return@collect
                val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
                // Don't yank the user back if they've scrolled up to read older output.
                if (lastVisible >= total - 2) {
                    // `size` and `total` can disagree across the composition/measure
                    // boundary: `size` leads when state has been written but the list
                    // hasn't recomposed yet (interval list smaller than `size`);
                    // `total` leads when layoutInfo is stale from a previous measure
                    // (spinner just disappeared, session swap). Either direction
                    // crashes inside MutableIntervalList.get during forceRemeasure.
                    // The smaller of the two is an index that exists in both views.
                    listState.scrollToItem(minOf(size, total) - 1)
                }
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
                onValueChange = setInputText,
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
    shouldPrune: () -> Boolean = { true },
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
            val drainJob = launch { drainStreamedLines(channel, outputLines, shouldPrune) }
            val h = sandboxController.executeCommandStreaming(
                command = command,
                onStdout = { line -> channel.trySend(TerminalLine.Output(line)) },
                onStderr = { line -> channel.trySend(TerminalLine.Error(line)) },
                sessionId = SandboxSessions.TERMINAL,
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
        if (shouldPrune()) pruneOutput(outputLines)
        setRunning(false)
    }
}

private suspend fun drainStreamedLines(
    channel: Channel<TerminalLine>,
    outputLines: MutableList<TerminalLine>,
    shouldPrune: () -> Boolean = { true },
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
            if (shouldPrune()) pruneOutput(outputLines)
        }
        if (closed) break
        delay(STREAM_FLUSH_INTERVAL_MS.milliseconds)
    }
}

private fun pruneOutput(outputLines: MutableList<TerminalLine>) {
    val excess = outputLines.size - MAX_OUTPUT_LINES
    if (excess > 0) {
        outputLines.subList(0, excess).clear()
    }
}
