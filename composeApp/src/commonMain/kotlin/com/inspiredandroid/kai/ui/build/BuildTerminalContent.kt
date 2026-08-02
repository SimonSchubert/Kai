package com.inspiredandroid.kai.ui.build

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inspiredandroid.kai.build.BuildTerminalSession
import com.inspiredandroid.kai.build.PlatformTerminalKeyboard
import com.inspiredandroid.kai.build.supportsRawTerminalInput
import com.inspiredandroid.kai.build.terminal.MIN_COLUMNS
import com.inspiredandroid.kai.build.terminal.MIN_ROWS
import com.inspiredandroid.kai.build.terminal.TerminalKey
import com.inspiredandroid.kai.build.terminal.TerminalModifiers
import com.inspiredandroid.kai.build.terminal.TerminalSnapshot
import com.inspiredandroid.kai.ui.handCursor
import com.inspiredandroid.kai.ui.settings.monoStyle
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.kai_build_terminal_placeholder
import kai.composeapp.generated.resources.kai_build_terminal_raw_hint
import kai.composeapp.generated.resources.kai_build_terminal_run_content_description
import kai.composeapp.generated.resources.kai_build_terminal_running
import kai.composeapp.generated.resources.kai_build_terminal_show_keyboard_content_description
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/** Classic 16-color ANSI palette (dark terminal). */
internal val AnsiPalette = listOf(
    Color(0xFF0C0C0C),
    Color(0xFFC50F1F),
    Color(0xFF13A10E),
    Color(0xFFC19C00),
    Color(0xFF0037DA),
    Color(0xFF881798),
    Color(0xFF3A96DD),
    Color(0xFFCCCCCC),
    Color(0xFF767676),
    Color(0xFFE74856),
    Color(0xFF16C60C),
    Color(0xFFF9F1A5),
    Color(0xFF3B78FF),
    Color(0xFFB4009E),
    Color(0xFF61D6D6),
    Color(0xFFF2F2F2),
)

private val TerminalFontSize = 11.sp
private val TerminalLineHeight = 13.sp

