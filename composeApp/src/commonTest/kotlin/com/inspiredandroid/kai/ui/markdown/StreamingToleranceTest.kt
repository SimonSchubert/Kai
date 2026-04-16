package com.inspiredandroid.kai.ui.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreamingToleranceTest {

    @Test
    fun `unclosed code fence yields open fence`() {
        val doc = parseMarkdown("```kotlin\nval x =")
        val fence = doc.blocks.single() as CodeFence
        assertEquals(false, fence.closed)
        assertEquals("kotlin", fence.language)
        assertEquals("val x =", fence.code)
    }

    @Test
    fun `unclosed emphasis yields literal text`() {
        val doc = parseMarkdown("this is *partial")
        val para = doc.blocks.single() as Paragraph
        assertEquals(listOf(Text("this is *partial")), para.inlines)
    }

    @Test
    fun `partial link yields literal text`() {
        val doc = parseMarkdown("before [foo")
        val para = doc.blocks.single() as Paragraph
        assertEquals(listOf(Text("before [foo")), para.inlines)
    }

    @Test
    fun `partial image yields literal text`() {
        val doc = parseMarkdown("before ![alt")
        val para = doc.blocks.single() as Paragraph
        assertEquals(listOf(Text("before ![alt")), para.inlines)
    }

    @Test
    fun `truncated kai-ui json is repaired into a block`() {
        val md = """
            ```kai-ui
            {"type":"column","children":[{"type":"text","value":"a"
        """.trimIndent()
        val doc = parseMarkdown(md)
        assertEquals(1, doc.blocks.size)
        assertTrue(doc.blocks[0] is KaiUiBlock || doc.blocks[0] is KaiUiError)
    }

    @Test
    fun `trailing incomplete paragraph still renders`() {
        val doc = parseMarkdown("Full paragraph.\n\nPartial **bold")
        assertEquals(2, doc.blocks.size)
        assertTrue(doc.blocks[1] is Paragraph)
    }

    @Test
    fun `document always renders - even after malformed table`() {
        val doc = parseMarkdown("| a | b |\n| - |")
        assertTrue(doc.blocks.isNotEmpty())
    }
}
