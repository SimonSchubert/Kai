package com.inspiredandroid.kai.ui.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Markdown-level integration: how `$`/`$$`/`\(…\)`/`\[…\]` become [InlineMath]/[DisplayMath]. */
class MathParsingTest {

    private fun inlines(text: String): List<InlineNode> {
        val para = parseMarkdown(text).blocks.single() as Paragraph
        return para.inlines
    }

    @Test
    fun `inline dollar math`() {
        val result = inlines("result is \$x^2\$ yes")
        assertEquals(3, result.size)
        assertEquals(Text("result is "), result[0])
        assertEquals(InlineMath("x^2"), result[1])
        assertEquals(Text(" yes"), result[2])
    }

    @Test
    fun `inline backslash paren math`() {
        val result = inlines("see \\(a + b\\) here")
        assertEquals(InlineMath("a + b"), result[1])
    }

    @Test
    fun `dollar followed by digit is not math`() {
        // `$5 and` does not have a closing `$` before another context; with closing it would.
        // Here we ensure the no-space-after-opener KaTeX rule keeps "$5 and $3" out of math.
        val result = inlines("I have \$5 and \$3 left")
        assertTrue(result.none { it is InlineMath }, "got: $result")
    }

    @Test
    fun `dollar with space after opener is not math`() {
        val result = inlines("cost \$ 5 is \$ here")
        assertTrue(result.none { it is InlineMath })
    }

    @Test
    fun `double dollar inline treated as math`() {
        val result = inlines("math \$\$x+1\$\$ ok")
        assertEquals(InlineMath("x+1"), result[1])
    }

    @Test
    fun `display math block with double dollar on own lines`() {
        val doc = parseMarkdown("Before\n\n\$\$\nx = \\frac{a}{b}\n\$\$\n\nAfter")
        val blocks = doc.blocks
        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is Paragraph)
        assertEquals(DisplayMath("x = \\frac{a}{b}"), blocks[1])
        assertTrue(blocks[2] is Paragraph)
    }

    @Test
    fun `display math single line block`() {
        val doc = parseMarkdown("\$\$E = mc^2\$\$")
        assertEquals(DisplayMath("E = mc^2"), doc.blocks.single())
    }

    @Test
    fun `display math bracket form`() {
        val doc = parseMarkdown("\\[x = 1\\]")
        assertEquals(DisplayMath("x = 1"), doc.blocks.single())
    }

    @Test
    fun `display math bracket form multi-line`() {
        val doc = parseMarkdown("\\[\nx = 1\n\\]")
        assertEquals(DisplayMath("x = 1"), doc.blocks.single())
    }

    @Test
    fun `math inside emphasis does not break`() {
        // `$x$` wins as atomic before the `*...*` emphasis scanner runs.
        val result = inlines("*italic \$y\$*")
        val emphasis = result.single() as Emphasis
        assertTrue(emphasis.children.any { it is InlineMath })
    }

    @Test
    fun `escaped dollar is literal`() {
        val result = inlines("price: \\\$5")
        assertTrue(result.none { it is InlineMath })
    }

    @Test
    fun `math with nested braces and escapes`() {
        val result = inlines("\$\\frac{1}{2}\$")
        assertEquals(InlineMath("\\frac{1}{2}"), result.single())
    }

    @Test
    fun `streaming unclosed display math yields empty-ish block`() {
        // Unclosed `$$` should not crash and should not swallow the rest of the document.
        val doc = parseMarkdown("\$\$\nx = 1")
        assertTrue(doc.blocks.any { it is DisplayMath })
    }
}
