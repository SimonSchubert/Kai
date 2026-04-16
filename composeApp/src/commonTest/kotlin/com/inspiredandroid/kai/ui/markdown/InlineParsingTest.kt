package com.inspiredandroid.kai.ui.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InlineParsingTest {

    private fun inlines(text: String): List<InlineNode> {
        val para = parseMarkdown(text).blocks.single() as Paragraph
        return para.inlines
    }

    @Test
    fun `plain text is a single Text node`() {
        assertEquals(listOf(Text("hello world")), inlines("hello world"))
    }

    @Test
    fun `strong with double asterisks`() {
        assertEquals(listOf(Strong(listOf(Text("bold")))), inlines("**bold**"))
    }

    @Test
    fun `strong with double underscores`() {
        assertEquals(listOf(Strong(listOf(Text("bold")))), inlines("__bold__"))
    }

    @Test
    fun `emphasis with single asterisk`() {
        assertEquals(listOf(Emphasis(listOf(Text("italic")))), inlines("*italic*"))
    }

    @Test
    fun `emphasis with single underscore`() {
        assertEquals(listOf(Emphasis(listOf(Text("italic")))), inlines("_italic_"))
    }

    @Test
    fun `strike with tildes`() {
        assertEquals(listOf(Strike(listOf(Text("gone")))), inlines("~~gone~~"))
    }

    @Test
    fun `inline code`() {
        assertEquals(listOf(InlineCode("x = 1")), inlines("`x = 1`"))
    }

    @Test
    fun `link produces Link with href and children`() {
        val result = inlines("[click here](https://example.com)")
        val link = result.single() as Link
        assertEquals("https://example.com", link.href)
        assertEquals(listOf(Text("click here")), link.children)
    }

    @Test
    fun `image produces Image with src and alt`() {
        val result = inlines("![photo](https://example.com/x.png)")
        val image = result.single() as Image
        assertEquals("https://example.com/x.png", image.src)
        assertEquals("photo", image.alt)
    }

    @Test
    fun `intraword underscores do not form emphasis`() {
        assertEquals(listOf(Text("foo_bar_baz")), inlines("foo_bar_baz"))
    }

    @Test
    fun `intraword asterisks do form emphasis`() {
        // Asterisks are permissive — matches CommonMark behavior.
        val result = inlines("foo*bar*baz")
        assertTrue(result.any { it is Emphasis })
    }

    @Test
    fun `backslash escape of asterisk`() {
        assertEquals(listOf(Text("*literal*")), inlines("\\*literal\\*"))
    }

    @Test
    fun `backslash escape of backtick`() {
        assertEquals(listOf(Text("`not code`")), inlines("\\`not code\\`"))
    }

    @Test
    fun `mixed bold and italic`() {
        val result = inlines("**bold** and *italic*")
        assertEquals(3, result.size)
        assertTrue(result[0] is Strong)
        assertTrue(result[1] is Text)
        assertTrue(result[2] is Emphasis)
    }

    @Test
    fun `nested emphasis inside strong`() {
        val result = inlines("**bold _and italic_ text**")
        val strong = result.single() as Strong
        assertTrue(strong.children.any { it is Emphasis })
    }

    @Test
    fun `hard line break from trailing double space`() {
        val result = inlines("line one  \nline two")
        assertTrue(result.any { it is LineBreak })
    }

    @Test
    fun `unclosed emphasis degrades to literal`() {
        val result = inlines("*unclosed text")
        assertEquals(listOf(Text("*unclosed text")), result)
    }

    @Test
    fun `unclosed strong degrades to literal`() {
        val result = inlines("**unclosed")
        assertEquals(listOf(Text("**unclosed")), result)
    }

    @Test
    fun `inline code with special chars inside`() {
        assertEquals(listOf(InlineCode("a*b*c")), inlines("`a*b*c`"))
    }
}
