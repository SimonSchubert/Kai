package com.inspiredandroid.kai.ui.markdown.math

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Typographic ratios relative to the current base font size. Picked visually; not TeX-exact.
private const val SCRIPT_SCALE = 0.7f
private const val RADICAL_SYMBOL_SCALE = 1.2f
private const val BIG_OP_SCALE_DISPLAY = 1.6f
private const val BIG_OP_SCALE_INLINE = 1.3f
private const val DELIMITER_SCALE = 1.5f
private const val ACCENT_GLYPH_SCALE = 0.85f
private const val MATRIX_DELIM_SCALE_PER_ROW = 0.6f
private const val MATRIX_DELIM_MAX_SCALE = 4.0f
private val MATRIX_COL_GAP = 12.dp
private val MATRIX_ROW_GAP = 4.dp
private val SCRIPT_FONT_SIZE = 12.sp

/** Reused sentinel for absent matrix cells — avoids allocating a new empty Group per recomposition. */
private val EMPTY_MATRIX_CELL: MathAtom = Group(emptyList())

/** Glyphs for narrow accents. Widening accents (BAR, OVERLINE, WIDEHAT, WIDETILDE) render as a drawn line. */
private val ACCENT_GLYPHS = mapOf(
    AccentKind.HAT to "^",
    AccentKind.WIDEHAT to "^",
    AccentKind.TILDE to "~",
    AccentKind.WIDETILDE to "~",
    AccentKind.VEC to "→",
    AccentKind.DOT to "·",
    AccentKind.DDOT to "··",
)

/**
 * Renders a LaTeX math fragment. The parser tolerates malformed input; unknown commands
 * render as literal `\name` text so nothing crashes.
 *
 * @param display if true, big operators typeset limits above/below and the root layout is
 *   given display-math sizing. Inline formulas keep limits as sub/superscripts.
 */
@Composable
fun MathFormula(
    latex: String,
    display: Boolean = false,
    modifier: Modifier = Modifier,
    baseSize: TextUnit = if (display) 20.sp else 16.sp,
) {
    val atom = remember(latex) { MathParser.parse(latex) }
    val color = LocalContentColor.current
    Box(modifier) {
        AtomRenderer(atom, display = display, baseSize = baseSize, color = color)
    }
}

@Composable
private fun AtomRenderer(
    atom: MathAtom,
    display: Boolean,
    baseSize: TextUnit,
    color: Color,
) {
    when (atom) {
        is Group -> GroupRenderer(atom.atoms, display, baseSize, color)
        is Sym -> SymText(atom, baseSize, color)
        is Space -> Spacer(Modifier.width(emsToDp(atom.emWidth, baseSize)))
        is Frac -> FractionRenderer(atom, display, baseSize, color)
        is Script -> ScriptRenderer(atom, display, baseSize, color)
        is Radical -> RadicalRenderer(atom, display, baseSize, color)
        is LargeOp -> LargeOpRenderer(atom, display, baseSize, color)
        is Delim -> DelimRenderer(atom, display, baseSize, color)
        is Styled -> StyledRenderer(atom, display, baseSize, color)
        is Accent -> AccentRenderer(atom, display, baseSize, color)
        is Matrix -> MatrixRenderer(atom, display, baseSize, color)
    }
}

/**
 * Runs of inline-renderable atoms (letters, symbols, scripts, styled text) collapse into a
 * single [Text] so selection and copy work. Structural atoms (fractions, radicals, delimiters,
 * stacked big ops) each render as their own composable and are stitched via [Row].
 */
@Composable
private fun GroupRenderer(
    atoms: List<MathAtom>,
    display: Boolean,
    baseSize: TextUnit,
    color: Color,
) {
    if (atoms.isEmpty()) return
    if (atoms.size == 1) {
        AtomRenderer(atoms[0], display, baseSize, color)
        return
    }

    val segments = splitIntoSegments(atoms, display)
    if (segments.size == 1 && segments[0] is RunSegment) {
        InlineRun((segments[0] as RunSegment).atoms, baseSize, color)
        return
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        for (seg in segments) {
            when (seg) {
                is RunSegment -> InlineRun(seg.atoms, baseSize, color)
                is ComplexSegment -> AtomRenderer(seg.atom, display, baseSize, color)
            }
        }
    }
}

private sealed interface Segment
private data class RunSegment(val atoms: List<MathAtom>) : Segment
private data class ComplexSegment(val atom: MathAtom) : Segment

