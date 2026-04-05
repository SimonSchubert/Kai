package com.inspiredandroid.kai.ui.dynamicui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
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
    fun `parses multi-line JSON objects as column`() {
        val block = """
            {"type":"text","value":"Question 1 of 3","style":"caption"}
            {"type":"text","value":"Complete the sequence:","style":"body","bold":true}
            {"type":"text","value":"2, 6, 12, 20, 30, ?","style":"title"}
            {"type":"spacer"}
            {"type":"column","children":[{"type":"button","label":"42","action":{"type":"callback","event":"answer","data":{"answer":"42"}},"variant":"filled"}]}
        """.trimIndent()
        val message = "Here's a quiz:\n```kai-ui\n$block\n```\nGood luck!"
        val segments = KaiUiParser.parse(message)
        assertEquals(3, segments.size)
        assertIs<KaiUiParser.MarkdownSegment>(segments[0])
        assertIs<KaiUiParser.UiSegment>(segments[1])
        assertIs<KaiUiParser.MarkdownSegment>(segments[2])

        val column = (segments[1] as KaiUiParser.UiSegment).node
        assertIs<ColumnNode>(column)
        assertEquals(5, column.children.size)
        assertIs<TextNode>(column.children[0])
        assertIs<TextNode>(column.children[1])
        assertIs<TextNode>(column.children[2])
        assertIs<SpacerNode>(column.children[3])
        assertIs<ColumnNode>(column.children[4])
    }

    @Test
    fun `multi-line skips malformed line and renders the rest`() {
        // Simulates LLM adding an extra } inside a nested line
        val block = """
            {"type":"text","value":"Question 1","style":"caption"}
            {"type":"text","value":"Pick one:","style":"title"}
            {invalid json here}
            {"type":"text","value":"Good luck","style":"body"}
        """.trimIndent()
        val message = "```kai-ui\n$block\n```"
        val segments = KaiUiParser.parse(message)
        assertEquals(1, segments.size)
        val column = (segments[0] as KaiUiParser.UiSegment).node
        assertIs<ColumnNode>(column)
        // The invalid line is skipped, but the 3 valid lines parse
        assertEquals(3, column.children.size)
    }

    @Test
    fun `multi-line with extra closing brace in nested column from LLM`() {
        // Real-world kimi-k2.5 output: multi-line NDJSON where the column line has an extra }
        val block = """
            {"type":"text","value":"Question 1 of 3","style":"caption","color":"secondary"}
            {"type":"text","value":"Complete the sequence:","style":"body","bold":true}
            {"type":"text","value":"2, 6, 12, 20, 30, ?","style":"title"}
            {"type":"spacer","size":12}
            {"type":"column","children":[{"type":"button","label":"38","action":{"type":"callback","event":"answer_q1","data":{"answer":"38"}},"variant":"filled"},{"type":"button","label":"40","action":{"type":"callback","event":"answer_q1","data":{"answer":"40"}},"variant":"filled"},{"type":"button","label":"42","action":{"type":"callback","event":"answer_q1","data":{"answer":"42"}},"variant":"filled"},{"type":"button","label":"44","action":{"type":"callback","event":"answer_q1","data":{"answer":"44"}},"variant":"filled"}}]}
        """.trimIndent()
        val message = "Sure! Let's see how sharp you are today.\n\n```kai-ui\n$block\n```\n\nTake your shot!"
        val segments = KaiUiParser.parse(message)
        assertEquals(3, segments.size)
        assertIs<KaiUiParser.MarkdownSegment>(segments[0])
        assertIs<KaiUiParser.UiSegment>(segments[1])
        assertIs<KaiUiParser.MarkdownSegment>(segments[2])

        val column = (segments[1] as KaiUiParser.UiSegment).node
        assertIs<ColumnNode>(column)
        // sanitizeJson repairs the extra } so all 5 lines parse including buttons
        assertEquals(5, column.children.size)
        assertIs<TextNode>(column.children[0])
        assertEquals("Question 1 of 3", (column.children[0] as TextNode).value)
        assertIs<TextNode>(column.children[1])
        assertIs<TextNode>(column.children[2])
        assertIs<SpacerNode>(column.children[3])
        val buttonsColumn = assertIs<ColumnNode>(column.children[4])
        assertEquals(4, buttonsColumn.children.size)
        assertIs<ButtonNode>(buttonsColumn.children[0])
    }

    @Test
    fun `single-line column with extra closing brace in children`() {
        // Real-world kimi-k2.5 output: single column with buttons, extra } before ]}
        val json = """{"type":"column","children":[{"type":"button","label":"All roses fade quickly","action":{"type":"callback","event":"answer_q2","data":{"answer":"all"}},"variant":"filled"},{"type":"button","label":"Some roses fade quickly","action":{"type":"callback","event":"answer_q2","data":{"answer":"some"}},"variant":"filled"},{"type":"button","label":"No roses fade quickly","action":{"type":"callback","event":"answer_q2","data":{"answer":"none"}},"variant":"filled"},{"type":"button","label":"None of these follow","action":{"type":"callback","event":"answer_q2","data":{"answer":"none_follow"}},"variant":"filled"}}]}"""
        val message = "Which statement is necessarily true?\n\n```kai-ui\n$json\n```"
        val segments = KaiUiParser.parse(message)
        assertEquals(2, segments.size)
        assertIs<KaiUiParser.MarkdownSegment>(segments[0])
        assertIs<KaiUiParser.UiSegment>(segments[1])

        val column = (segments[1] as KaiUiParser.UiSegment).node
        assertIs<ColumnNode>(column)
        assertEquals(4, column.children.size)
        for (child in column.children) {
            assertIs<ButtonNode>(child)
        }
        assertEquals("All roses fade quickly", (column.children[0] as ButtonNode).label)
        assertEquals("None of these follow", (column.children[3] as ButtonNode).label)
    }

    @Test
    fun `extra closing bracket inside nested structure is skipped`() {
        // Extra ] where } is expected — sanitizeJson should skip it
        val json = """{"type":"column","children":[{"type":"text","value":"A"},{"type":"text","value":"B"}]]}"""
        val message = "```kai-ui\n$json\n```"
        val segments = KaiUiParser.parse(message)
        assertEquals(1, segments.size)
        assertIs<KaiUiParser.UiSegment>(segments[0])
        val column = (segments[0] as KaiUiParser.UiSegment).node
        assertIs<ColumnNode>(column)
        assertEquals(2, column.children.size)
    }

    @Test
    fun `callback data with non-string values`() {
        // LLMs sometimes send booleans or numbers in the data map instead of strings
        val json = """{"type":"button","label":"Continue","action":{"type":"callback","event":"continue","data":{"continue":true,"count":42,"name":"test"}}}"""
        val message = "```kai-ui\n$json\n```"
        val segments = KaiUiParser.parse(message)
        assertEquals(1, segments.size)
        val button = (segments[0] as KaiUiParser.UiSegment).node as ButtonNode
        val action = button.action as CallbackAction
        assertEquals("continue", action.event)
        val data = action.dataAsStrings!!
        assertEquals("true", data["continue"])
        assertEquals("42", data["count"])
        assertEquals("test", data["name"])
    }

    @Test
    fun `fixes equals sign instead of colon in key-value separator`() {
        val json = """{"type":"column","children=[{"type":"text","value":"Hello"}]}"""
        val message = "```kai-ui\n$json\n```"
        val segments = KaiUiParser.parse(message)
        assertEquals(1, segments.size)
        assertIs<KaiUiParser.UiSegment>(segments[0])
        val column = (segments[0] as KaiUiParser.UiSegment).node
        assertIs<ColumnNode>(column)
        assertEquals(1, column.children.size)
        assertIs<TextNode>(column.children[0])
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

    // --- Tests for new node types ---

    @Test
    fun `parses switch node`() {
        val json = """{"type":"switch","id":"dark_mode","label":"Dark Mode","checked":true}"""
        val message = "```kai-ui\n$json\n```"
        val segments = KaiUiParser.parse(message)
        val node = (segments[0] as KaiUiParser.UiSegment).node
        assertIs<SwitchNode>(node)
        assertEquals("dark_mode", node.id)
        assertEquals("Dark Mode", node.label)
        assertEquals(true, node.checked)
    }

    @Test
    fun `parses slider node`() {
        val json = """{"type":"slider","id":"volume","label":"Volume","value":75,"min":0,"max":100,"step":5}"""
        val message = "```kai-ui\n$json\n```"
        val segments = KaiUiParser.parse(message)
        val node = (segments[0] as KaiUiParser.UiSegment).node
        assertIs<SliderNode>(node)
        assertEquals("volume", node.id)
        assertEquals(75f, node.value)
        assertEquals(0f, node.min)
        assertEquals(100f, node.max)
        assertEquals(5f, node.step)
    }

    @Test
    fun `parses radio_group node`() {
        val json = """{"type":"radio_group","id":"size","label":"Size","options":["S","M","L","XL"],"selected":"M"}"""
        val message = "```kai-ui\n$json\n```"
        val segments = KaiUiParser.parse(message)
        val node = (segments[0] as KaiUiParser.UiSegment).node
        assertIs<RadioGroupNode>(node)
        assertEquals(listOf("S", "M", "L", "XL"), node.options)
        assertEquals("M", node.selected)
    }

    @Test
    fun `parses progress node determinate`() {
        val json = """{"type":"progress","value":0.7,"label":"Uploading..."}"""
        val message = "```kai-ui\n$json\n```"
        val segments = KaiUiParser.parse(message)
        val node = (segments[0] as KaiUiParser.UiSegment).node
        assertIs<ProgressNode>(node)
        assertEquals(0.7f, node.value)
        assertEquals("Uploading...", node.label)
    }

    @Test
    fun `parses progress node indeterminate`() {
        val json = """{"type":"progress","label":"Loading..."}"""
        val message = "```kai-ui\n$json\n```"
        val segments = KaiUiParser.parse(message)
        val node = (segments[0] as KaiUiParser.UiSegment).node
        assertIs<ProgressNode>(node)
        assertEquals(null, node.value)
    }

    @Test
    fun `parses alert node`() {
        val json = """{"type":"alert","message":"File saved successfully","title":"Success","severity":"success"}"""
        val message = "```kai-ui\n$json\n```"
        val segments = KaiUiParser.parse(message)
        val node = (segments[0] as KaiUiParser.UiSegment).node
        assertIs<AlertNode>(node)
        assertEquals("File saved successfully", node.message)
        assertEquals("Success", node.title)
        assertEquals(AlertSeverity.SUCCESS, node.severity)
    }

    @Test
    fun `parses chip_group node`() {
        val json = """{"type":"chip_group","id":"tags","chips":[{"label":"Kotlin","value":"kotlin"},{"label":"Java","value":"java"}],"selection":"multi"}"""
        val message = "```kai-ui\n$json\n```"
        val segments = KaiUiParser.parse(message)
        val node = (segments[0] as KaiUiParser.UiSegment).node
        assertIs<ChipGroupNode>(node)
        assertEquals(2, node.chips.size)
        assertEquals("Kotlin", node.chips[0].label)
        assertEquals("kotlin", node.chips[0].value)
        assertEquals("multi", node.selection)
    }

    @Test
    fun `migrates legacy multiSelect to selection`() {
        // Legacy kai-ui blocks in historical chat messages used multiSelect:Boolean.
        val multiJson = """{"type":"chip_group","id":"tags","chips":[{"label":"A"}],"multiSelect":true}"""
        val multiNode = (KaiUiParser.parse("```kai-ui\n$multiJson\n```")[0] as KaiUiParser.UiSegment).node
        assertIs<ChipGroupNode>(multiNode)
        assertEquals("multi", multiNode.selection)

        val singleJson = """{"type":"chip_group","id":"tags","chips":[{"label":"A"}],"multiSelect":false}"""
        val singleNode = (KaiUiParser.parse("```kai-ui\n$singleJson\n```")[0] as KaiUiParser.UiSegment).node
        assertIs<ChipGroupNode>(singleNode)
        assertEquals("single", singleNode.selection)
    }

    @Test
    fun `parses icon node`() {
        val json = """{"type":"icon","name":"star","size":32,"color":"primary"}"""
        val message = "```kai-ui\n$json\n```"
        val segments = KaiUiParser.parse(message)
        val node = (segments[0] as KaiUiParser.UiSegment).node
        assertIs<IconNode>(node)
        assertEquals("star", node.name)
        assertEquals(32, node.size)
        assertEquals("primary", node.color)
    }

    @Test
    fun `parses code node`() {
        val json = """{"type":"code","code":"fun main() { println(\"Hello\") }","language":"kotlin"}"""
        val message = "```kai-ui\n$json\n```"
        val segments = KaiUiParser.parse(message)
        val node = (segments[0] as KaiUiParser.UiSegment).node
        assertIs<CodeNode>(node)
        assertEquals("fun main() { println(\"Hello\") }", node.code)
        assertEquals("kotlin", node.language)
    }

    @Test
    fun `parses tabs node`() {
        val json = """{"type":"tabs","tabs":[{"label":"Tab 1","children":[{"type":"text","value":"Content 1"}]},{"label":"Tab 2","children":[{"type":"text","value":"Content 2"}]}],"selectedIndex":0}"""
        val message = "```kai-ui\n$json\n```"
        val segments = KaiUiParser.parse(message)
        val node = (segments[0] as KaiUiParser.UiSegment).node
        assertIs<TabsNode>(node)
        assertEquals(2, node.tabs.size)
        assertEquals("Tab 1", node.tabs[0].label)
        assertEquals(1, node.tabs[0].children.size)
        assertIs<TextNode>(node.tabs[0].children[0])
    }

    @Test
    fun `parses accordion node`() {
        val json = """{"type":"accordion","title":"More details","children":[{"type":"text","value":"Hidden content"}],"expanded":false}"""
        val message = "```kai-ui\n$json\n```"
        val segments = KaiUiParser.parse(message)
        val node = (segments[0] as KaiUiParser.UiSegment).node
        assertIs<AccordionNode>(node)
        assertEquals("More details", node.title)
        assertEquals(false, node.expanded)
        assertEquals(1, node.children.size)
    }

    @Test
    fun `skips unknown top-level node type`() {
        val json = """{"type":"bottom_bar","buttons":[{"label":"Home","icon":"home"}]}"""
        val message = "```kai-ui\n$json\n```"
        val segments = KaiUiParser.parse(message)
        assertTrue(segments.none { it is KaiUiParser.ErrorSegment })
        assertTrue(segments.none { it is KaiUiParser.UiSegment })
    }

    @Test
    fun `strips unknown child node from children`() {
        val json = """{"type":"column","children":[{"type":"text","value":"Keep"},{"type":"bottom_bar","buttons":[]}]}"""
        val message = "```kai-ui\n$json\n```"
        val segments = KaiUiParser.parse(message)
        val node = (segments[0] as KaiUiParser.UiSegment).node
        assertIs<ColumnNode>(node)
        assertEquals(1, node.children.size)
        assertIs<TextNode>(node.children[0])
    }

    @Test
    fun `parses box node`() {
        val json = """{"type":"box","children":[{"type":"text","value":"Centered"}],"contentAlignment":"center"}"""
        val message = "```kai-ui\n$json\n```"
        val segments = KaiUiParser.parse(message)
        val node = (segments[0] as KaiUiParser.UiSegment).node
        assertIs<BoxNode>(node)
        assertEquals("center", node.contentAlignment)
        assertEquals(1, node.children.size)
    }

    @Test
    fun `parses button with outlined variant`() {
        val json = """{"type":"button","label":"Cancel","variant":"outlined"}"""
        val message = "```kai-ui\n$json\n```"
        val segments = KaiUiParser.parse(message)
        val node = (segments[0] as KaiUiParser.UiSegment).node
        assertIs<ButtonNode>(node)
        assertEquals(ButtonVariant.OUTLINED, node.variant)
    }

    @Test
    fun `parses button with text variant`() {
        val json = """{"type":"button","label":"Skip","variant":"text"}"""
        val message = "```kai-ui\n$json\n```"
        val segments = KaiUiParser.parse(message)
        val node = (segments[0] as KaiUiParser.UiSegment).node
        assertIs<ButtonNode>(node)
        assertEquals(ButtonVariant.TEXT, node.variant)
    }

    @Test
    fun `parses button with tonal variant`() {
        val json = """{"type":"button","label":"Maybe","variant":"tonal"}"""
        val message = "```kai-ui\n$json\n```"
        val segments = KaiUiParser.parse(message)
        val node = (segments[0] as KaiUiParser.UiSegment).node
        assertIs<ButtonNode>(node)
        assertEquals(ButtonVariant.TONAL, node.variant)
    }

    @Test
    fun `parses countdown node`() {
        val json = """{"type":"countdown","seconds":300,"label":"Time left"}"""
        val message = "```kai-ui\n$json\n```"
        val segments = KaiUiParser.parse(message)
        val node = (segments[0] as KaiUiParser.UiSegment).node
        assertIs<CountdownNode>(node)
        assertEquals(300, node.seconds)
        assertEquals("Time left", node.label)
        assertEquals(null, node.action)
    }

    @Test
    fun `parses countdown node with action`() {
        val json = """{"type":"countdown","seconds":60,"label":"Hurry!","action":{"type":"callback","event":"timer_done"}}"""
        val message = "```kai-ui\n$json\n```"
        val segments = KaiUiParser.parse(message)
        val node = (segments[0] as KaiUiParser.UiSegment).node
        assertIs<CountdownNode>(node)
        assertEquals(60, node.seconds)
        assertIs<CallbackAction>(node.action)
        assertEquals("timer_done", (node.action as CallbackAction).event)
    }

    @Test
    fun `parses quote node`() {
        val json = """{"type":"quote","text":"Be the change.","source":"Gandhi"}"""
        val message = "```kai-ui\n$json\n```"
        val segments = KaiUiParser.parse(message)
        val node = (segments[0] as KaiUiParser.UiSegment).node
        assertIs<QuoteNode>(node)
        assertEquals("Be the change.", node.text)
        assertEquals("Gandhi", node.source)
    }

    @Test
    fun `parses quote node without source`() {
        val json = """{"type":"quote","text":"Hello world"}"""
        val message = "```kai-ui\n$json\n```"
        val segments = KaiUiParser.parse(message)
        val node = (segments[0] as KaiUiParser.UiSegment).node
        assertIs<QuoteNode>(node)
        assertEquals("Hello world", node.text)
        assertNull(node.source)
    }

    @Test
    fun `parses badge node`() {
        val json = """{"type":"badge","value":"3","color":"error"}"""
        val message = "```kai-ui\n$json\n```"
        val segments = KaiUiParser.parse(message)
        val node = (segments[0] as KaiUiParser.UiSegment).node
        assertIs<BadgeNode>(node)
        assertEquals("3", node.value)
        assertEquals("error", node.color)
    }

    @Test
    fun `parses stat node`() {
        val json = """{"type":"stat","value":"$1,234","label":"Revenue","description":"12% increase"}"""
        val message = "```kai-ui\n$json\n```"
        val segments = KaiUiParser.parse(message)
        val node = (segments[0] as KaiUiParser.UiSegment).node
        assertIs<StatNode>(node)
        assertEquals("$1,234", node.value)
        assertEquals("Revenue", node.label)
        assertEquals("12% increase", node.description)
    }

    @Test
    fun `parses avatar node`() {
        val json = """{"type":"avatar","name":"John Doe","size":48}"""
        val message = "```kai-ui\n$json\n```"
        val segments = KaiUiParser.parse(message)
        val node = (segments[0] as KaiUiParser.UiSegment).node
        assertIs<AvatarNode>(node)
        assertEquals("John Doe", node.name)
        assertNull(node.imageUrl)
        assertEquals(48, node.size)
    }

    @Test
    fun `parses avatar node with image url`() {
        val json = """{"type":"avatar","name":"Jane","imageUrl":"https://example.com/photo.jpg"}"""
        val message = "```kai-ui\n$json\n```"
        val segments = KaiUiParser.parse(message)
        val node = (segments[0] as KaiUiParser.UiSegment).node
        assertIs<AvatarNode>(node)
        assertEquals("Jane", node.name)
        assertEquals("https://example.com/photo.jpg", node.imageUrl)
    }

    @Test
    fun `infers text node from object with text field but no type`() {
        val json = """{"type":"column","children":[{"text":"Hello","icon":"check"}]}"""
        val message = "```kai-ui\n$json\n```"
        val segments = KaiUiParser.parse(message)
        val node = (segments[0] as KaiUiParser.UiSegment).node
        assertIs<ColumnNode>(node)
        val child = node.children[0]
        assertIs<TextNode>(child)
        assertEquals("Hello", child.value)
    }

    @Test
    fun `infers column from object with title and subtitle but no type`() {
        val json = """{"type":"column","children":[{"title":"My Title","subtitle":"My Subtitle","icon":"settings"}]}"""
        val message = "```kai-ui\n$json\n```"
        val segments = KaiUiParser.parse(message)
        val node = (segments[0] as KaiUiParser.UiSegment).node
        assertIs<ColumnNode>(node)
        val child = node.children[0]
        assertIs<ColumnNode>(child)
        assertEquals(2, child.children.size)
        val title = child.children[0]
        assertIs<TextNode>(title)
        assertEquals("My Title", title.value)
        val subtitle = child.children[1]
        assertIs<TextNode>(subtitle)
        assertEquals("My Subtitle", subtitle.value)
    }

    @Test
    fun `infers text node from object with title only but no type`() {
        val json = """{"type":"column","children":[{"title":"A Title"}]}"""
        val message = "```kai-ui\n$json\n```"
        val segments = KaiUiParser.parse(message)
        val node = (segments[0] as KaiUiParser.UiSegment).node
        assertIs<ColumnNode>(node)
        val child = node.children[0]
        assertIs<TextNode>(child)
        assertEquals("A Title", child.value)
    }

    @Test
    fun `infers text node from object with label but no type`() {
        val json = """{"type":"column","children":[{"label":"Click me"}]}"""
        val message = "```kai-ui\n$json\n```"
        val segments = KaiUiParser.parse(message)
        val node = (segments[0] as KaiUiParser.UiSegment).node
        assertIs<ColumnNode>(node)
        val child = node.children[0]
        assertIs<TextNode>(child)
        assertEquals("Click me", child.value)
    }

    // --- Truncated JSON recovery tests ---

    @Test
    fun `recovers truncated JSON mid-value in nested object`() {
        // Simulates LLM response cut off inside a deeply nested structure
        val json = """{"type":"column","children":[{"type":"text","value":"Complete"},{"type":"text","value":"Trun"""
        val message = "```kai-ui\n$json\n```"
        val segments = KaiUiParser.parse(message)
        assertEquals(1, segments.size)
        val node = (segments[0] as KaiUiParser.UiSegment).node
        assertIs<ColumnNode>(node)
        // At least the first complete child should be recovered
        assertTrue(node.children.isNotEmpty())
        assertIs<TextNode>(node.children[0])
        assertEquals("Complete", (node.children[0] as TextNode).value)
    }

    @Test
    fun `recovers truncated JSON after comma`() {
        val json = """{"type":"column","children":[{"type":"text","value":"First"},"""
        val message = "```kai-ui\n$json\n```"
        val segments = KaiUiParser.parse(message)
        assertEquals(1, segments.size)
        val node = (segments[0] as KaiUiParser.UiSegment).node
        assertIs<ColumnNode>(node)
        assertEquals(1, node.children.size)
        assertIs<TextNode>(node.children[0])
        assertEquals("First", (node.children[0] as TextNode).value)
    }

    @Test
    fun `recovers truncated JSON mid-key`() {
        val json = """{"type":"column","children":[{"type":"text","value":"OK"}],"spa"""
        val message = "```kai-ui\n$json\n```"
        val segments = KaiUiParser.parse(message)
        assertEquals(1, segments.size)
        val node = (segments[0] as KaiUiParser.UiSegment).node
        assertIs<ColumnNode>(node)
        assertEquals(1, node.children.size)
    }

    @Test
    fun `flattens array value to string when primitive expected`() {
        val json = """{"type":"text","value":["line one","line two"]}"""
        val message = "```kai-ui\n$json\n```"
        val segments = KaiUiParser.parse(message)
        assertEquals(1, segments.size)
        val node = (segments[0] as KaiUiParser.UiSegment).node
        assertIs<TextNode>(node)
        assertEquals("line one, line two", node.value)
    }

    @Test
    fun `parses list items with content field instead of type`() {
        val json = """{"type":"list","items":[{"content":"First item"},{"content":"Second item"}]}"""
        val message = "```kai-ui\n$json\n```"
        val segments = KaiUiParser.parse(message)
        assertEquals(1, segments.size)
        val node = (segments[0] as KaiUiParser.UiSegment).node
        assertIs<ListNode>(node)
        assertEquals(2, node.items.size)
        val first = node.items[0]
        assertIs<TextNode>(first)
        assertEquals("First item", first.value)
        val second = node.items[1]
        assertIs<TextNode>(second)
        assertEquals("Second item", second.value)
    }

    @Test
    fun `detects kai-ui as plain text followed by json code block`() {
        val message = "kai-ui\n```json\n{\"type\":\"text\",\"value\":\"Hello\"}\n```"
        assertTrue(KaiUiParser.containsUiBlocks(message))
        val segments = KaiUiParser.parse(message)
        assertEquals(1, segments.size)
        assertIs<KaiUiParser.UiSegment>(segments[0])
        val node = (segments[0] as KaiUiParser.UiSegment).node
        assertIs<TextNode>(node)
        assertEquals("Hello", node.value)
    }

    @Test
    fun `detects kai-ui as plain text followed by untagged code block`() {
        val message = "kai-ui\n```\n{\"type\":\"text\",\"value\":\"Hello\"}\n```"
        assertTrue(KaiUiParser.containsUiBlocks(message))
        val segments = KaiUiParser.parse(message)
        assertEquals(1, segments.size)
        assertIs<KaiUiParser.UiSegment>(segments[0])
    }

    @Test
    fun `strips split kai-ui blocks`() {
        val message = "Before\nkai-ui\n```json\n{\"type\":\"text\",\"value\":\"Hi\"}\n```\nAfter"
        val stripped = KaiUiParser.stripUiBlocks(message)
        assertFalse(stripped.contains("kai-ui"))
        assertFalse(stripped.contains("\"type\""))
        assertTrue(stripped.contains("Before"))
        assertTrue(stripped.contains("After"))
    }
}
