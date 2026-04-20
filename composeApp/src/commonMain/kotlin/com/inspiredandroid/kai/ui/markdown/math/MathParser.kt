package com.inspiredandroid.kai.ui.markdown.math

/**
 * Parses a LaTeX math fragment into a [MathAtom] tree. Robust to malformed input:
 * - Unknown commands render as literal `\name` text.
 * - Unmatched braces close at end-of-input.
 * - Unmatched `\left`/`\right` close with a blank delimiter.
 *
 * The grammar covered, roughly:
 *   expr      := atom* (handled by parseSequence)
 *   atom      := '{' expr '}'
 *              | '\frac' group group
 *              | '\sqrt' ('[' expr ']')? group
 *              | '\left' delim expr '\right' delim
 *              | '\mathbb' group | '\mathbf' group | ...
 *              | '\<name>' (looked up via [MathSymbols])
 *              | single-char
 *   script-suffix := ('_' atom | '^' atom)+   (attached to preceding atom)
 */
internal object MathParser {

    private data class EnvSpec(val delim: MatrixDelim, val align: MatrixAlign)

    private val MATRIX_ENVIRONMENTS = mapOf(
        "matrix" to EnvSpec(MatrixDelim.NONE, MatrixAlign.CENTERED),
        "pmatrix" to EnvSpec(MatrixDelim.PAREN, MatrixAlign.CENTERED),
        "bmatrix" to EnvSpec(MatrixDelim.BRACKET, MatrixAlign.CENTERED),
        "Bmatrix" to EnvSpec(MatrixDelim.BRACE, MatrixAlign.CENTERED),
        "vmatrix" to EnvSpec(MatrixDelim.VBAR, MatrixAlign.CENTERED),
        "Vmatrix" to EnvSpec(MatrixDelim.DBLVBAR, MatrixAlign.CENTERED),
        "cases" to EnvSpec(MatrixDelim.CASES, MatrixAlign.LEFT),
        "aligned" to EnvSpec(MatrixDelim.NONE, MatrixAlign.ALIGN_RL),
        "align" to EnvSpec(MatrixDelim.NONE, MatrixAlign.ALIGN_RL),
        "aligned*" to EnvSpec(MatrixDelim.NONE, MatrixAlign.ALIGN_RL),
        "align*" to EnvSpec(MatrixDelim.NONE, MatrixAlign.ALIGN_RL),
    )

    fun parse(latex: String): MathAtom {
        if (latex.isBlank()) return Group(emptyList())
        return try {
            val parser = ParserState(latex)
            val atoms = parser.parseSequence(stopAtBrace = false)
            if (atoms.size == 1) atoms[0] else Group(atoms)
        } catch (_: Throwable) {
            // Absolute-last-resort fallback: raw text. Should be rare.
            Sym(latex, SymKind.ORDINARY)
        }
    }

    private class ParserState(val src: String) {
        var i = 0
        val len = src.length

        /**
         * Parse a flat sequence of atoms until we hit:
         *  - EOF
         *  - `}` if [stopAtBrace]
         *  - `\right` (always — the caller handles whether that was expected)
         */
        fun parseSequence(stopAtBrace: Boolean): List<MathAtom> {
            val result = mutableListOf<MathAtom>()
            while (i < len) {
                skipWhitespaceAndComments()
                if (i >= len) break
                val c = src[i]
                if (stopAtBrace && c == '}') break
                if (c == '\\' && peekCommandName() == "right") break
                if (c == '&') {
                    i++
                    continue
                }
                if (c == '\\' && peekCommandName() == "\\") {
                    i += 2
                    continue
                }

                val atom = parseAtom() ?: continue
                val withScripts = attachScripts(atom)
                result += withScripts
            }
            return result
        }

