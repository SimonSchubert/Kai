package com.inspiredandroid.kai.ui.markdown.math

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Tests for [MathParser] — LaTeX fragments → [MathAtom] tree. */
class MathAtomParserTest {

    private fun parse(s: String) = MathParser.parse(s)

    private fun group(atom: MathAtom): List<MathAtom> = when (atom) {
        is Group -> atom.atoms
        else -> listOf(atom)
    }

    @Test
    fun `empty input gives empty group`() {
        val result = parse("")
        assertEquals(Group(emptyList()), result)
    }

    @Test
    fun `single variable letter is a VARIABLE sym`() {
        val result = parse("x")
        assertEquals(Sym("x", SymKind.VARIABLE), result)
    }

    @Test
    fun `digits collapse into a single ORDINARY sym`() {
        val atoms = group(parse("123"))
        assertEquals(1, atoms.size)
        assertEquals(Sym("123", SymKind.ORDINARY), atoms[0])
    }

    @Test
    fun `greek letter command resolves to Unicode VARIABLE sym`() {
        assertEquals(Sym("α", SymKind.VARIABLE), parse("\\alpha"))
        assertEquals(Sym("π", SymKind.VARIABLE), parse("\\pi"))
        assertEquals(Sym("Σ", SymKind.ORDINARY), parse("\\Sigma"))
    }

    @Test
    fun `unknown command renders as literal backslash-name`() {
        val atom = parse("\\wibble")
        assertEquals(Sym("\\wibble", SymKind.ORDINARY), atom)
    }

    @Test
    fun `superscript single char`() {
        val atom = parse("x^2")
        assertTrue(atom is Script)
        assertEquals(Sym("x", SymKind.VARIABLE), atom.base)
        assertEquals(Sym("2", SymKind.ORDINARY), atom.sup)
        assertEquals(null, atom.sub)
    }

    @Test
    fun `subscript single char`() {
        val atom = parse("x_i")
        assertTrue(atom is Script)
        assertEquals(Sym("i", SymKind.VARIABLE), atom.sub)
    }

    @Test
    fun `sub and sup attached to same base`() {
        val atom = parse("x_i^2")
        assertTrue(atom is Script)
        assertEquals(Sym("i", SymKind.VARIABLE), atom.sub)
        assertEquals(Sym("2", SymKind.ORDINARY), atom.sup)
    }

    @Test
    fun `superscript with braces groups the whole expression`() {
        val atom = parse("e^{i\\pi}")
        assertTrue(atom is Script)
        val sup = atom.sup as Group
        assertEquals(2, sup.atoms.size)
        assertEquals(Sym("i", SymKind.VARIABLE), sup.atoms[0])
        assertEquals(Sym("π", SymKind.VARIABLE), sup.atoms[1])
    }

    @Test
    fun `fraction parses numerator and denominator`() {
        val atom = parse("\\frac{a}{b}")
        assertTrue(atom is Frac)
        assertEquals(Sym("a", SymKind.VARIABLE), atom.num)
        assertEquals(Sym("b", SymKind.VARIABLE), atom.den)
    }

    @Test
    fun `nested fractions`() {
        val atom = parse("\\frac{\\frac{1}{2}}{3}")
        assertTrue(atom is Frac)
        assertTrue(atom.num is Frac)
    }

    @Test
    fun `sqrt with and without index`() {
        val plain = parse("\\sqrt{x}")
        assertTrue(plain is Radical)
        assertEquals(null, plain.index)
        assertEquals(Sym("x", SymKind.VARIABLE), plain.radicand)

        val withIndex = parse("\\sqrt[3]{x}")
        assertTrue(withIndex is Radical)
        assertEquals(Sym("3", SymKind.ORDINARY), withIndex.index)
    }

    @Test
    fun `sum with subscript and superscript produces LargeOp with limits`() {
        val atom = parse("\\sum_{i=0}^{n} i")
        val first = (atom as Group).atoms[0]
        assertTrue(first is LargeOp)
        assertEquals("∑", first.symbol)
        assertNotNull(first.sub)
        assertNotNull(first.sup)
    }

