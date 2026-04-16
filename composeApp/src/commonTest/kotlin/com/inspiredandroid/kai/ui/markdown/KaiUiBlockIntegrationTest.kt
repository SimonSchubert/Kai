package com.inspiredandroid.kai.ui.markdown

import com.inspiredandroid.kai.ui.dynamicui.AlertNode
import com.inspiredandroid.kai.ui.dynamicui.ColumnNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KaiUiBlockIntegrationTest {

    @Test
    fun `kai-ui fence produces KaiUiBlock`() {
        val md = """
            ```kai-ui
            {"type":"alert","title":"Heads up","message":"Hello"}
            ```
        """.trimIndent()
        val block = parseMarkdown(md).blocks.single()
        assertTrue(block is KaiUiBlock)
        val alert = (block as KaiUiBlock).node as AlertNode
        assertEquals("Heads up", alert.title)
        assertEquals("Hello", alert.message)
    }

    @Test
    fun `malformed kai-ui fence produces KaiUiError`() {
        val md = """
            ```kai-ui
            not json at all
            ```
        """.trimIndent()
        val block = parseMarkdown(md).blocks.single()
        assertTrue(block is KaiUiError)
    }

    @Test
    fun `ndjson multi-line kai-ui wraps children in a column`() {
        val md = """
            ```kai-ui
            {"type":"text","value":"a"}
            {"type":"text","value":"b"}
            ```
        """.trimIndent()
        val block = parseMarkdown(md).blocks.single()
        assertTrue(block is KaiUiBlock)
        val col = (block as KaiUiBlock).node as ColumnNode
        assertEquals(2, col.children.size)
    }

    @Test
    fun `kai-ui block surrounded by markdown produces three blocks`() {
        val md = """
            Before

            ```kai-ui
            {"type":"alert","message":"hi"}
            ```

            After
        """.trimIndent()
        val blocks = parseMarkdown(md).blocks
        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is Paragraph)
        assertTrue(blocks[1] is KaiUiBlock)
        assertTrue(blocks[2] is Paragraph)
    }

    @Test
    fun `split-block pattern with json fence is treated as kai-ui`() {
        val md = """
            kai-ui
            ```json
            {"type":"alert","message":"hi"}
            ```
        """.trimIndent()
        val block = parseMarkdown(md).blocks.single()
        assertTrue(block is KaiUiBlock)
    }

    @Test
    fun `kai-ui block speakable text walks the node tree`() {
        val md = """
            Intro.

            ```kai-ui
            {"type":"alert","title":"Heads up","message":"Take care"}
            ```

            Outro.
        """.trimIndent()
        val spoken = parseMarkdown(md).toSpeakableText()
        assertTrue(spoken.contains("Intro"))
        assertTrue(spoken.contains("Heads up"))
        assertTrue(spoken.contains("Take care"))
        assertTrue(spoken.contains("Outro"))
    }
}