        private fun attachScripts(initial: MathAtom): MathAtom {
            var base = initial
            while (i < len) {
                skipWhitespace()
                if (i >= len) break
                val c = src[i]
                if (c != '_' && c != '^' && c != '\'') break

                var sub: MathAtom? = null
                var sup: MathAtom? = null

                while (i < len) {
                    val ch = src[i]
                    when (ch) {
                        '\'' -> {
                            val start = i
                            while (i < len && src[i] == '\'') i++
                            val primes = "′".repeat(i - start)
                            val primeAtom = Sym(primes, SymKind.ORDINARY)
                            sup = if (sup == null) primeAtom else Group(listOf(sup, primeAtom))
                        }

                        '_', '^' -> {
                            val isSup = ch == '^'
                            i++
                            skipWhitespace()
                            val arg = parseAtom() ?: Group(emptyList())
                            if (isSup) {
                                sup = if (sup == null) arg else Group(listOf(sup, arg))
                            } else {
                                sub = if (sub == null) arg else Group(listOf(sub, arg))
                            }
                        }

                        else -> break
                    }
                    skipWhitespace()
                }

                base = when (base) {
                    is LargeOp -> base.copy(
                        sub = mergeOptional(base.sub, sub),
                        sup = mergeOptional(base.sup, sup),
                    )

                    else -> Script(base, sub, sup)
                }
                break // one script group per base atom is enough; the outer loop has already done its job
            }
            return base
        }

        private fun mergeOptional(a: MathAtom?, b: MathAtom?): MathAtom? = when {
            a == null -> b
            b == null -> a
            else -> Group(listOf(a, b))
        }

        private fun parseAtom(): MathAtom? {
            skipWhitespaceAndComments()
            if (i >= len) return null
            val c = src[i]
            return when {
                c == '{' -> {
                    i++
                    val inner = parseSequence(stopAtBrace = true)
                    if (i < len && src[i] == '}') i++
                    Group(inner)
                }

                c == '}' -> null

                c == '\\' -> parseCommand()

                c.isDigit() || c == '.' || c == ',' -> {
                    // Emit digits/numbers as a single ORDINARY atom so that "123" stays tight.
                    val start = i
                    while (i < len && (src[i].isDigit() || src[i] == '.' || src[i] == ',')) i++
                    Sym(src.substring(start, i), SymKind.ORDINARY)
                }

                c.isLetter() -> {
                    i++
                    Sym(c.toString(), SymKind.VARIABLE)
                }

                c == '+' || c == '-' || c == '*' || c == '/' -> {
                    i++
                    Sym(if (c == '*') "∗" else c.toString(), SymKind.BIN_OP)
                }

                c == '=' || c == '<' || c == '>' -> {
                    i++
                    Sym(c.toString(), SymKind.REL_OP)
                }

                c == '(' || c == '[' -> {
                    i++
                    Sym(c.toString(), SymKind.OPEN)
                }

                c == ')' || c == ']' -> {
                    i++
                    Sym(c.toString(), SymKind.CLOSE)
                }

                c == '|' -> {
                    i++
                    Sym("|", SymKind.ORDINARY)
                }

                c == ';' || c == ':' -> {
                    i++
                    Sym(c.toString(), SymKind.PUNCT)
                }

                c == '\'' -> {
                    // Prime: treat the whole run `'''` as a single superscript glyph.
                    val start = i
                    while (i < len && src[i] == '\'') i++
                    val primes = "′".repeat(i - start)
                    Sym(primes, SymKind.ORDINARY)
                }

                else -> {
                    i++
                    Sym(c.toString(), SymKind.ORDINARY)
                }
            }
        }