private fun splitIntoSegments(atoms: List<MathAtom>, display: Boolean): List<Segment> {
    val out = mutableListOf<Segment>()
    val run = mutableListOf<MathAtom>()
    for (a in atoms) {
        if (isInlineRenderable(a, display)) {
            run += a
        } else {
            if (run.isNotEmpty()) {
                out += RunSegment(run.toList())
                run.clear()
            }
            out += ComplexSegment(a)
        }
    }
    if (run.isNotEmpty()) out += RunSegment(run.toList())
    return out
}

private fun isInlineRenderable(atom: MathAtom, display: Boolean): Boolean = when (atom) {
    is Sym, is Space -> true

    is Script -> isInlineRenderable(atom.base, display) &&
        (atom.sub?.let(::isScriptContentInlineRenderable) ?: true) &&
        (atom.sup?.let(::isScriptContentInlineRenderable) ?: true)

    is Styled -> atom.style != MathStyle.TEXT && atom.atoms.all { isInlineRenderable(it, display) }

    is Group -> atom.atoms.all { isInlineRenderable(it, display) }

    is LargeOp -> !display && !atom.alwaysLimits

    is Frac, is Radical, is Delim, is Accent, is Matrix -> false
}

private fun isScriptContentInlineRenderable(atom: MathAtom): Boolean = when (atom) {
    is Sym, is Space -> true
    is Group -> atom.atoms.all(::isScriptContentInlineRenderable)
    is Styled -> atom.style != MathStyle.TEXT && atom.atoms.all(::isScriptContentInlineRenderable)
    else -> false
}

@Composable
private fun InlineRun(atoms: List<MathAtom>, baseSize: TextUnit, color: Color) {
    val style = MaterialTheme.typography.bodyLarge
    val annotated = buildAnnotatedString {
        for (a in atoms) appendAtomInline(a, color)
    }
    Text(text = annotated, style = style.copy(fontSize = baseSize, color = color))
}

private fun AnnotatedString.Builder.appendAtomInline(atom: MathAtom, color: Color) {
    when (atom) {
        is Sym -> append(symSpan(atom))

        is Space -> {
            // Approximation — the AnnotatedString.Builder has no Density access, so we pick the
            // nearest Unicode space width (thin / en / em / 2×em) instead of a pixel-exact width.
            val raw = atom.emWidth
            when {
                raw <= 0f -> Unit
                raw < 0.3f -> append('\u2009')
                raw < 0.8f -> append('\u2002')
                raw < 1.5f -> append('\u2003')
                else -> append('\u2003').also { append('\u2003') }
            }
        }

        is Styled -> {
            val span = styleSpan(atom.style)
            withStyle(span) {
                if (atom.style == MathStyle.DOUBLE_STRUCK || atom.style == MathStyle.CALLIGRAPHIC) {
                    for (inner in atom.atoms) appendMapped(inner, atom.style)
                } else {
                    for (inner in atom.atoms) appendAtomInline(inner, color)
                }
            }
        }

        is Group -> for (inner in atom.atoms) appendAtomInline(inner, color)

        is Script -> {
            appendAtomInline(atom.base, color)
            appendScripts(atom.sub, atom.sup, color)
        }

        is LargeOp -> {
            append(atom.symbol)
            appendScripts(atom.sub, atom.sup, color)
        }

        is Frac, is Radical, is Delim, is Accent, is Matrix -> {
            // Shouldn't reach here — [isInlineRenderable] keeps these off the AnnotatedString path.
            append('\u25A1')
        }
    }
}

private fun AnnotatedString.Builder.appendScripts(sub: MathAtom?, sup: MathAtom?, color: Color) {
    sup?.let {
        withStyle(SpanStyle(fontSize = SCRIPT_FONT_SIZE, baselineShift = BaselineShift.Superscript)) {
            appendAtomInline(it, color)
        }
    }
    sub?.let {
        withStyle(SpanStyle(fontSize = SCRIPT_FONT_SIZE, baselineShift = BaselineShift.Subscript)) {
            appendAtomInline(it, color)
        }
    }
}

private fun AnnotatedString.Builder.appendMapped(atom: MathAtom, style: MathStyle) {
    when (atom) {
        is Sym -> {
            val mapped = atom.text.map { ch ->
                when (style) {
                    MathStyle.DOUBLE_STRUCK -> MathSymbols.mapDoubleStruck(ch)
                    MathStyle.CALLIGRAPHIC -> MathSymbols.mapCalligraphic(ch)
                    else -> ch.toString()
                }
            }.joinToString("")
            append(mapped)
        }

        is Group -> for (inner in atom.atoms) appendMapped(inner, style)

        else -> appendAtomInline(atom, Color.Unspecified)
    }
}