    @Test
    fun `integral with limits`() {
        val atom = parse("\\int_a^b f(x) dx")
        val atoms = (atom as Group).atoms
        val op = atoms[0]
        assertTrue(op is LargeOp)
        assertEquals("∫", op.symbol)
        assertEquals(Sym("a", SymKind.VARIABLE), op.sub)
        assertEquals(Sym("b", SymKind.VARIABLE), op.sup)
    }

    @Test
    fun `left and right parens`() {
        val atom = parse("\\left( x + 1 \\right)")
        assertTrue(atom is Delim)
        assertEquals("(", atom.left)
        assertEquals(")", atom.right)
    }

    @Test
    fun `left and right with dot is empty delim`() {
        val atom = parse("\\left. x \\right|")
        assertTrue(atom is Delim)
        assertEquals("", atom.left)
        assertEquals("|", atom.right)
    }

    @Test
    fun `mathbb maps uppercase letters to double-struck via Styled`() {
        val atom = parse("\\mathbb{R}")
        assertTrue(atom is Styled)
        assertEquals(MathStyle.DOUBLE_STRUCK, atom.style)
    }

    @Test
    fun `text preserves spaces`() {
        val atom = parse("\\text{hello world}")
        assertTrue(atom is Styled)
        assertEquals(MathStyle.TEXT, atom.style)
        val inner = atom.atoms.single() as Sym
        assertEquals("hello world", inner.text)
    }

    @Test
    fun `function name sin is a FUNCTION sym`() {
        val atom = parse("\\sin")
        assertEquals(Sym("sin", SymKind.FUNCTION), atom)
    }

    @Test
    fun `lim is a LargeOp with subscript`() {
        val atom = parse("\\lim_{x \\to 0} f(x)")
        val lim = (atom as Group).atoms[0]
        assertTrue(lim is LargeOp)
        assertEquals("lim", lim.symbol)
        assertNotNull(lim.sub)
    }

    @Test
    fun `arrow relation`() {
        val atom = parse("a \\to b")
        val atoms = (atom as Group).atoms
        assertEquals(Sym("⟶", SymKind.REL_OP), atoms[1])
    }

    @Test
    fun `quadratic formula full parse does not throw`() {
        val atom = parse("x = \\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}")
        // Structure: x, =, Frac
        val atoms = (atom as Group).atoms
        assertEquals(Sym("x", SymKind.VARIABLE), atoms[0])
        assertEquals(Sym("=", SymKind.REL_OP), atoms[1])
        assertTrue(atoms[2] is Frac)
    }

    @Test
    fun `issue 150 integral reproducer parses without error`() {
        // From the GitHub issue's example.
        val atom = parse("\\int_a^b x^2 \\arctan(x)\\,dx")
        val atoms = (atom as Group).atoms
        assertTrue(atoms[0] is LargeOp)
    }

    @Test
    fun `unmatched opening brace does not throw`() {
        val atom = parse("\\frac{a")
        // Should degrade without crashing.
        assertTrue(atom is Frac)
    }

    @Test
    fun `primes collapse into single sym`() {
        val atom = parse("f''")
        assertTrue(atom is Script)
        val sup = atom.sup as Sym
        assertEquals("′′", sup.text)
    }

    // ── New-construct coverage ──────────────────────────────────────────────────────────────

    @Test
    fun `implies and iff map to long arrows`() {
        assertEquals(Sym("⟹", SymKind.REL_OP), parse("\\implies"))
        assertEquals(Sym("⟺", SymKind.REL_OP), parse("\\iff"))
    }

    @Test
    fun `boldsymbol wraps in BOLD_ITALIC style`() {
        val atom = parse("\\boldsymbol{\\alpha}")
        assertTrue(atom is Styled)
        assertEquals(MathStyle.BOLD_ITALIC, atom.style)
    }

    @Test
    fun `pmod expands to parenthesized mod group`() {
        val atom = parse("\\pmod{n}") as Group
        val op = atom.atoms.filterIsInstance<Sym>().firstOrNull { it.text == "mod" }
        assertNotNull(op)
        assertEquals(SymKind.FUNCTION, op.kind)
    }

