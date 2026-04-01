package com.inspiredandroid.kai.ui.dynamicui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class KaiUiParserTest {

    @Test
    fun `detects kai-ui blocks`() {
        val message = "Hello\n```kai-ui\n{\"type\":\"text\",\"value\":\"Hi\"}\n```\nBye"
        assertTrue(KaiUiParser.containsUiBlocks(message))
    }

    @Test
    fun `no false positive on regular code blocks`() {
        val message = "```kotlin\nval x = 1\n```"
        assertFalse(KaiUiParser.containsUiBlocks(message))
    }

    @Test
    fun `parses text node`() {
        val message = "Before\n```kai-ui\n{\"type\":\"text\",\"value\":\"Hello\"}\n```\nAfter"
        val segments = KaiUiParser.parse(message)
        assertEquals(3, segments.size)
        assertIs<KaiUiParser.MarkdownSegment>(segments[0])
        assertIs<KaiUiParser.UiSegment>(segments[1])
        assertIs<KaiUiParser.MarkdownSegment>(segments[2])

        val uiSegment = segments[1] as KaiUiParser.UiSegment
        val textNode = assertIs<TextNode>(uiSegment.node)
        assertEquals("Hello", textNode.value)
    }

    @Test
    fun `parses column with children`() {
        val json = """
            {
              "type": "column",
              "children": [
                {"type": "text", "value": "Title", "style": "headline"},
                {"type": "button", "label": "Click", "action": {"type": "callback", "event": "click"}}
              ]
            }
        """.trimIndent()
        val message = "```kai-ui\n$json\n```"
        val segments = KaiUiParser.parse(message)
        assertEquals(1, segments.size)
        val node = (segments[0] as KaiUiParser.UiSegment).node
        assertIs<ColumnNode>(node)
        assertEquals(2, node.children.size)
        assertIs<TextNode>(node.children[0])
        assertIs<ButtonNode>(node.children[1])
    }

    @Test
    fun `callback action with collectFrom`() {
        val json = """{"type":"button","label":"Submit","action":{"type":"callback","event":"submit","collectFrom":["name","email"]}}"""
        val message = "```kai-ui\n$json\n```"
        val segments = KaiUiParser.parse(message)
        val button = (segments[0] as KaiUiParser.UiSegment).node as ButtonNode
        val action = button.action as CallbackAction
        assertEquals("submit", action.event)
        assertEquals(listOf("name", "email"), action.collectFrom)
    }

    @Test
    fun `invalid json produces error segment`() {
        val message = "```kai-ui\n{invalid json}\n```"
        val segments = KaiUiParser.parse(message)
        assertEquals(1, segments.size)
        assertIs<KaiUiParser.ErrorSegment>(segments[0])
    }

    @Test
    fun `multiple ui blocks in one message`() {
        val message = "First block:\n```kai-ui\n{\"type\":\"text\",\"value\":\"A\"}\n```\nMiddle text\n```kai-ui\n{\"type\":\"text\",\"value\":\"B\"}\n```\nEnd"
        val segments = KaiUiParser.parse(message)
        assertEquals(5, segments.size)
        assertIs<KaiUiParser.MarkdownSegment>(segments[0])
        assertIs<KaiUiParser.UiSegment>(segments[1])
        assertIs<KaiUiParser.MarkdownSegment>(segments[2])
        assertIs<KaiUiParser.UiSegment>(segments[3])
        assertIs<KaiUiParser.MarkdownSegment>(segments[4])
    }

    @Test
    fun `interactive nodes require id`() {
        val json = """{"type":"text_input","id":"name","label":"Name"}"""
        val message = "```kai-ui\n$json\n```"
        val segments = KaiUiParser.parse(message)
        val node = (segments[0] as KaiUiParser.UiSegment).node as TextInputNode
        assertEquals("name", node.id)
        assertEquals("Name", node.label)
    }

    @Test
    fun `select node with options`() {
        val json = """{"type":"select","id":"color","label":"Color","options":["Red","Blue","Green"],"selected":"Blue"}"""
        val message = "```kai-ui\n$json\n```"
        val segments = KaiUiParser.parse(message)
        val node = (segments[0] as KaiUiParser.UiSegment).node as SelectNode
        assertEquals(listOf("Red", "Blue", "Green"), node.options)
        assertEquals("Blue", node.selected)
    }

    @Test
    fun `toggle action`() {
        val json = """{"type":"button","label":"Toggle","action":{"type":"toggle","targetId":"details"}}"""
        val message = "```kai-ui\n$json\n```"
        val segments = KaiUiParser.parse(message)
        val button = (segments[0] as KaiUiParser.UiSegment).node as ButtonNode
        val toggle = assertIs<ToggleAction>(button.action)
        assertEquals("details", toggle.targetId)
    }

    @Test
    fun `open url action`() {
        val json = """{"type":"button","label":"Visit","action":{"type":"open_url","url":"https://example.com"}}"""
        val message = "```kai-ui\n$json\n```"
        val segments = KaiUiParser.parse(message)
        val button = (segments[0] as KaiUiParser.UiSegment).node as ButtonNode
        val openUrl = assertIs<OpenUrlAction>(button.action)
        assertEquals("https://example.com", openUrl.url)
    }

    @Test
    fun `handles extra trailing braces from LLM`() {
        val json = """{"type":"text","value":"Hi"}}"""
        val message = "```kai-ui\n$json\n```"
        val segments = KaiUiParser.parse(message)
        assertEquals(1, segments.size)
        assertIs<KaiUiParser.UiSegment>(segments[0])
        val node = (segments[0] as KaiUiParser.UiSegment).node
        assertIs<TextNode>(node)
        assertEquals("Hi", node.value)
    }

    @Test
    fun `handles multiple extra trailing braces`() {
        val json = """{"type":"column","children":[{"type":"text","value":"A"}]}}}"""
        val message = "```kai-ui\n$json\n```"
        val segments = KaiUiParser.parse(message)
        assertEquals(1, segments.size)
        assertIs<KaiUiParser.UiSegment>(segments[0])
        assertIs<ColumnNode>((segments[0] as KaiUiParser.UiSegment).node)
    }

    @Test
    fun `detects kai-ui block without newlines around content`() {
        val message = "```kai-ui{\"type\":\"text\",\"value\":\"Hi\"}```"
        assertTrue(KaiUiParser.containsUiBlocks(message))
        val segments = KaiUiParser.parse(message)
        assertEquals(1, segments.size)
        val node = (segments[0] as KaiUiParser.UiSegment).node
        assertIs<TextNode>(node)
    }

    @Test
    fun `parses complex nested kai-ui from kimi model`() {
        val json = """{"type":"column","children":[{"type":"text","value":"Wilderness Survival","style":"headline","bold":true},{"type":"text","value":"You wake up in a cold pine forest.","style":"body"},{"type":"divider"},{"type":"text","value":"Status","style":"title"},{"type":"row","children":[{"type":"card","children":[{"type":"text","value":"Health: 80/100","style":"body"}]},{"type":"card","children":[{"type":"text","value":"Hunger: 30/100","style":"body"}]},{"type":"card","children":[{"type":"text","value":"Energy: 70/100","style":"body"}]}]},{"type":"text","value":"What do you want to do?","style":"title"},{"type":"column","children":[{"type":"button","label":"Follow river","action":{"type":"callback","event":"survival_choice","data":{"choice":"river"}},"variant":"filled"},{"type":"button","label":"Head to mountains","action":{"type":"callback","event":"survival_choice","data":{"choice":"mountains"}},"variant":"filled"},{"type":"button","label":"Stay & build camp here","action":{"type":"callback","event":"survival_choice","data":{"choice":"camp"}},"variant":"filled"}]}]}"""
        val message = "```kai-ui\n$json\n```\n\nType \"stop game\" anytime to quit."
        assertTrue(KaiUiParser.containsUiBlocks(message))
        val segments = KaiUiParser.parse(message)
        assertEquals(2, segments.size)
        assertIs<KaiUiParser.UiSegment>(segments[0])
        assertIs<KaiUiParser.MarkdownSegment>(segments[1])
        val column = (segments[0] as KaiUiParser.UiSegment).node
        assertIs<ColumnNode>(column)
        assertEquals(7, column.children.size)
    }
}