private fun symSpan(sym: Sym): AnnotatedString {
    val italic = sym.kind == SymKind.VARIABLE && sym.text.length == 1 && sym.text[0].isLetter() && !isGreek(sym.text[0])
    return buildAnnotatedString {
        val spacing = kindSpacing(sym.kind)
        if (spacing.first > 0) append('\u2009')
        withStyle(SpanStyle(fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal)) {
            append(sym.text)
        }
        if (spacing.second > 0) append('\u2009')
    }
}

private fun kindSpacing(kind: SymKind): Pair<Int, Int> = when (kind) {
    SymKind.BIN_OP, SymKind.REL_OP -> 1 to 1
    SymKind.PUNCT -> 0 to 1
    else -> 0 to 0
}

private fun isGreek(ch: Char): Boolean = ch.code in 0x0370..0x03FF

private fun styleSpan(style: MathStyle): SpanStyle = when (style) {
    MathStyle.TEXT -> SpanStyle(fontFamily = FontFamily.Default, fontStyle = FontStyle.Normal)

    MathStyle.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)

    MathStyle.BOLD_ITALIC -> SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)

    MathStyle.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)

    MathStyle.ROMAN -> SpanStyle(fontStyle = FontStyle.Normal)

    MathStyle.DOUBLE_STRUCK -> SpanStyle()

    // handled via mapping
    MathStyle.CALLIGRAPHIC -> SpanStyle() // handled via mapping
}

@Composable
private fun SymText(sym: Sym, baseSize: TextUnit, color: Color) {
    val text = symSpan(sym)
    Text(text = text, style = MaterialTheme.typography.bodyLarge.copy(fontSize = baseSize, color = color))
}

@Composable
private fun FractionRenderer(
    frac: Frac,
    display: Boolean,
    baseSize: TextUnit,
    color: Color,
) {
    val horizontalPadding = 2.dp
    val verticalBarPadding = 2.dp
    val barColor = if (frac.drawBar) color else Color.Transparent
    Layout(
        modifier = Modifier.padding(horizontal = horizontalPadding),
        content = {
            AtomRenderer(frac.num, display, baseSize, color)
            HorizontalBar(barColor, thickness = 1.dp)
            AtomRenderer(frac.den, display, baseSize, color)
        },
    ) { measurables, constraints ->
        // Strip min-width so numerator/denominator measure at their intrinsic content size.
        val childConstraints = constraints.copy(minWidth = 0)
        val numP = measurables[0].measure(childConstraints)
        val denP = measurables[2].measure(childConstraints)
        val width = maxOf(numP.width, denP.width)
        val barPaddingPx = verticalBarPadding.roundToPx()
        val barHeightPx = 1.dp.roundToPx()
        val barP = measurables[1].measure(Constraints.fixed(width, barHeightPx))
        val height = numP.height + barPaddingPx + barHeightPx + barPaddingPx + denP.height

        layout(width, height) {
            numP.placeRelative((width - numP.width) / 2, 0)
            barP.placeRelative(0, numP.height + barPaddingPx)
            denP.placeRelative((width - denP.width) / 2, numP.height + barPaddingPx + barHeightPx + barPaddingPx)
        }
    }
}

@Composable
private fun ScriptRenderer(
    script: Script,
    display: Boolean,
    baseSize: TextUnit,
    color: Color,
) {
    // If the base is inline-renderable we defer to the AnnotatedString path for better
    // baseline handling. This branch is hit only when the base itself is structural.
    Row(verticalAlignment = Alignment.CenterVertically) {
        AtomRenderer(script.base, display, baseSize, color)
        val scriptSize = (baseSize.value * SCRIPT_SCALE).sp
        Column {
            if (script.sup != null) {
                AtomRenderer(script.sup, display = false, baseSize = scriptSize, color = color)
            } else {
                Spacer(Modifier.height(1.dp))
            }
            if (script.sub != null) {
                AtomRenderer(script.sub, display = false, baseSize = scriptSize, color = color)
            } else {
                Spacer(Modifier.height(1.dp))
            }
        }
    }
}

@Composable
private fun RadicalRenderer(
    radical: Radical,
    display: Boolean,
    baseSize: TextUnit,
    color: Color,
) {
    val strokePx = 1.dp
    val topPad = 3.dp
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (radical.index != null) {
            val indexSize = (baseSize.value * SCRIPT_SCALE).sp
            Box(Modifier.padding(bottom = 6.dp)) {
                AtomRenderer(radical.index, display = false, baseSize = indexSize, color = color)
            }
        }
        Text(
            text = "√",
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = (baseSize.value * RADICAL_SYMBOL_SCALE).sp, color = color),
        )
        Box(
            Modifier
                .padding(top = topPad)
                .drawBehind {
                    drawLine(
                        color = color,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = strokePx.toPx(),
                    )
                }
                .padding(top = 2.dp, start = 2.dp, end = 2.dp),
        ) {
            AtomRenderer(radical.radicand, display, baseSize, color)
        }
    }
}

