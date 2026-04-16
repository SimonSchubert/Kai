package com.inspiredandroid.kai.ui.markdown

import kotlin.test.Test
import kotlin.test.assertEquals

class MarkdownToSpeakableTextTest {

    private fun speak(md: String) = parseMarkdown(md).toSpeakableText()

    @Test
    fun `strips headers`() {
        assertEquals("Title", speak("# Title"))
        assertEquals("Subtitle", speak("## Subtitle"))
        assertEquals("Deep", speak("###### Deep"))
    }

    @Test
    fun `strips bold`() {
        assertEquals("bold text", speak("**bold text**"))
        assertEquals("bold text", speak("__bold text__"))
    }

    @Test
    fun `strips italic`() {
        assertEquals("italic", speak("*italic*"))
        assertEquals("italic", speak("_italic_"))
    }

    @Test
    fun `strips bold and italic combined`() {
        assertEquals("bold and italic", speak("**bold** and *italic*"))
    }

    @Test
    fun `strips inline code keeping text`() {
        assertEquals("code here", speak("`code here`"))
    }

    @Test
    fun `drops code block entirely`() {
        assertEquals("", speak("```kotlin\nval x = 1\n```"))
    }

    @Test
    fun `strips links keeping text`() {
        assertEquals("click here", speak("[click here](https://example.com)"))
    }

    @Test
    fun `strips images keeping alt text`() {
        assertEquals("photo", speak("![photo](https://example.com/img.png)"))
    }

    @Test
    fun `strips strikethrough`() {
        assertEquals("removed", speak("~~removed~~"))
    }

    @Test
    fun `horizontal rules become empty`() {
        assertEquals("", speak("---"))
        assertEquals("", speak("***"))
        assertEquals("", speak("___"))
    }

    @Test
    fun `blockquote yields inner text`() {
        assertEquals("quoted text", speak("> quoted text"))
    }

    @Test
    fun `unordered list items get period separator`() {
        assertEquals("item one.\nitem two.", speak("- item one\n- item two"))
        assertEquals("item.", speak("* item"))
    }

    @Test
    fun `ordered list items get period separator`() {
        assertEquals("first.\nsecond.", speak("1. first\n2. second"))
    }

    @Test
    fun `list items keep existing end punctuation`() {
        assertEquals("already done.", speak("- already done."))
        assertEquals("is it?", speak("- is it?"))
        assertEquals("wow!", speak("- wow!"))
    }

    @Test
    fun `multiple blank lines collapse to single paragraph break`() {
        assertEquals("a\n\nb", speak("a\n\n\n\nb"))
    }

    @Test
    fun `mixed markdown document`() {
        val input = "# Hello\n\nThis is **bold** and *italic* with `code`.\n\n- item one\n- item two"
        val expected = "Hello\n\nThis is bold and italic with code.\n\nitem one.\nitem two."
        assertEquals(expected, speak(input))
    }

    @Test
    fun `plain text passes through unchanged`() {
        assertEquals("Hello world", speak("Hello world"))
    }

    @Test
    fun `table reads headers and rows`() {
        val md = "| A | B |\n| - | - |\n| 1 | 2 |\n| 3 | 4 |"
        assertEquals("A, B. 1, 2. 3, 4", speak(md))
    }
}
