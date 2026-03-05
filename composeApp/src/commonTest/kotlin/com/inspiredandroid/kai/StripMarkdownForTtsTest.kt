package com.inspiredandroid.kai

import kotlin.test.Test
import kotlin.test.assertEquals

class StripMarkdownForTtsTest {

    @Test
    fun `strips headers`() {
        assertEquals("Title", "# Title".stripMarkdownForTts())
        assertEquals("Subtitle", "## Subtitle".stripMarkdownForTts())
        assertEquals("Deep", "###### Deep".stripMarkdownForTts())
    }

    @Test
    fun `strips bold`() {
        assertEquals("bold text", "**bold text**".stripMarkdownForTts())
        assertEquals("bold text", "__bold text__".stripMarkdownForTts())
    }

    @Test
    fun `strips italic`() {
        assertEquals("italic", "*italic*".stripMarkdownForTts())
        assertEquals("italic", "_italic_".stripMarkdownForTts())
    }

    @Test
    fun `strips bold and italic combined`() {
        assertEquals("bold and italic", "**bold** and *italic*".stripMarkdownForTts())
    }

    @Test
    fun `strips inline code`() {
        assertEquals("code here", "`code here`".stripMarkdownForTts())
    }

    @Test
    fun `strips code blocks`() {
        val input = "```kotlin\nval x = 1\n```"
        assertEquals("val x = 1", input.stripMarkdownForTts())
    }

    @Test
    fun `strips links keeping text`() {
        assertEquals("click here", "[click here](https://example.com)".stripMarkdownForTts())
    }

    @Test
    fun `strips images keeping alt text`() {
        assertEquals("photo", "![photo](https://example.com/img.png)".stripMarkdownForTts())
    }

    @Test
    fun `strips strikethrough`() {
        assertEquals("removed", "~~removed~~".stripMarkdownForTts())
    }

    @Test
    fun `strips horizontal rules`() {
        assertEquals("", "---".stripMarkdownForTts())
        assertEquals("", "***".stripMarkdownForTts())
        assertEquals("", "___".stripMarkdownForTts())
    }

    @Test
    fun `strips blockquotes`() {
        assertEquals("quoted text", "> quoted text".stripMarkdownForTts())
    }

    @Test
    fun `strips unordered list markers and adds period`() {
        assertEquals("item one.\nitem two.", "- item one\n- item two".stripMarkdownForTts())
        assertEquals("item.", "* item".stripMarkdownForTts())
    }

    @Test
    fun `strips ordered list markers and adds period`() {
        assertEquals("first.\nsecond.", "1. first\n2. second".stripMarkdownForTts())
    }

    @Test
    fun `does not double punctuate list items`() {
        assertEquals("already done.", "- already done.".stripMarkdownForTts())
        assertEquals("is it?", "- is it?".stripMarkdownForTts())
        assertEquals("wow!", "- wow!".stripMarkdownForTts())
    }

    @Test
    fun `collapses multiple blank lines`() {
        assertEquals("a\n\nb", "a\n\n\n\nb".stripMarkdownForTts())
    }

    @Test
    fun `handles mixed markdown`() {
        val input = "# Hello\n\nThis is **bold** and *italic* with `code`.\n\n- item one\n- item two"
        val expected = "Hello\n\nThis is bold and italic with code.\n\nitem one.\nitem two."
        assertEquals(expected, input.stripMarkdownForTts())
    }

    @Test
    fun `plain text is unchanged`() {
        assertEquals("Hello world", "Hello world".stripMarkdownForTts())
    }
}
