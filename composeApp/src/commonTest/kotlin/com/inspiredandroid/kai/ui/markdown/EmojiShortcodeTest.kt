package com.inspiredandroid.kai.ui.markdown

import kotlin.test.Test
import kotlin.test.assertEquals

class EmojiShortcodeTest {

    private fun inlines(text: String): List<InlineNode> = (parseMarkdown(text).blocks.single() as Paragraph).inlines

    @Test
    fun `maps common shortcodes to emoji`() {
        val result = inlines(":tada: :rocket: :sparkles: :+1:")
        val flat = result.joinToString("") { (it as Text).value }
        assertEquals("\uD83C\uDF89 \uD83D\uDE80 \u2728 \uD83D\uDC4D", flat)
    }

    @Test
    fun `unknown shortcode is left as literal text`() {
        assertEquals(listOf(Text(":unknown_made_up_thing:")), inlines(":unknown_made_up_thing:"))
    }

    @Test
    fun `shortcodes work inside emphasis`() {
        val result = inlines("**look: :fire: hot**")
        val strong = result.single() as Strong
        val flat = strong.children.joinToString("") { (it as Text).value }
        assertEquals("look: \uD83D\uDD25 hot", flat)
    }
}
