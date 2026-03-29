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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inspiredandroid.kai.SandboxController
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.terminal_help_text
import kai.composeapp.generated.resources.terminal_input_placeholder
import kai.composeapp.generated.resources.terminal_run_content_description
import kai.composeapp.generated.resources.terminal_title
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val scrollState = rememberScrollState()
    val outputLines = remember { mutableStateListOf<TerminalLine>().apply { addAll(initialLines) } }
    var inputText by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(false) }
    val colors = terminalColors(darkBackground)
    val focusRequester = remember { FocusRequester() }
    val canSubmit = sandboxController != null && inputText.isNotBlank() && !isRunning
    val isInputEnabled = sandboxController != null && !isRunning
    val executeInput = {
        val controller = sandboxController
        if (controller != null && inputText.isNotBlank() && !isRunning) {
            val cmd = inputText.trim()
            inputText = ""
            scope.launch {
                runCommand(cmd, outputLines, controller) { isRunning = it }
            }
        }
    }

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
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                        color = colors.prompt,
                    ),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Alpine Linux",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = colors.text.copy(alpha = 0.5f),
                    ),
                )
            }
        }

        SelectionContainer(
            modifier = Modifier.weight(1f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                if (outputLines.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.terminal_help_text),
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = colors.dimText,
                        ),
                    )
                }
                outputLines.forEach { line ->
                    when (line) {
                        is TerminalLine.Command -> {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "$ ${line.text}",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    color = colors.prompt,
                                ),
                            )
                        }

                        is TerminalLine.Output -> {
                            Text(
                                text = parseAnsiToAnnotatedString(line.text, colors.text),
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                ),
                            )
                        }

                        is TerminalLine.Error -> {
                            Text(
                                text = parseAnsiToAnnotatedString(line.text, colors.error),
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                ),
                            )
                        }
                    }
                }
                if (isRunning) {
                    Spacer(Modifier.height(4.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = colors.prompt,
                    )
                }
            }
        }

        LaunchedEffect(outputLines.size, isRunning) {
            scrollState.animateScrollTo(scrollState.maxValue)
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
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = colors.prompt,
                ),
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
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = colors.text,
                ),
                placeholder = {
                    Text(
                        text = stringResource(Res.string.terminal_input_placeholder),
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            color = colors.dimText,
                        ),
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
                    onGo = { executeInput() },
                ),
                singleLine = true,
            )
            IconButton(
                onClick = { executeInput() },
                enabled = canSubmit,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(Res.string.terminal_run_content_description),
                    tint = if (canSubmit) colors.prompt else colors.dimText,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
fun TerminalSheet(
    sandboxController: SandboxController,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = terminalColors().inputBg,
        dragHandle = null,
    ) {
        TerminalContent(
            sandboxController = sandboxController,
            modifier = Modifier.fillMaxSize(),
            showHeader = true,
        )
    }
}

private const val MAX_OUTPUT_LINES = 500

private suspend fun runCommand(
    command: String,
    outputLines: MutableList<TerminalLine>,
    sandboxController: SandboxController,
    setRunning: (Boolean) -> Unit,
) {
    if (command == "clear") {
        outputLines.clear()
        return
    }
    outputLines.add(TerminalLine.Command(command))
    setRunning(true)
    try {
        val result = withContext(Dispatchers.Default) {
            sandboxController.executeCommand(command)
        }
        if (result.isNotEmpty()) {
            outputLines.add(TerminalLine.Output(result))
        }
    } catch (e: Exception) {
        outputLines.add(TerminalLine.Error(e.message ?: "Command failed"))
    }
    val excess = outputLines.size - MAX_OUTPUT_LINES
    if (excess > 0) {
        outputLines.subList(0, excess).clear()
    }
    setRunning(false)
}

sealed interface TerminalLine {
    data class Command(val text: String) : TerminalLine
    data class Output(val text: String) : TerminalLine
    data class Error(val text: String) : TerminalLine
}