@Composable
private fun AccentRenderer(
    accent: Accent,
    display: Boolean,
    baseSize: TextUnit,
    color: Color,
) {
    val glyphSize = (baseSize.value * ACCENT_GLYPH_SCALE).sp
    val glyphStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = glyphSize, color = color)
    // BAR visually matches OVERLINE — both draw a horizontal line and need explicit width.
    // A narrower traditional \bar glyph would also work but requires a font with that metric.
    val widening = accent.kind == AccentKind.OVERLINE ||
        accent.kind == AccentKind.BAR ||
        accent.kind == AccentKind.WIDEHAT ||
        accent.kind == AccentKind.WIDETILDE
    val lineThicknessDp = 1.dp
    // Narrow visual gap: glyph-based accents (hat, tilde, dot, vec) have built-in whitespace
    // above their ink, so we don't need additional padding. Widening accents (bar/overline)
    // are 1dp strokes and benefit from a small gap instead.
    val accentGapDp = if (widening) 2.dp else (-4).dp

    Layout(
        content = {
            val glyph = ACCENT_GLYPHS[accent.kind]
            if (glyph != null) {
                Text(glyph, style = glyphStyle)
            } else {
                HorizontalBar(color, lineThicknessDp)
            }
            AtomRenderer(accent.base, display, baseSize, color)
        },
    ) { measurables, constraints ->
        val childConstraints = constraints.copy(minWidth = 0)
        val baseP = measurables[1].measure(childConstraints)
        val accentP = if (widening) {
            measurables[0].measure(Constraints.fixed(baseP.width, lineThicknessDp.roundToPx()))
        } else {
            measurables[0].measure(childConstraints)
        }
        val gapPx = accentGapDp.roundToPx()
        val totalWidth = maxOf(baseP.width, accentP.width)
        val totalHeight = accentP.height + gapPx + baseP.height
        layout(totalWidth, totalHeight) {
            accentP.placeRelative((totalWidth - accentP.width) / 2, 0)
            baseP.placeRelative((totalWidth - baseP.width) / 2, accentP.height + gapPx)
        }
    }
}

/**
 * Thin horizontal line that stretches to its parent's width. Shared by fraction bars and
 * `\bar` / `\overline` accents. The center-aligned draw means the line is drawn at y = height/2
 * with `strokeWidth = height`, giving the full height as ink.
 */
@Composable
private fun HorizontalBar(color: Color, thickness: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .height(thickness)
            .drawBehind {
                drawLine(
                    color = color,
                    start = Offset(0f, size.height / 2),
                    end = Offset(size.width, size.height / 2),
                    strokeWidth = size.height,
                )
            },
    )
}

@Composable
private fun LargeOpRenderer(
    op: LargeOp,
    display: Boolean,
    baseSize: TextUnit,
    color: Color,
) {
    val stacked = display || op.alwaysLimits
    if (!stacked) {
        // Inline big op renders as op + scripts — reuse the script path.
        val base = Sym(op.symbol, SymKind.ORDINARY)
        val wrapped: MathAtom = if (op.sub != null || op.sup != null) {
            Script(base, op.sub, op.sup)
        } else {
            base
        }
        AtomRenderer(wrapped, display, baseSize, color)
        return
    }

    val opSize = (baseSize.value * if (display) BIG_OP_SCALE_DISPLAY else BIG_OP_SCALE_INLINE).sp
    val limitSize = (baseSize.value * SCRIPT_SCALE).sp
    val opText = op.symbol

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (op.sup != null) {
            AtomRenderer(op.sup, display = false, baseSize = limitSize, color = color)
        }
        Text(
            text = opText,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = opSize, color = color),
        )
        if (op.sub != null) {
            AtomRenderer(op.sub, display = false, baseSize = limitSize, color = color)
        }
    }
}

@Composable
private fun MatrixRenderer(
    matrix: Matrix,
    display: Boolean,
    baseSize: TextUnit,
    color: Color,
) {
    // Wrap the grid in delimiters scaled to the grid's height — similar approach to DelimRenderer.
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (matrix.delim.left.isNotEmpty()) {
            MatrixDelimiter(matrix.delim.left, baseSize, color, rowCount = matrix.rows.size)
        }
        MatrixGrid(matrix, display, baseSize, color)
        if (matrix.delim.right.isNotEmpty()) {
            MatrixDelimiter(matrix.delim.right, baseSize, color, rowCount = matrix.rows.size)
        }
    }
}

