package com.inspiredandroid.kai.ui.markdown.math

/**
 * LaTeX command → Unicode mapping for the symbols that LLMs actually emit.
 * Kept deliberately small; unknown commands fall back to rendering the raw `\name` string.
 */
internal object MathSymbols {

    private val GREEK_LOWER = mapOf(
        "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ",
        "epsilon" to "ϵ", "varepsilon" to "ε", "zeta" to "ζ", "eta" to "η",
        "theta" to "θ", "vartheta" to "ϑ", "iota" to "ι", "kappa" to "κ",
        "lambda" to "λ", "mu" to "μ", "nu" to "ν", "xi" to "ξ",
        "pi" to "π", "varpi" to "ϖ", "rho" to "ρ", "varrho" to "ϱ",
        "sigma" to "σ", "varsigma" to "ς", "tau" to "τ", "upsilon" to "υ",
        "phi" to "ϕ", "varphi" to "φ", "chi" to "χ", "psi" to "ψ",
        "omega" to "ω",
    )

    private val GREEK_UPPER = mapOf(
        "Gamma" to "Γ", "Delta" to "Δ", "Theta" to "Θ", "Lambda" to "Λ",
        "Xi" to "Ξ", "Pi" to "Π", "Sigma" to "Σ", "Upsilon" to "Υ",
        "Phi" to "Φ", "Psi" to "Ψ", "Omega" to "Ω",
    )

    private val BINARY_OPS = mapOf(
        "cdot" to "⋅", "cdots" to "⋯", "ldots" to "…", "dots" to "…", "vdots" to "⋮", "ddots" to "⋱",
        "times" to "×", "div" to "÷", "pm" to "±", "mp" to "∓",
        "ast" to "∗", "star" to "⋆", "circ" to "∘", "bullet" to "•",
        "oplus" to "⊕", "ominus" to "⊖", "otimes" to "⊗", "oslash" to "⊘", "odot" to "⊙",
        "cap" to "∩", "cup" to "∪", "wedge" to "∧", "vee" to "∨",
        "setminus" to "∖",
    )

    private val RELATION_OPS = mapOf(
        "leq" to "≤", "le" to "≤", "geq" to "≥", "ge" to "≥",
        "neq" to "≠", "ne" to "≠",
        "approx" to "≈", "equiv" to "≡", "sim" to "∼", "simeq" to "≃", "cong" to "≅",
        "propto" to "∝",
        "ll" to "≪", "gg" to "≫",
        "subset" to "⊂", "supset" to "⊃", "subseteq" to "⊆", "supseteq" to "⊇",
        "in" to "∈", "notin" to "∉", "ni" to "∋",
        // Use the long-arrow variants (U+27F6 / U+27F5) instead of the short ones (U+2192 /
        // U+2190). The short arrowhead collapses to a single pixel at subscript sizes and reads
        // as a dash; the long variants keep the arrow shape identifiable at any size.
        "to" to "⟶", "rightarrow" to "⟶", "leftarrow" to "⟵", "gets" to "⟵",
        "Rightarrow" to "⟹", "Leftarrow" to "⟸", "Leftrightarrow" to "⟺",
        "mapsto" to "⟼", "leftrightarrow" to "⟷",
        "implies" to "⟹", "iff" to "⟺",
    )

    private val MISC_SYMBOLS = mapOf(
        "infty" to "∞", "partial" to "∂", "nabla" to "∇",
        "forall" to "∀", "exists" to "∃", "nexists" to "∄",
        "emptyset" to "∅", "varnothing" to "∅",
        "hbar" to "ℏ", "ell" to "ℓ", "Re" to "ℜ", "Im" to "ℑ", "wp" to "℘",
        "aleph" to "ℵ", "beth" to "ℶ",
        "neg" to "¬", "lnot" to "¬",
        "angle" to "∠", "triangle" to "△", "square" to "□",
        "top" to "⊤", "bot" to "⊥", "perp" to "⊥", "parallel" to "∥",
        "degree" to "°",
        "prime" to "′", "dagger" to "†", "ddagger" to "‡",
        "checkmark" to "✓",
        "copyright" to "©",
        "backslash" to "\\",
    )

    private val LARGE_OPS = mapOf(
        "sum" to "∑", "prod" to "∏", "coprod" to "∐",
        "int" to "∫", "iint" to "∬", "iiint" to "∭", "oint" to "∮",
        "bigcup" to "⋃", "bigcap" to "⋂", "bigvee" to "⋁", "bigwedge" to "⋀",
        "bigoplus" to "⨁", "bigotimes" to "⨂", "bigodot" to "⨀",
    )