    @Test
    fun `bmod renders as bare mod function`() {
        assertEquals(Sym("mod", SymKind.FUNCTION), parse("\\bmod"))
    }

    @Test
    fun `unclosed accent brace does not crash`() {
        val atom = parse("\\hat{x")
        assertTrue(atom is Accent)
        assertEquals(AccentKind.HAT, atom.kind)
    }

    @Test
    fun `hat accent produces Accent HAT`() {
        val atom = parse("\\hat{x}")
        assertTrue(atom is Accent)
        assertEquals(AccentKind.HAT, atom.kind)
        assertEquals(Sym("x", SymKind.VARIABLE), atom.base)
    }

    @Test
    fun `vec accent with multi-char base`() {
        val atom = parse("\\vec{v}")
        assertTrue(atom is Accent)
        assertEquals(AccentKind.VEC, atom.kind)
    }

    @Test
    fun `overline widens across content`() {
        val atom = parse("\\overline{AB}")
        assertTrue(atom is Accent)
        assertEquals(AccentKind.OVERLINE, atom.kind)
    }

    @Test
    fun `binom parses as parenthesized borderless frac`() {
        val atom = parse("\\binom{n}{k}")
        assertTrue(atom is Delim)
        assertEquals("(", atom.left)
        assertEquals(")", atom.right)
        val frac = atom.content as Frac
        assertTrue(!frac.drawBar)
        assertEquals(Sym("n", SymKind.VARIABLE), frac.num)
        assertEquals(Sym("k", SymKind.VARIABLE), frac.den)
    }

    @Test
    fun `pmatrix 2x2 parses rows and cells`() {
        val atom = parse("\\begin{pmatrix} a & b \\\\ c & d \\end{pmatrix}")
        assertTrue(atom is Matrix)
        assertEquals(MatrixDelim.PAREN, atom.delim)
        assertEquals(2, atom.rows.size)
        assertEquals(2, atom.rows[0].size)
        assertEquals(Sym("a", SymKind.VARIABLE), atom.rows[0][0])
        assertEquals(Sym("d", SymKind.VARIABLE), atom.rows[1][1])
    }

    @Test
    fun `bmatrix and vmatrix pick correct delimiters`() {
        val b = parse("\\begin{bmatrix} 1 \\end{bmatrix}") as Matrix
        assertEquals(MatrixDelim.BRACKET, b.delim)
        val v = parse("\\begin{vmatrix} 1 \\end{vmatrix}") as Matrix
        assertEquals(MatrixDelim.VBAR, v.delim)
    }

    @Test
    fun `cases environment produces left-aligned matrix with left brace`() {
        val atom = parse("\\begin{cases} x & x > 0 \\\\ 0 & \\text{else} \\end{cases}")
        assertTrue(atom is Matrix)
        assertEquals(MatrixDelim.CASES, atom.delim)
        assertEquals(MatrixAlign.LEFT, atom.alignMode)
    }

    @Test
    fun `aligned uses ALIGN_RL mode and no delimiters`() {
        val atom = parse("\\begin{aligned} x &= 1 \\\\ y &= 2 \\end{aligned}")
        assertTrue(atom is Matrix)
        assertEquals(MatrixDelim.NONE, atom.delim)
        assertEquals(MatrixAlign.ALIGN_RL, atom.alignMode)
        assertEquals(2, atom.rows.size)
    }

    @Test
    fun `unknown environment swallows body without crashing`() {
        val atom = parse("\\begin{unknown} x + y \\end{unknown}")
        assertTrue(atom is Group)
        assertTrue(atom.atoms.isEmpty())
    }

    @Test
    fun `unclosed matrix still returns Matrix with collected rows`() {
        val atom = parse("\\begin{pmatrix} a & b")
        assertTrue(atom is Matrix)
        assertEquals(1, atom.rows.size)
        assertEquals(2, atom.rows[0].size)
    }
}
