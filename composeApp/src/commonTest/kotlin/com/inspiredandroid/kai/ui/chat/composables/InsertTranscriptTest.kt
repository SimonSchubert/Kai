package com.inspiredandroid.kai.ui.chat.composables

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals

class InsertTranscriptTest {

    @Test
    fun `inserts transcript into an empty composer`() {
        val result = insertTranscript(TextFieldValue(""), "Hello there")

        assertEquals("Hello there", result.text)
        assertEquals(TextRange(11), result.selection)
    }

    @Test
    fun `appends transcript without joining words`() {
        val result = insertTranscript(
            TextFieldValue("What time is it", selection = TextRange(15)),
            "right now",
        )

        assertEquals("What time is it right now", result.text)
    }

    @Test
    fun `replaces selected text and leaves the cursor after the transcript`() {
        val result = insertTranscript(
            TextFieldValue("Hello world", selection = TextRange(6, 11)),
            "Kai",
        )

        assertEquals("Hello Kai", result.text)
        assertEquals(TextRange(9), result.selection)
    }

    @Test
    fun `inserts between words with spacing on both sides`() {
        val result = insertTranscript(
            TextFieldValue("HelloWorld", selection = TextRange(5)),
            "there",
        )

        assertEquals("Hello there World", result.text)
        assertEquals(TextRange(11), result.selection)
    }

    @Test
    fun `ignores blank recognition results`() {
        val original = TextFieldValue("Keep this", selection = TextRange(4))

        assertEquals(original, insertTranscript(original, "   "))
    }
}