    /** Commands that typeset as upright function names: `\sin`, `\cos`, etc. */
    private val FUNCTION_NAMES = setOf(
        "sin", "cos", "tan", "cot", "sec", "csc",
        "sinh", "cosh", "tanh", "coth",
        "arcsin", "arccos", "arctan",
        "log", "ln", "lg", "exp",
        "min", "max", "inf", "sup", "det", "dim", "ker", "deg",
        "gcd", "lcm", "mod", "Pr",
        "arg", "hom",
    )

    /** Function names that also always typeset their subscript as a limit (below in display). */
    private val LIMIT_FUNCTIONS = setOf("lim", "liminf", "limsup", "max", "min", "sup", "inf")

    private val SPACE_COMMANDS = mapOf(
        "," to 3f / 18f,
        ":" to 4f / 18f,
        ";" to 5f / 18f,
        "!" to -3f / 18f,
        " " to 6f / 18f,
        "quad" to 1f,
        "qquad" to 2f,
    )

    /** Commands whose output is literally one character (e.g. `\{` → `{`). */
    private val LITERAL_ESCAPES = mapOf(
        "{" to "{",
        "}" to "}",
        "$" to "$",
        "%" to "%",
        "&" to "&",
        "#" to "#",
        "_" to "_",
    )

    private val DOUBLE_STRUCK_UPPER = mapOf(
        'A' to "𝔸", 'B' to "𝔹", 'C' to "ℂ", 'D' to "𝔻", 'E' to "𝔼", 'F' to "𝔽",
        'G' to "𝔾", 'H' to "ℍ", 'I' to "𝕀", 'J' to "𝕁", 'K' to "𝕂", 'L' to "𝕃",
        'M' to "𝕄", 'N' to "ℕ", 'O' to "𝕆", 'P' to "ℙ", 'Q' to "ℚ", 'R' to "ℝ",
        'S' to "𝕊", 'T' to "𝕋", 'U' to "𝕌", 'V' to "𝕍", 'W' to "𝕎", 'X' to "𝕏",
        'Y' to "𝕐", 'Z' to "ℤ",
    )

    private val CALLIGRAPHIC_UPPER = mapOf(
        'A' to "𝒜", 'B' to "ℬ", 'C' to "𝒞", 'D' to "𝒟", 'E' to "ℰ", 'F' to "ℱ",
        'G' to "𝒢", 'H' to "ℋ", 'I' to "ℐ", 'J' to "𝒥", 'K' to "𝒦", 'L' to "ℒ",
        'M' to "ℳ", 'N' to "𝒩", 'O' to "𝒪", 'P' to "𝒫", 'Q' to "𝒬", 'R' to "ℛ",
        'S' to "𝒮", 'T' to "𝒯", 'U' to "𝒰", 'V' to "𝒱", 'W' to "𝒲", 'X' to "𝒳",
        'Y' to "𝒴", 'Z' to "𝒵",
    )

    fun lookup(command: String): MathAtom? {
        GREEK_LOWER[command]?.let { return Sym(it, SymKind.VARIABLE) }
        GREEK_UPPER[command]?.let { return Sym(it, SymKind.ORDINARY) }
        BINARY_OPS[command]?.let { return Sym(it, SymKind.BIN_OP) }
        RELATION_OPS[command]?.let { return Sym(it, SymKind.REL_OP) }
        MISC_SYMBOLS[command]?.let { return Sym(it, SymKind.ORDINARY) }
        LARGE_OPS[command]?.let { return LargeOp(it) }
        if (command in FUNCTION_NAMES) {
            return Sym(command, SymKind.FUNCTION)
        }
        if (command in LIMIT_FUNCTIONS) {
            // Matches LaTeX default: inline renders subscript beside, display renders below.
            return LargeOp(command)
        }
        SPACE_COMMANDS[command]?.let { return Space(it) }
        LITERAL_ESCAPES[command]?.let { return Sym(it, SymKind.ORDINARY) }
        return null
    }

    fun mapDoubleStruck(ch: Char): String = DOUBLE_STRUCK_UPPER[ch] ?: ch.toString()

    fun mapCalligraphic(ch: Char): String = CALLIGRAPHIC_UPPER[ch] ?: ch.toString()
}
