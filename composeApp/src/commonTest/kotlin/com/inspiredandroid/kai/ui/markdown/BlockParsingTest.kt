package com.inspiredandroid.kai.ui.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BlockParsingTest {

    @Test
    fun `atx headings h1 through h6`() {
        for (level in 1..6) {
            val hashes = "#".repeat(level)
            val doc = parseMarkdown("$hashes Title")
            val heading = doc.blocks.single() as Heading
            assertEquals(level, heading.level)
            assertEquals(listOf(Text("Title")), heading.inlines)
        }
    }

    @Test
    fun `atx heading allows trailing hashes`() {
        val doc = parseMarkdown("## Title ##")
        val heading = doc.blocks.single() as Heading
        assertEquals(2, heading.level)
        assertEquals("Title", (heading.inlines.single() as Text).value)
    }

    @Test
    fun `setext h1 and h2`() {
        val h1 = parseMarkdown("Title\n===")
        assertEquals(Heading(1, listOf(Text("Title"))), h1.blocks.single())
        val h2 = parseMarkdown("Title\n---")
        assertEquals(Heading(2, listOf(Text("Title"))), h2.blocks.single())
    }

    @Test
    fun `horizontal rules`() {
        assertEquals(HorizontalRule, parseMarkdown("---").blocks.single())
        assertEquals(HorizontalRule, parseMarkdown("***").blocks.single())
        assertEquals(HorizontalRule, parseMarkdown("___").blocks.single())
        assertEquals(HorizontalRule, parseMarkdown("- - -").blocks.single())
    }

    @Test
    fun `fenced code block with language`() {
        val doc = parseMarkdown("```kotlin\nval x = 1\n```")
        val fence = doc.blocks.single() as CodeFence
        assertEquals("kotlin", fence.language)
        assertEquals("val x = 1", fence.code)
        assertTrue(fence.closed)
    }

    @Test
    fun `fenced code block with no language`() {
        val doc = parseMarkdown("```\ncode\n```")
        val fence = doc.blocks.single() as CodeFence
        assertEquals(null, fence.language)
        assertEquals("code", fence.code)
        assertTrue(fence.closed)
    }

    @Test
    fun `unclosed fenced code is rendered with closed=false`() {
        val doc = parseMarkdown("```python\nprint('hi')")
        val fence = doc.blocks.single() as CodeFence
        assertEquals("python", fence.language)
        assertEquals("print('hi')", fence.code)
        assertEquals(false, fence.closed)
    }

    @Test
    fun `tilde fence is supported`() {
        val doc = parseMarkdown("~~~js\nlet x = 1\n~~~")
        val fence = doc.blocks.single() as CodeFence
        assertEquals("js", fence.language)
        assertEquals("let x = 1", fence.code)
    }

    @Test
    fun `blockquote single line`() {
        val doc = parseMarkdown("> quoted")
        val bq = doc.blocks.single() as Blockquote
        val inner = bq.children.single() as Paragraph
        assertEquals("quoted", (inner.inlines.single() as Text).value)
    }

    @Test
    fun `blockquote multiple lines`() {
        val doc = parseMarkdown("> line 1\n> line 2")
        val bq = doc.blocks.single() as Blockquote
        val inner = bq.children.single() as Paragraph
        assertEquals("line 1\nline 2", (inner.inlines.single() as Text).value)
    }

    @Test
    fun `bullet list with dash`() {
        val doc = parseMarkdown("- a\n- b\n- c")
        val list = doc.blocks.single() as BulletList
        assertEquals(3, list.items.size)
        assertTrue(list.tight)
        assertEquals("a", (list.items[0].children.single() as Paragraph).inlines.joinToString("") { (it as Text).value })
    }

    @Test
    fun `ordered list starting at 5`() {
        val doc = parseMarkdown("5. first\n6. second")
        val list = doc.blocks.single() as OrderedList
        assertEquals(5, list.start)
        assertEquals(2, list.items.size)
    }

    @Test
    fun `loose list via blank line between items`() {
        val doc = parseMarkdown("- a\n\n- b")
        val list = doc.blocks.single() as BulletList
        assertEquals(2, list.items.size)
        assertEquals(false, list.tight)
    }

    @Test
    fun `nested bullet list`() {
        val doc = parseMarkdown("- outer\n  - inner1\n  - inner2\n- second")
        val outer = doc.blocks.single() as BulletList
        assertEquals(2, outer.items.size)
        val first = outer.items[0]
        val nested = first.children.firstOrNull { it is BulletList } as? BulletList
        assertNotNull(nested)
        assertEquals(2, nested!!.items.size)
    }

    @Test
    fun `simple table with alignment`() {
        val doc = parseMarkdown("| a | b | c |\n| :- | :-: | -: |\n| 1 | 2 | 3 |")
        val table = doc.blocks.single() as Table
        assertEquals(listOf(ColumnAlign.LEFT, ColumnAlign.CENTER, ColumnAlign.RIGHT), table.alignments)
        assertEquals(3, table.headers.size)
        assertEquals(1, table.rows.size)
        assertEquals("1", (table.rows[0][0].single() as Text).value)
    }

    @Test
    fun `table without outer pipes`() {
        val doc = parseMarkdown("a | b\n---|---\n1 | 2")
        val table = doc.blocks.single() as Table
        assertEquals(2, table.headers.size)
        assertEquals(1, table.rows.size)
    }

    @Test
    fun `multiple paragraphs separated by blank line`() {
        val doc = parseMarkdown("first\n\nsecond")
        assertEquals(2, doc.blocks.size)
        assertTrue(doc.blocks[0] is Paragraph)
        assertTrue(doc.blocks[1] is Paragraph)
    }

    @Test
    fun `paragraph ends at heading opener`() {
        val doc = parseMarkdown("para\n# heading")
        assertEquals(2, doc.blocks.size)
        assertTrue(doc.blocks[0] is Paragraph)
        assertTrue(doc.blocks[1] is Heading)
    }

    @Test
    fun `empty input produces empty document`() {
        assertEquals(emptyList(), parseMarkdown("").blocks)
        assertEquals(emptyList(), parseMarkdown("   \n  ").blocks)
    }
}