/**
 * Project workspace: the active session's VT cell grid sized to the viewport
 * (desktop-style resize) plus its input bar. Session switching and launching
 * agents live in the bar above, so the grid gets the whole screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BuildTerminalContent(
    session: BuildTerminalSession,
    onSubmitLine: (String) -> Unit,
    onKey: (TerminalKey, TerminalModifiers) -> Unit,
    onText: (String, TerminalModifiers) -> Unit,
    onResize: (columns: Int, rows: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val terminal = session.terminal
    val busy = session.busy
    // Each tab keeps its own draft line.
    var input by remember(session.id) { mutableStateOf("") }
    // Keys straight to the PTY is the default where the platform can do it —
    // that is the only way a TUI reacts while the user is still typing.
    var rawInput by remember { mutableStateOf(supportsRawTerminalInput) }
    var latched by remember { mutableStateOf(TerminalModifiers.None) }
    var showKeyboardRequest by remember { mutableIntStateOf(0) }
    // Switching to line mode while the keyboard is up hands the caret to the
    // field, so a half-typed thought carries on instead of needing a tap.
    var focusInputRequest by remember { mutableIntStateOf(0) }
    val inputFocus = remember { FocusRequester() }
    // IME bottom inset is multiplatform; WindowInsets.isImeVisible is Android-only.
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    // Keyboard mode has nothing to type into here — the grid is the surface — so
    // the bar is only chrome (hint, show-keyboard). Hide it while the soft
    // keyboard is up to give the cell grid that extra row; the key row above
    // keeps the mode toggle reachable either way.
    val hideInputBar = rawInput && imeVisible
    val bg = AnsiPalette[0]

    // A latch stands for one press, wherever that press came from.
    val consumeLatch: (TerminalModifiers) -> TerminalModifiers = { reported ->
        val merged = latched + reported
        latched = TerminalModifiers.None
        merged
    }

    val submit = {
        val line = input
        if (line.isNotEmpty() || busy) {
            // Carriage return, not line feed: apps in raw mode read the byte
            // directly and only CR counts as Enter.
            onSubmitLine(line + "\r")
            input = ""
        }
    }

    // Full-bleed sideways: the cell grid keeps its own inset, so an outer margin
    // only cost columns.
    Column(modifier = modifier.fillMaxSize().imePadding()) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp).weight(1f),
            shape = RoundedCornerShape(12.dp),
            color = bg,
            tonalElevation = 2.dp,
        ) {
            Column {
                Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    ) {
                        val density = LocalDensity.current
                        val textMeasurer = rememberTextMeasurer()
                        val cellMetrics = remember(textMeasurer, density) {
                            val style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = TerminalFontSize,
                                lineHeight = TerminalLineHeight,
                            )
                            val m = textMeasurer.measure("M", style = style)
                            val cellW = m.size.width.coerceAtLeast(1)
                            val cellH = with(density) { TerminalLineHeight.toPx() }.toInt().coerceAtLeast(1)
                            cellW to cellH
                        }
                        val (cellW, cellH) = cellMetrics
                        val maxW = constraints.maxWidth
                        val maxH = constraints.maxHeight
                        val cols = (maxW / cellW).coerceAtLeast(MIN_COLUMNS)
                        val rows = (maxH / cellH).coerceAtLeast(MIN_ROWS)

                        // Keyed on the session too: a new tab starts at the default
                        // geometry and needs this pass even when the viewport didn't move.
                        LaunchedEffect(cols, rows, session.id) {
                            delay(80)
                            onResize(cols, rows)
                        }

                        val keyboardActive = rawInput && busy
                        TerminalGrid(
                            snapshot = terminal,
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (keyboardActive) {
                                        Modifier.pointerInput(Unit) {
                                            detectTapGestures { showKeyboardRequest++ }
                                        }
                                    } else {
                                        Modifier
                                    },
                                ),
                        )

                        if (keyboardActive) {
                            PlatformTerminalKeyboard(
                                showKeyboardRequest = showKeyboardRequest,
                                onKey = { key, reported -> onKey(key, consumeLatch(reported)) },
                                onText = { text, reported -> onText(text, consumeLatch(reported)) },
                                modifier = Modifier.size(1.dp),
                            )
                        }
                    }

                    // OSC 8 hyperlinks (Grok login puts the URL here, not as visible cells).
                    if (terminal.hyperlinks.isNotEmpty()) {
                        TerminalHyperlinkBar(urls = terminal.hyperlinks)
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

                TerminalKeyRow(
                    enabled = busy,
                    latched = latched,
                    onLatchChange = { latched = it },
                    onKey = { key -> onKey(key, consumeLatch(TerminalModifiers.None)) },
                    rawInput = rawInput,
                    onToggleInputMode = if (supportsRawTerminalInput) {
                        {
                            rawInput = !rawInput
                            if (!rawInput && imeVisible) focusInputRequest++
                        }
                    } else {
                        null
                    },
                )

                if (!hideInputBar) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1A1A1A))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (busy) "›" else "$",
                            style = monoStyle(14.sp, AnsiPalette[10]),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        if (rawInput) {
                            Text(
                                text = stringResource(Res.string.kai_build_terminal_raw_hint),
                                style = monoStyle(13.sp, AnsiPalette[8]),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = { showKeyboardRequest++ },
                                enabled = busy,
                                modifier = Modifier.handCursor(),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Keyboard,
                                    contentDescription = stringResource(
                                        Res.string.kai_build_terminal_show_keyboard_content_description,
                                    ),
                                    tint = AnsiPalette[7],
                                )
                            }
                        } else {
                            // Guarded: a focus request that raises the IME crashes
                            // the screenshot renderer, which has no IME to raise.
                            val inspecting = LocalInspectionMode.current
                            LaunchedEffect(focusInputRequest) {
                                if (focusInputRequest > 0 && !inspecting) inputFocus.requestFocus()
                            }
                            TextField(
                                value = input,
                                onValueChange = { input = it },
                                modifier = Modifier.weight(1f).focusRequester(inputFocus),
                                enabled = busy,
                                textStyle = monoStyle(14.sp, AnsiPalette[7]),
                                placeholder = {
                                    Text(
                                        text = stringResource(
                                            if (busy) {
                                                Res.string.kai_build_terminal_running
                                            } else {
                                                Res.string.kai_build_terminal_placeholder
                                            },
                                        ),
                                        style = monoStyle(14.sp, AnsiPalette[8]),
                                    )
                                },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent,
                                    cursorColor = AnsiPalette[10],
                                    focusedTextColor = AnsiPalette[7],
                                    unfocusedTextColor = AnsiPalette[7],
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                keyboardActions = KeyboardActions(onGo = { submit() }),
                                singleLine = true,
                            )
                        }

                        if (busy && !rawInput) {
                            IconButton(onClick = submit, modifier = Modifier.handCursor()) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = stringResource(
                                        Res.string.kai_build_terminal_run_content_description,
                                    ),
                                    tint = AnsiPalette[10],
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Shows URLs that TUIs only embed as OSC 8 hyperlinks (invisible on the cell grid).
 * Tap opens in the system browser; text is selectable for long-press copy.
 *
 * Auto-hides after [HYPERLINK_DISPLAY_MS] so login links do not stick around
 * forever once the user has signed in (or dismissed the flow). A new URL set
 * restarts the timer.
 */
