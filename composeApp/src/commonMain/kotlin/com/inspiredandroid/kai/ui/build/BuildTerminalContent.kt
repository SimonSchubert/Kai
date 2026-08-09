package com.inspiredandroid.kai.ui.build

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.text.TextMeasurer
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
import com.inspiredandroid.kai.build.terminal.TerminalMouseEncoder
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

/** How long a viewport change has to hold still before the PTY hears about it. */
private const val RESIZE_SETTLE_MS = 80L

/** Characters measured in one run, to average out whole-pixel rounding. */
private const val ADVANCE_SAMPLE = 64

/**
 * One terminal cell in pixels, as the grid actually draws it.
 *
 * Measured rather than derived from the style, because both roundings run the
 * wrong way. A single glyph reports a width rounded up to whole pixels, so
 * dividing the viewport by it drops a column the row could have held; a line
 * occupies a fractional number of pixels, and truncating that buys a row too
 * many — the shell then paints a last line that hangs half outside the viewport.
 *
 * [firstLine] is the height of one line and [lineStep] what each further line
 * adds. The two differ on fonts whose natural height exceeds the requested line
 * height, so rows are counted with both rather than by plain division.
 */
internal data class TerminalCellMetrics(
    val advance: Float,
    val firstLine: Int,
    val lineStep: Int,
) {
    /** Whole rows that fit in [height] pixels. */
    fun rowsIn(height: Int): Int = if (height < firstLine) 0 else 1 + (height - firstLine) / lineStep

    /** Column under [x] pixels from the left edge of the grid. */
    fun columnAt(x: Float): Int = (x / advance).toInt()

    /** Row under [y] pixels from the top of the grid. */
    fun rowAt(y: Float): Int = if (y < firstLine) 0 else 1 + ((y - firstLine) / lineStep).toInt()
}

private fun terminalCellMetrics(measurer: TextMeasurer): TerminalCellMetrics {
    val style = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = TerminalFontSize,
        lineHeight = TerminalLineHeight,
    )
    val run = measurer.measure("M".repeat(ADVANCE_SAMPLE), style = style, softWrap = false)
    val oneLine = measurer.measure("M", style = style)
    val twoLines = measurer.measure("M\nM", style = style)
    return TerminalCellMetrics(
        advance = (run.size.width / ADVANCE_SAMPLE.toFloat()).coerceAtLeast(1f),
        firstLine = oneLine.size.height.coerceAtLeast(1),
        lineStep = (twoLines.size.height - oneLine.size.height).coerceAtLeast(1),
    )
}

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
    onMouse: (String) -> Unit,
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
    // A tab is opened to be typed into, so its first appearance raises the
    // keyboard without waiting for a tap. Only the first: switching back to a
    // tab later is often to read what an agent wrote, not to type at it.
    val keyboardRaisedFor = remember { mutableSetOf<String>() }
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

    LaunchedEffect(session.id, busy, rawInput) {
        // Nothing to raise until the shell is live and keys go straight to it.
        if (busy && rawInput && keyboardRaisedFor.add(session.id)) showKeyboardRequest++
    }

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
                        val textMeasurer = rememberTextMeasurer()
                        val cell = remember(textMeasurer, LocalDensity.current) {
                            terminalCellMetrics(textMeasurer)
                        }
                        val maxW = constraints.maxWidth
                        val maxH = constraints.maxHeight
                        val cols = (maxW / cell.advance).toInt().coerceAtLeast(MIN_COLUMNS)
                        val rows = cell.rowsIn(maxH).coerceAtLeast(MIN_ROWS)

                        // Keyed on the session too: a new tab starts at the default
                        // geometry and needs this pass even when the viewport didn't move.
                        // The first pass for a tab reports straight away — the shell is
                        // starting right then, and it is the geometry in hand at that
                        // moment that the PTY and the agent are launched with. Later
                        // passes wait out the keyboard and rotation animations, which
                        // walk through sizes nobody should be resized to.
                        val settled = remember(session.id) { mutableStateOf(false) }
                        LaunchedEffect(cols, rows, session.id) {
                            if (settled.value) delay(RESIZE_SETTLE_MS)
                            settled.value = true
                            onResize(cols, rows)
                        }

                        val keyboardActive = rawInput && busy
                        // An app that asked for mouse reports owns the taps: a
                        // tap is a click on the cell under the finger, not a
                        // request for the keyboard. The keyboard button in the
                        // input bar stays the way to raise the IME.
                        val mouseActive = terminal.mouse.enabled && busy
                        TerminalGrid(
                            snapshot = terminal,
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    when {
                                        mouseActive -> terminalMouseInput(
                                            snapshot = terminal,
                                            cell = cell,
                                            onMouse = onMouse,
                                        )
                                        keyboardActive -> Modifier.pointerInput(Unit) {
                                            detectTapGestures { showKeyboardRequest++ }
                                        }
                                        else -> Modifier
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

/**
 * Reports touches on the cell grid to an app that asked for mouse events.
 *
 * A tap that stays put is a left click on the cell under the finger. A vertical
 * drag is the scroll wheel — one notch per cell row crossed, in the direction
 * that makes the content follow the finger, which is how every list on the
 * device already behaves. Sideways movement is ignored: a TUI has nothing to
 * scroll sideways, and treating it as drag would only make taps harder to land.
 *
 * The raw pointer loop is needed to measure that drag; the higher-level tap and
 * drag detectors cannot answer both questions from one gesture. Changes are
 * never consumed, so the grid keeps its own semantics.
 */
@Composable
private fun terminalMouseInput(
    snapshot: TerminalSnapshot,
    cell: TerminalCellMetrics,
    onMouse: (String) -> Unit,
): Modifier {
    // The snapshot changes on every repaint; reading it through these holders
    // keeps a burst of output from restarting the gesture loop mid-touch.
    val current = rememberUpdatedState(snapshot)
    val send = rememberUpdatedState(onMouse)
    return Modifier.pointerInput(cell) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val state = current.value.mouse
            val col = cell.columnAt(down.position.x)
                .coerceIn(0, (current.value.columns - 1).coerceAtLeast(0))
            val row = cell.rowAt(down.position.y)
                .coerceIn(0, (current.value.rows - 1).coerceAtLeast(0))

            var travelled = 0f
            var pendingScroll = 0f
            var scrolled = false
            var position = down.position

            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                val delta = change.position - position
                position = change.position
                travelled += delta.getDistance()
                pendingScroll += delta.y
                while (pendingScroll >= cell.lineStep) {
                    pendingScroll -= cell.lineStep
                    scrolled = true
                    TerminalMouseEncoder.wheel(up = true, col = col, row = row, state = state)
                        ?.let { send.value(it) }
                }
                while (pendingScroll <= -cell.lineStep) {
                    pendingScroll += cell.lineStep
                    scrolled = true
                    TerminalMouseEncoder.wheel(up = false, col = col, row = row, state = state)
                        ?.let { send.value(it) }
                }
                if (!change.pressed) break
            }

            if (!scrolled && travelled <= viewConfiguration.touchSlop) {
                TerminalMouseEncoder.click(col = col, row = row, state = state)
                    ?.let { send.value(it) }
            }
        }
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