@Composable
private fun MatrixDelimiter(text: String, baseSize: TextUnit, color: Color, rowCount: Int) {
    // Scale delim height to match grid row count; clamped so very tall matrices don't explode.
    val scale = (DELIMITER_SCALE + MATRIX_DELIM_SCALE_PER_ROW * (rowCount - 1))
        .coerceAtMost(MATRIX_DELIM_MAX_SCALE)
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge.copy(
            fontSize = (baseSize.value * scale).sp,
            color = color,
        ),
    )
}

@Composable
private fun MatrixGrid(
    matrix: Matrix,
    display: Boolean,
    baseSize: TextUnit,
    color: Color,
) {
    val rowCount = matrix.rows.size
    if (rowCount == 0) return
    val colCount = matrix.rows.maxOf { it.size }

    Layout(
        modifier = Modifier.padding(horizontal = 4.dp),
        content = {
            for (row in matrix.rows) {
                for (colIndex in 0 until colCount) {
                    val cell = row.getOrNull(colIndex) ?: EMPTY_MATRIX_CELL
                    // Box wrapper guarantees one measurable per cell — an empty Group alone
                    // emits zero composables, which would break the grid indexing below.
                    Box { AtomRenderer(cell, display, baseSize, color) }
                }
            }
        },
    ) { measurables, constraints ->
        val childConstraints = constraints.copy(minWidth = 0)
        val placeables = measurables.map { it.measure(childConstraints) }
        val cellGrid: List<List<androidx.compose.ui.layout.Placeable>> =
            (0 until rowCount).map { r -> placeables.subList(r * colCount, (r + 1) * colCount) }
        val colWidths = IntArray(colCount) { c -> cellGrid.maxOf { it[c].width } }
        val rowHeights = IntArray(rowCount) { r -> cellGrid[r].maxOf { it.height } }
        val colGapPx = MATRIX_COL_GAP.roundToPx()
        val rowGapPx = MATRIX_ROW_GAP.roundToPx()
        val totalWidth = colWidths.sum() + colGapPx * (colCount - 1).coerceAtLeast(0)
        val totalHeight = rowHeights.sum() + rowGapPx * (rowCount - 1).coerceAtLeast(0)

        layout(totalWidth, totalHeight) {
            var y = 0
            for (r in 0 until rowCount) {
                var x = 0
                for (c in 0 until colCount) {
                    val p = cellGrid[r][c]
                    val colW = colWidths[c]
                    val rowH = rowHeights[r]
                    val xOffset = when (matrix.alignMode) {
                        MatrixAlign.CENTERED -> (colW - p.width) / 2
                        MatrixAlign.LEFT -> 0
                        MatrixAlign.ALIGN_RL -> if (c % 2 == 0) colW - p.width else 0
                    }
                    val yOffset = (rowH - p.height) / 2
                    p.placeRelative(x + xOffset, y + yOffset)
                    x += colW + colGapPx
                }
                y += rowHeights[r] + rowGapPx
            }
        }
    }
}

@Composable
private fun DelimRenderer(
    delim: Delim,
    display: Boolean,
    baseSize: TextUnit,
    color: Color,
) {
    // Rough delim stretching: scale the delimiter's font size with the base size. Works well
    // for fractions (which are ~2x base tall) but isn't perfect for deep nesting.
    val delimSize = (baseSize.value * DELIMITER_SCALE).sp
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (delim.left.isNotEmpty()) {
            Text(
                text = delim.left,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = delimSize, color = color),
            )
        }
        AtomRenderer(delim.content, display, baseSize, color)
        if (delim.right.isNotEmpty()) {
            Text(
                text = delim.right,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = delimSize, color = color),
            )
        }
    }
}

@Composable
private fun StyledRenderer(
    styled: Styled,
    display: Boolean,
    baseSize: TextUnit,
    color: Color,
) {
    if (styled.style == MathStyle.TEXT) {
        // Join raw text from the children verbatim — `\text{hello world}` preserves the space.
        val text = styled.atoms.joinToString("") { (it as? Sym)?.text ?: "" }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = baseSize, color = color),
        )
        return
    }
    // Inline-renderable styled group: build annotated string.
    val annotated = buildAnnotatedString {
        appendAtomInline(styled, color)
    }
    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = baseSize, color = color),
    )
}

private fun emsToDp(em: Float, baseSize: TextUnit): Dp {
    // Approximate: 1em ≈ baseSize. We don't have access to Density here, so use sp value as dp.
    // This is imperfect but close enough for thin/medium math spacing.
    return (baseSize.value * em).dp
}