        private fun parseCommand(): MathAtom {
            // Entered pointing at '\'. Handle single-char escapes and environment markers first.
            val escapeStart = i
            i++
            if (i >= len) return Sym("\\", SymKind.ORDINARY)

            val first = src[i]
            // Single non-letter escape like `\{`, `\$`, `\,`, `\;`, `\!`, `\|`, `\\`
            if (!first.isLetter()) {
                i++
                val name = first.toString()
                return MathSymbols.lookup(name) ?: Sym("\\$name", SymKind.ORDINARY)
            }

            val nameStart = i
            while (i < len && src[i].isLetter()) i++
            val name = src.substring(nameStart, i)

            return when (name) {
                "frac", "dfrac", "tfrac" -> {
                    val num = parseRequiredGroup()
                    val den = parseRequiredGroup()
                    Frac(num, den)
                }

                "binom", "dbinom", "tbinom" -> {
                    val top = parseRequiredGroup()
                    val bottom = parseRequiredGroup()
                    Delim("(", ")", Frac(top, bottom, drawBar = false))
                }

                "sqrt" -> {
                    skipWhitespace()
                    val index = if (i < len && src[i] == '[') {
                        i++
                        val indexStart = i
                        var depth = 0
                        while (i < len && !(depth == 0 && src[i] == ']')) {
                            if (src[i] == '{') {
                                depth++
                            } else if (src[i] == '}') {
                                depth--
                            }
                            i++
                        }
                        val indexLatex = src.substring(indexStart, i)
                        if (i < len && src[i] == ']') i++
                        parse(indexLatex)
                    } else {
                        null
                    }
                    skipWhitespace()
                    val radicand = parseRequiredGroup()
                    Radical(index, radicand)
                }

                "left" -> {
                    skipWhitespace()
                    val leftDelim = readDelimiter()
                    val inner = parseSequence(stopAtBrace = false)
                    // parseSequence stops at `\right` without consuming it; consume it now.
                    val rightDelim = if (i < len && src[i] == '\\' && peekCommandName() == "right") {
                        i += "\\right".length
                        skipWhitespace()
                        readDelimiter()
                    } else {
                        ""
                    }
                    Delim(leftDelim, rightDelim, Group(inner))
                }

                "right" -> {
                    // Orphan \right (outside a \left context) — emit a bare delim.
                    skipWhitespace()
                    val d = readDelimiter()
                    Sym(d, SymKind.CLOSE)
                }

                "mathbb" -> Styled(MathStyle.DOUBLE_STRUCK, groupAsList(parseRequiredGroup()))

                "mathbf" -> Styled(MathStyle.BOLD, groupAsList(parseRequiredGroup()))

                "boldsymbol", "bm" -> Styled(MathStyle.BOLD_ITALIC, groupAsList(parseRequiredGroup()))

                "mathit" -> Styled(MathStyle.ITALIC, groupAsList(parseRequiredGroup()))

                "mathrm", "operatorname" -> Styled(MathStyle.ROMAN, groupAsList(parseRequiredGroup()))

                "mathcal" -> Styled(MathStyle.CALLIGRAPHIC, groupAsList(parseRequiredGroup()))

                "text", "textrm", "textbf", "textit" -> {
                    val content = readVerbatimGroup()
                    Styled(MathStyle.TEXT, listOf(Sym(content, SymKind.ORDINARY)))
                }

                "pmod" -> {
                    // \pmod{n} renders as " (mod n)".
                    val arg = parseRequiredGroup()
                    Group(
                        listOf(
                            Space(0.5f),
                            Sym("(", SymKind.OPEN),
                            Sym("mod", SymKind.FUNCTION),
                            Space(0.3f),
                            arg,
                            Sym(")", SymKind.CLOSE),
                        ),
                    )
                }

                "bmod" -> Sym("mod", SymKind.FUNCTION)

                "hat" -> Accent(parseRequiredGroup(), AccentKind.HAT)

                "bar" -> Accent(parseRequiredGroup(), AccentKind.BAR)

                "vec" -> Accent(parseRequiredGroup(), AccentKind.VEC)

                "tilde" -> Accent(parseRequiredGroup(), AccentKind.TILDE)

                "dot" -> Accent(parseRequiredGroup(), AccentKind.DOT)

                "ddot" -> Accent(parseRequiredGroup(), AccentKind.DDOT)

                "overline" -> Accent(parseRequiredGroup(), AccentKind.OVERLINE)

                "widehat" -> Accent(parseRequiredGroup(), AccentKind.WIDEHAT)

                "widetilde" -> Accent(parseRequiredGroup(), AccentKind.WIDETILDE)

                "begin" -> {
                    val envName = readVerbatimGroup()
                    parseEnvironment(envName)
                }

                "end" -> {
                    // Orphan `\end{…}` — eat the arg, emit nothing.
                    readVerbatimGroup()
                    Group(emptyList())
                }

                else -> MathSymbols.lookup(name)
                    ?: Sym(src.substring(escapeStart, i), SymKind.ORDINARY)
            }
        }

        private fun parseRequiredGroup(): MathAtom {
            skipWhitespace()
            if (i >= len) return Group(emptyList())
            if (src[i] == '{') {
                i++
                val inner = parseSequence(stopAtBrace = true)
                if (i < len && src[i] == '}') i++
                return if (inner.size == 1) inner[0] else Group(inner)
            }
            // Single token (e.g. `\frac 1 2`)
            return parseAtom() ?: Group(emptyList())
        }

