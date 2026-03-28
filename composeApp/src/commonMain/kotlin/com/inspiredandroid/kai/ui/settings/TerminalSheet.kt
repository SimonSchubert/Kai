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
import org.jetbrains.compose.resources.stringResource
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.terminal_help_text
import kai.composeapp.generated.resources.terminal_input_placeholder
import kai.composeapp.generated.resources.terminal_run_content_description
import kai.composeapp.generated.resources.terminal_title
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class TerminalColors(
    val bg: Color,
    val inputBg: Color,
    val text: Color,
    val prompt: Color,
    val error: Color,
    val dimText: Color,
)

@Composable
private fun terminalColors(): TerminalColors {
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
fun TerminalSheet(
    sandboxController: SandboxController,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val outputLines = remember { mutableStateListOf<TerminalLine>() }
    var inputText by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(false) }
    var alpineVersion by remember { mutableStateOf("") }
    val colors = terminalColors()
    val focusRequester = remember { FocusRequester() }
    val executeInput = {
        if (inputText.isNotBlank() && !isRunning) {
            val cmd = inputText.trim()
            inputText = ""
            scope.launch {
                runCommand(cmd, outputLines, sandboxController) { isRunning = it }
            }
        }
    }

    LaunchedEffect(Unit) {
        alpineVersion = withContext(Dispatchers.IO) {
            sandboxController.executeCommand("cat /etc/alpine-release").trim()
        }
        focusRequester.requestFocus()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.inputBg,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.bg)
                .imePadding(),
        ) {
            // Header with status bar padding
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
                    text = if (alpineVersion.isNotEmpty()) "Alpine Linux $alpineVersion" else "Alpine Linux",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = colors.text.copy(alpha = 0.5f),
                    ),
                )
            }

            // Output area
            SelectionContainer(
                modifier = Modifier.weight(1f),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
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
                        val (text, color, prefix) = when (line) {
                            is TerminalLine.Command -> Triple("$ ${line.text}", colors.prompt, true)
                            is TerminalLine.Output -> Triple(line.text, colors.text, false)
                            is TerminalLine.Error -> Triple(line.text, colors.error, false)
                        }
                        if (prefix) Spacer(Modifier.height(4.dp))
                        Text(
                            text = text,
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = color,
                            ),
                        )
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

            // Auto-scroll to bottom when output changes
            LaunchedEffect(outputLines.size, isRunning) {
                scrollState.animateScrollTo(scrollState.maxValue)
            }

            // Input area with navigation bar padding
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.inputBg)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
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
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    enabled = !isRunning,
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
                    enabled = inputText.isNotBlank() && !isRunning,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(Res.string.terminal_run_content_description),
                        tint = if (inputText.isNotBlank() && !isRunning) colors.prompt else colors.dimText,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
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
        val result = withContext(Dispatchers.IO) {
            sandboxController.executeCommand(command)
        }
        if (result.isNotEmpty()) {
            outputLines.add(TerminalLine.Output(result))
        }
    } catch (e: Exception) {
        outputLines.add(TerminalLine.Error(e.message ?: "Command failed"))
    }
    while (outputLines.size > MAX_OUTPUT_LINES) {
        outputLines.removeAt(0)
    }
    setRunning(false)
}

private sealed interface TerminalLine {
    data class Command(val text: String) : TerminalLine
    data class Output(val text: String) : TerminalLine
    data class Error(val text: String) : TerminalLine
}
