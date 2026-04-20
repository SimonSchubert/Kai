package com.inspiredandroid.kai.ui.markdown.math

import androidx.compose.runtime.Immutable

/**
 * Minimal LaTeX math AST. Deliberately scoped to the subset of commands that show up in
 * real LLM output: fractions, scripts, radicals, big operators (with limits), delimiters,
 * a handful of font styles, and a symbol lookup for greek letters and operators.
 *
 * Unknown commands degrade to a literal [Sym] carrying the raw `\name` text — nothing in
 * the renderer crashes on malformed input.
 */
@Immutable
sealed interface MathAtom

/** Single typeset glyph: a letter, digit, operator symbol, or mapped LaTeX command. */
@Immutable
data class Sym(val text: String, val kind: SymKind = SymKind.ORDINARY) : MathAtom

enum class SymKind {
    /** Variable letters — rendered italic. */
    VARIABLE,

    /** Digits, punctuation, unit-like glyphs — rendered upright. */
    ORDINARY,

    /** Binary operators (+, -, ·, ×) — upright with symmetric spacing. */
    BIN_OP,

    /** Relation operators (=, <, >, ≤, ≈, →) — upright with symmetric spacing. */
    REL_OP,

    /** Function names like sin, cos, log, lim — upright, not italic. */
    FUNCTION,

    /** Opening delimiter that doesn't stretch (e.g. a bare `(` without \left). */
    OPEN,

    /** Closing delimiter. */
    CLOSE,

    /** Punctuation — upright, no extra spacing. */
    PUNCT,
}

@Immutable
data class Group(val atoms: List<MathAtom>) : MathAtom

@Immutable
data class Frac(val num: MathAtom, val den: MathAtom, val drawBar: Boolean = true) : MathAtom

/** Subscript/superscript attached to [base]. One or both of [sub]/[sup] may be present. */
@Immutable
data class Script(val base: MathAtom, val sub: MathAtom?, val sup: MathAtom?) : MathAtom

@Immutable
data class Radical(val index: MathAtom?, val radicand: MathAtom) : MathAtom

/**
 * Big operator like ∑, ∫, ∏, ⋃. In display mode [sub]/[sup] are typeset above/below the
 * operator (limits); in inline mode they fall through to [Script] positioning. When
 * [alwaysLimits] is true (e.g. `\lim`), limits are used even inline.
 */
@Immutable
data class LargeOp(
    val symbol: String,
    val sub: MathAtom? = null,
    val sup: MathAtom? = null,
    val alwaysLimits: Boolean = false,
) : MathAtom

/** `\left X ... \right Y` — brackets stretch to the height of [content]. */
@Immutable
data class Delim(val left: String, val right: String, val content: MathAtom) : MathAtom

@Immutable
data class Styled(val style: MathStyle, val atoms: List<MathAtom>) : MathAtom

/**
 * `\hat{x}`, `\bar{x}`, `\vec{v}`, `\tilde{y}`, `\dot{x}`, `\ddot{x}` — single-glyph accent
 * centered above [base]. The widening variants (`\overline`, `\widehat`, `\widetilde`) stretch
 * to match the base's width instead.
 */
@Immutable
data class Accent(val base: MathAtom, val kind: AccentKind) : MathAtom

enum class AccentKind {
    HAT,
    BAR,
    VEC,
    TILDE,
    DOT,
    DDOT,
    OVERLINE,
    WIDEHAT,
    WIDETILDE,
}

/**
 * A 2D grid of cells from environments like `pmatrix`, `cases`, or `aligned`. Rows are
 * separated by `\\` and cells by `&` in the source; cells may themselves be arbitrary math.
 */
@Immutable
data class Matrix(
    val rows: List<List<MathAtom>>,
    val delim: MatrixDelim,
    val alignMode: MatrixAlign = MatrixAlign.CENTERED,
) : MathAtom

enum class MatrixDelim(val left: String, val right: String) {
    NONE("", ""),
    PAREN("(", ")"),
    BRACKET("[", "]"),
    BRACE("{", "}"),
    VBAR("|", "|"),
    DBLVBAR("‖", "‖"),
    CASES("{", ""),
}

enum class MatrixAlign {
    /** All cells horizontally centered — default for pmatrix / bmatrix / matrix / vmatrix. */
    CENTERED,

    /** All cells left-aligned — used by `cases`. */
    LEFT,

    /** Odd columns right-aligned, even columns left-aligned — `aligned` / `align`. */
    ALIGN_RL,
}

enum class MathStyle {
    /** `\text{...}` — upright, rendered as ordinary text with spaces preserved. */
    TEXT,

    /** `\mathbf{...}` — bold upright. */
    BOLD,

    /** `\boldsymbol{...}` — bold italic, used for bold greek letters and bold variables. */
    BOLD_ITALIC,

    /** `\mathit{...}` — italic (default for letters, but useful to force it). */
    ITALIC,

    /** `\mathrm{...}` — upright roman. */
    ROMAN,

    /** `\mathbb{...}` — double-struck (via Unicode mapping where available). */
    DOUBLE_STRUCK,

    /** `\mathcal{...}` — calligraphic (best-effort Unicode mapping). */
    CALLIGRAPHIC,
}

/** Horizontal spacing: \, \: \; \! \quad \qquad, measured in em. */
@Immutable
data class Space(val emWidth: Float) : MathAtom