        private fun parseEnvironment(envName: String): MathAtom {
            val spec = MATRIX_ENVIRONMENTS[envName] ?: run {
                // Unknown environment — swallow body up to the matching \end{envName}.
                skipToEnd(envName)
                return Group(emptyList())
            }
            return parseMatrixBody(envName, spec.delim, spec.align)
        }

        private fun parseMatrixBody(envName: String, delim: MatrixDelim, align: MatrixAlign): Matrix {
            val rows = mutableListOf<List<MathAtom>>()
            var row = mutableListOf<MathAtom>()
            var cell = mutableListOf<MathAtom>()
            fun finishCell() {
                row += if (cell.size == 1) cell[0] else Group(cell.toList())
                cell = mutableListOf()
            }
            fun finishRow() {
                finishCell()
                rows += row.toList()
                row = mutableListOf()
            }
            while (i < len) {
                skipWhitespaceAndComments()
                if (i >= len) break
                val c = src[i]
                if (c == '\\' && peekCommandName() == "end") {
                    i += "\\end".length
                    readVerbatimGroup() // consume {envname}; tolerate mismatched names
                    break
                }
                if (c == '\\' && peekCommandName() == "\\") {
                    i += 2
                    finishRow()
                    continue
                }
                if (c == '&') {
                    i++
                    finishCell()
                    continue
                }
                val atom = parseAtom() ?: continue
                cell += attachScripts(atom)
            }
            if (cell.isNotEmpty() || row.isNotEmpty()) finishRow()
            // Drop a trailing blank row produced by `\\ ` right before `\end{…}`.
            if (rows.isNotEmpty() && rows.last().all { it is Group && it.atoms.isEmpty() }) {
                rows.removeAt(rows.lastIndex)
            }
            return Matrix(rows, delim, align)
        }

        private fun skipToEnd(envName: String) {
            while (i < len) {
                if (src[i] == '\\' && peekCommandName() == "end") {
                    i += "\\end".length
                    val ended = readVerbatimGroup()
                    if (ended == envName) return
                } else {
                    i++
                }
            }
        }

        private fun readVerbatimGroup(): String {
            skipWhitespace()
            if (i >= len || src[i] != '{') return ""
            i++
            val start = i
            var depth = 1
            while (i < len && depth > 0) {
                val c = src[i]
                if (c == '{') {
                    depth++
                } else if (c == '}') {
                    depth--
                    if (depth == 0) break
                }
                i++
            }
            val end = i
            if (i < len && src[i] == '}') i++
            return src.substring(start, end)
        }

        private fun readDelimiter(): String {
            if (i >= len) return ""
            val c = src[i]
            if (c == '\\') {
                i++
                if (i >= len) return "\\"
                val first = src[i]
                if (!first.isLetter()) {
                    i++
                    return when (first) {
                        '|' -> "‖"
                        '{' -> "{"
                        '}' -> "}"
                        else -> first.toString()
                    }
                }
                val nameStart = i
                while (i < len && src[i].isLetter()) i++
                val name = src.substring(nameStart, i)
                return when (name) {
                    "lbrace" -> "{"
                    "rbrace" -> "}"
                    "langle" -> "⟨"
                    "rangle" -> "⟩"
                    "lceil" -> "⌈"
                    "rceil" -> "⌉"
                    "lfloor" -> "⌊"
                    "rfloor" -> "⌋"
                    "vert" -> "|"
                    "Vert" -> "‖"
                    else -> ""
                }
            }
            i++
            return if (c == '.') "" else c.toString()
        }

        private fun groupAsList(atom: MathAtom): List<MathAtom> = when (atom) {
            is Group -> atom.atoms
            else -> listOf(atom)
        }

        private fun peekCommandName(): String {
            if (i >= len || src[i] != '\\') return ""
            if (i + 1 >= len) return "\\"
            val c = src[i + 1]
            if (!c.isLetter()) return c.toString()
            var j = i + 1
            while (j < len && src[j].isLetter()) j++
            return src.substring(i + 1, j)
        }

        private fun skipWhitespace() {
            while (i < len && (src[i] == ' ' || src[i] == '\t' || src[i] == '\n' || src[i] == '\r')) {
                i++
            }
        }

        private fun skipWhitespaceAndComments() {
            while (i < len) {
                val c = src[i]
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    i++
                } else if (c == '%') {
                    // LaTeX comment to end of line.
                    while (i < len && src[i] != '\n') i++
                } else {
                    break
                }
            }
        }
    }
}