@Composable
private fun TerminalHyperlinkBar(urls: ImmutableList<String>) {
    var dismissed by remember { mutableStateOf(false) }
    LaunchedEffect(urls) {
        dismissed = false
        delay(HYPERLINK_DISPLAY_MS)
        dismissed = true
    }
    if (dismissed || urls.isEmpty()) return

    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF141414))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        urls.forEach { url ->
            SelectionContainer {
                Text(
                    text = url,
                    style = monoStyle(12.sp, AnsiPalette[14]),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .handCursor()
                        .clickable {
                            runCatching { uriHandler.openUri(url) }
                        },
                )
            }
        }
    }
}

/** How long login / OSC 8 links stay under the grid before the bar goes away. */
private const val HYPERLINK_DISPLAY_MS = 2 * 60 * 1000L

/**
 * Style for one cell. [fgIndex]/[bgIndex] are already clamped palette indices;
 * a zero background means "no background" so the surface shows through.
 */
private fun cellSpanStyle(fgIndex: Int, bgIndex: Int, bold: Boolean, isCursor: Boolean): SpanStyle {
    val fg = AnsiPalette.getOrElse(fgIndex) { AnsiPalette[7] }
    val bg = AnsiPalette.getOrElse(bgIndex) { AnsiPalette[0] }
    return SpanStyle(
        color = if (isCursor) bg else fg,
        background = if (isCursor) {
            fg
        } else if (bgIndex == 0) {
            Color.Unspecified
        } else {
            bg
        },
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        fontFamily = FontFamily.Monospace,
        fontSize = TerminalFontSize,
        letterSpacing = 0.sp,
    )
}

/**
 * Flattens the cell grid into the string the terminal draws.
 *
 * One span per *run* of identically styled cells, not one per cell. A span per
 * cell puts a style transition on every character, so paragraph layout cost
 * scales with the cell count — at a viewport-sized grid that is tens of
 * thousands of spans rebuilt on every PTY revision. Real screens change style a
 * handful of times per row, so runs collapse that to roughly one span per row.
 */
internal fun buildTerminalText(snapshot: TerminalSnapshot): AnnotatedString = buildAnnotatedString {
    val run = StringBuilder(snapshot.columns.coerceAtLeast(1))
    var runFg = -1
    var runBg = -1
    var runBold = false
    var runCursor = false

    fun flushRun() {
        if (run.isEmpty()) return
        withStyle(cellSpanStyle(runFg, runBg, runBold, runCursor)) { append(run.toString()) }
        run.setLength(0)
    }

    for (row in 0 until snapshot.rows) {
        for (col in 0 until snapshot.columns) {
            val cell = snapshot.cellAt(col, row)
            val fg = cell.fg.coerceIn(0, 15)
            val bg = cell.bg.coerceIn(0, 15)
            // The cursor inverts its own cell, so it always breaks the run.
            val isCursor = snapshot.cursorVisible &&
                col == snapshot.cursorCol &&
                row == snapshot.cursorRow
            if (fg != runFg || bg != runBg || cell.bold != runBold || isCursor != runCursor) {
                flushRun()
            }
            runFg = fg
            runBg = bg
            runBold = cell.bold
            runCursor = isCursor
            // Cells the shell never wrote hold NUL; they render as blanks.
            run.append(if (cell.char == Char.MIN_VALUE) ' ' else cell.char)
        }
        flushRun()
        if (row < snapshot.rows - 1) append('\n')
    }
}

@Composable
private fun TerminalGrid(
    snapshot: TerminalSnapshot,
    modifier: Modifier = Modifier,
) {
    val revision = snapshot.revision
    val annotated = remember(revision, snapshot.columns, snapshot.rows) {
        buildTerminalText(snapshot)
    }
    BasicText(
        text = annotated,
        modifier = modifier,
        style = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = TerminalFontSize,
            lineHeight = TerminalLineHeight,
            color = AnsiPalette[7],
        ),
        softWrap = false,
        maxLines = snapshot.rows.coerceAtLeast(1),
    )
}
