package com.inspiredandroid.kai.ui.dynamicui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KaiUiTtsTest {

    @Test
    fun `plain markdown passes through strip-markdown`() {
        assertEquals("Hello world", "**Hello** world".toSpeakableText())
    }

    @Test
    fun `speaks text nodes and button labels inside a kai-ui fence`() {
        val message = """
            Intro line.

            ```kai-ui
            {"type":"column","children":[
              {"type":"text","value":"Pick a cheese"},
              {"type":"button","label":"Mozzarella","action":{"type":"callback","event":"pick","data":{"choice":"moz"}}},
              {"type":"button","label":"Gorgonzola","action":{"type":"callback","event":"pick","data":{"choice":"gor"}}}
            ]}
            ```

            Anything else?
        """.trimIndent()

        val spoken = message.toSpeakableText()

        assertTrue(spoken.contains("Intro line"))
        assertTrue(spoken.contains("Pick a cheese"))
        assertTrue(spoken.contains("Mozzarella"))
        assertTrue(spoken.contains("Gorgonzola"))
        assertTrue(spoken.contains("Anything else?"))
    }

    @Test
    fun `speaks alert title and message`() {
        val message = """
            ```kai-ui
            {"type":"alert","title":"Heads up","message":"Pick carefully"}
            ```
        """.trimIndent()

        val spoken = message.toSpeakableText()
        assertTrue(spoken.contains("Heads up"))
        assertTrue(spoken.contains("Pick carefully"))
    }

    @Test
    fun `skips code nodes`() {
        val message = """
            ```kai-ui
            {"type":"code","code":"val x = 42","language":"kotlin"}
            ```
        """.trimIndent()

        assertEquals("", message.toSpeakableText())
    }
}
