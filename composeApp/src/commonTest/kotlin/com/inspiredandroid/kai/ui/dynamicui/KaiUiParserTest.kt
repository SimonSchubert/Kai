package com.inspiredandroid.kai.ui.dynamicui

import com.inspiredandroid.kai.ui.markdown.KaiUiBlock
import com.inspiredandroid.kai.ui.markdown.KaiUiError
import com.inspiredandroid.kai.ui.markdown.Paragraph
import com.inspiredandroid.kai.ui.markdown.parseMarkdown
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KaiUiParserTest {

    private fun hasUiBlocks(message: String): Boolean = parseMarkdown(message).blocks.any { it is KaiUiBlock || it is KaiUiError }

    private fun parseUi(json: String): KaiUiNode {
        val result = KaiUiParser.parseUiBlockBody(json)
        val ui = assertIs<KaiUiParser.UiBlockResult.Ui>(result)
        return ui.node
    }

    @Test
    fun `detects kai-ui blocks`() {
        val message = "Hello\n```kai-ui\n{\"type\":\"text\",\"value\":\"Hi\"}\n```\nBye"
        assertTrue(hasUiBlocks(message))
    }

    @Test
    fun `no false positive on regular code blocks`() {
        val message = "```kotlin\nval x = 1\n```"
        assertFalse(hasUiBlocks(message))
    }

    @Test
    fun `parses text node`() {
        val message = "Before\n```kai-ui\n{\"type\":\"text\",\"value\":\"Hello\"}\n```\nAfter"
        val blocks = parseMarkdown(message).blocks
        assertEquals(3, blocks.size)
        assertIs<Paragraph>(blocks[0])
        assertIs<KaiUiBlock>(blocks[1])
        assertIs<Paragraph>(blocks[2])

        val uiBlock = blocks[1] as KaiUiBlock
        val textNode = assertIs<TextNode>(uiBlock.node)
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
        val node = assertIs<ColumnNode>(parseUi(json))
        assertEquals(2, node.children.size)
        assertIs<TextNode>(node.children[0])
        assertIs<ButtonNode>(node.children[1])
    }

    @Test
    fun `callback action with collectFrom`() {
        val json = """{"type":"button","label":"Submit","action":{"type":"callback","event":"submit","collectFrom":["name","email"]}}"""
        val button = assertIs<ButtonNode>(parseUi(json))
        val action = button.action as CallbackAction
        assertEquals("submit", action.event)
        assertEquals(listOf("name", "email"), action.collectFrom)
    }

    @Test
    fun `invalid json produces error segment`() {
        val message = "```kai-ui\n{invalid json}\n```"
        val blocks = parseMarkdown(message).blocks
        assertEquals(1, blocks.size)
        assertIs<KaiUiError>(blocks[0])
    }

    @Test
    fun `multiple ui blocks in one message`() {
        val message = "First block:\n```kai-ui\n{\"type\":\"text\",\"value\":\"A\"}\n```\nMiddle text\n```kai-ui\n{\"type\":\"text\",\"value\":\"B\"}\n```\nEnd"
        val blocks = parseMarkdown(message).blocks
        assertEquals(5, blocks.size)
        assertIs<Paragraph>(blocks[0])
        assertIs<KaiUiBlock>(blocks[1])
        assertIs<Paragraph>(blocks[2])
        assertIs<KaiUiBlock>(blocks[3])
        assertIs<Paragraph>(blocks[4])
    }

    @Test
    fun `interactive nodes require id`() {
        val json = """{"type":"text_input","id":"name","label":"Name"}"""
        val node = assertIs<TextInputNode>(parseUi(json))
        assertEquals("name", node.id)
        assertEquals("Name", node.label)
    }

    @Test
    fun `select node with options`() {
        val json = """{"type":"select","id":"color","label":"Color","options":["Red","Blue","Green"],"selected":"Blue"}"""
        val node = assertIs<SelectNode>(parseUi(json))
        assertEquals(listOf("Red", "Blue", "Green"), node.options)
        assertEquals("Blue", node.selected)
    }

    @Test
    fun `toggle action`() {
        val json = """{"type":"button","label":"Toggle","action":{"type":"toggle","targetId":"details"}}"""
        val button = assertIs<ButtonNode>(parseUi(json))
        val toggle = assertIs<ToggleAction>(button.action)
        assertEquals("details", toggle.targetId)
    }

    @Test
    fun `open url action`() {
        val json = """{"type":"button","label":"Visit","action":{"type":"open_url","url":"https://example.com"}}"""
        val button = assertIs<ButtonNode>(parseUi(json))
        val openUrl = assertIs<OpenUrlAction>(button.action)
        assertEquals("https://example.com", openUrl.url)
    }

    @Test
    fun `handles extra trailing braces from LLM`() {
        val json = """{"type":"text","value":"Hi"}}"""
        val node = assertIs<TextNode>(parseUi(json))
        assertEquals("Hi", node.value)
    }

    @Test
    fun `handles multiple extra trailing braces`() {
        val json = """{"type":"column","children":[{"type":"text","value":"A"}]}}}"""
        assertIs<ColumnNode>(parseUi(json))
    }

    @Test
    fun `parses kai-ui block when body and closing fence share line with newline before body`() {
        // The markdown parser requires the opening fence to end the line; the body must start on
        // the next line. This is a well-formed CommonMark fenced block.
        val message = "```kai-ui\n{\"type\":\"text\",\"value\":\"Hi\"}\n```"
        assertTrue(hasUiBlocks(message))
        val blocks = parseMarkdown(message).blocks
        assertEquals(1, blocks.size)
        val uiBlock = assertIs<KaiUiBlock>(blocks[0])
        assertIs<TextNode>(uiBlock.node)
    }

    @Test
    fun `parses multi-line JSON objects as column`() {
        val block = """
            {"type":"text","value":"Question 1 of 3","style":"caption"}
            {"type":"text","value":"Complete the sequence:","style":"body","bold":true}
            {"type":"text","value":"2, 6, 12, 20, 30, ?","style":"title"}
            {"type":"column","children":[{"type":"button","label":"42","action":{"type":"callback","event":"answer","data":{"answer":"42"}},"variant":"filled"}]}
        """.trimIndent()
        val message = "Here's a quiz:\n```kai-ui\n$block\n```\nGood luck!"
        val blocks = parseMarkdown(message).blocks
        assertEquals(3, blocks.size)
        assertIs<Paragraph>(blocks[0])
        assertIs<KaiUiBlock>(blocks[1])
        assertIs<Paragraph>(blocks[2])

        val column = (blocks[1] as KaiUiBlock).node
        assertIs<ColumnNode>(column)
        assertEquals(4, column.children.size)
        assertIs<TextNode>(column.children[0])
        assertIs<TextNode>(column.children[1])
        assertIs<TextNode>(column.children[2])
        assertIs<ColumnNode>(column.children[3])
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
        val column = assertIs<ColumnNode>(parseUi(block))
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
            {"type":"column","children":[{"type":"button","label":"38","action":{"type":"callback","event":"answer_q1","data":{"answer":"38"}},"variant":"filled"},{"type":"button","label":"40","action":{"type":"callback","event":"answer_q1","data":{"answer":"40"}},"variant":"filled"},{"type":"button","label":"42","action":{"type":"callback","event":"answer_q1","data":{"answer":"42"}},"variant":"filled"},{"type":"button","label":"44","action":{"type":"callback","event":"answer_q1","data":{"answer":"44"}},"variant":"filled"}}]}
        """.trimIndent()
        val message = "Sure! Let's see how sharp you are today.\n\n```kai-ui\n$block\n```\n\nTake your shot!"
        val blocks = parseMarkdown(message).blocks
        val uiBlocks = blocks.filterIsInstance<KaiUiBlock>()
        assertEquals(1, uiBlocks.size)

        val column = uiBlocks[0].node
        assertIs<ColumnNode>(column)
        // sanitizeJson repairs the extra } so all 4 lines parse including buttons
        assertEquals(4, column.children.size)
        assertIs<TextNode>(column.children[0])
        assertEquals("Question 1 of 3", (column.children[0] as TextNode).value)
        assertIs<TextNode>(column.children[1])
        assertIs<TextNode>(column.children[2])
        val buttonsColumn = assertIs<ColumnNode>(column.children[3])
        assertEquals(4, buttonsColumn.children.size)
        assertIs<ButtonNode>(buttonsColumn.children[0])
    }

    @Test
    fun `legacy spacer nodes and spacing fields are silently dropped`() {
        // Back-compat: old assistant messages may contain spacer children and
        // spacing/padding properties on containers. The parser must still render them,
        // treating spacer as no-op and ignoring the removed fields.
        val json = """{"type":"column","spacing":16,"padding":8,"children":[
            {"type":"text","value":"Before"},
            {"type":"spacer","size":16},
            {"type":"text","value":"After"},
            {"type":"card","padding":12,"children":[
                {"type":"text","value":"Inside"},
                {"type":"spacer","height":8}
            ]}
        ]}"""
        val column = assertIs<ColumnNode>(parseUi(json))
        assertEquals(3, column.children.size)
        assertIs<TextNode>(column.children[0])
        assertIs<TextNode>(column.children[1])
        val card = assertIs<CardNode>(column.children[2])
        assertEquals(1, card.children.size)
        assertIs<TextNode>(card.children[0])
    }

    @Test
    fun `single-line column with extra closing brace in children`() {
        // Real-world kimi-k2.5 output: single column with buttons, extra } before ]}
        val json = """{"type":"column","children":[{"type":"button","label":"All roses fade quickly","action":{"type":"callback","event":"answer_q2","data":{"answer":"all"}},"variant":"filled"},{"type":"button","label":"Some roses fade quickly","action":{"type":"callback","event":"answer_q2","data":{"answer":"some"}},"variant":"filled"},{"type":"button","label":"No roses fade quickly","action":{"type":"callback","event":"answer_q2","data":{"answer":"none"}},"variant":"filled"},{"type":"button","label":"None of these follow","action":{"type":"callback","event":"answer_q2","data":{"answer":"none_follow"}},"variant":"filled"}}]}"""
        val message = "Which statement is necessarily true?\n\n```kai-ui\n$json\n```"
        val blocks = parseMarkdown(message).blocks
        val uiBlocks = blocks.filterIsInstance<KaiUiBlock>()
        assertEquals(1, uiBlocks.size)

        val column = uiBlocks[0].node
        assertIs<ColumnNode>(column)
        assertEquals(4, column.children.size)
        for (child in column.children) {
            assertIs<ButtonNode>(child)
        }
        assertEquals("All roses fade quickly", (column.children[0] as ButtonNode).label)
        assertEquals("None of these follow", (column.children[3] as ButtonNode).label)
    }

    @Test
    fun `closes object before next array element when LLM omits brace`() {
        // Real-world broken output: each button is missing its closing `}` before the
        // comma that separates it from the next button in the array. The first failure
        // is at offset 139 — `,{` inside button1 where a key is expected.
        val json = """{"type":"row","children":[{"type":"button","label":"Au","action":{"type":"callback","event":"answer","data":{"question":1,"answer":"Au"}},{"type":"button","label":"Ag","action":{"type":"callback","event":"answer","data":{"question":1,"answer":"Ag"}},{"type":"button","label":"Fe","action":{"type":"callback","event":"answer","data":{"question":1,"answer":"Fe"}}}}}]}"""
        val row = assertIs<RowNode>(parseUi(json))
        assertEquals(3, row.children.size)
        assertEquals("Au", (row.children[0] as ButtonNode).label)
        assertEquals("Ag", (row.children[1] as ButtonNode).label)
        assertEquals("Fe", (row.children[2] as ButtonNode).label)
    }

    @Test
    fun `extra closing bracket inside nested structure is skipped`() {
        // Extra ] where } is expected — sanitizeJson should skip it
        val json = """{"type":"column","children":[{"type":"text","value":"A"},{"type":"text","value":"B"}]]}"""
        val column = assertIs<ColumnNode>(parseUi(json))
        assertEquals(2, column.children.size)
    }

    @Test
    fun `callback data with non-string values`() {
        // LLMs sometimes send booleans or numbers in the data map instead of strings
        val json = """{"type":"button","label":"Continue","action":{"type":"callback","event":"continue","data":{"continue":true,"count":42,"name":"test"}}}"""
        val button = assertIs<ButtonNode>(parseUi(json))
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
        val column = assertIs<ColumnNode>(parseUi(json))
        assertEquals(1, column.children.size)
        assertIs<TextNode>(column.children[0])
    }

    @Test
    fun `parses complex nested kai-ui from kimi model`() {
        val json = """{"type":"column","children":[{"type":"text","value":"Wilderness Survival","style":"headline","bold":true},{"type":"text","value":"You wake up in a cold pine forest.","style":"body"},{"type":"divider"},{"type":"text","value":"Status","style":"title"},{"type":"row","children":[{"type":"card","children":[{"type":"text","value":"Health: 80/100","style":"body"}]},{"type":"card","children":[{"type":"text","value":"Hunger: 30/100","style":"body"}]},{"type":"card","children":[{"type":"text","value":"Energy: 70/100","style":"body"}]}]},{"type":"text","value":"What do you want to do?","style":"title"},{"type":"column","children":[{"type":"button","label":"Follow river","action":{"type":"callback","event":"survival_choice","data":{"choice":"river"}},"variant":"filled"},{"type":"button","label":"Head to mountains","action":{"type":"callback","event":"survival_choice","data":{"choice":"mountains"}},"variant":"filled"},{"type":"button","label":"Stay & build camp here","action":{"type":"callback","event":"survival_choice","data":{"choice":"camp"}},"variant":"filled"}]}]}"""
        val message = "```kai-ui\n$json\n```\n\nType \"stop game\" anytime to quit."
        assertTrue(hasUiBlocks(message))
        val blocks = parseMarkdown(message).blocks
        val uiBlocks = blocks.filterIsInstance<KaiUiBlock>()
        assertEquals(1, uiBlocks.size)
        val column = uiBlocks[0].node
        assertIs<ColumnNode>(column)
        assertEquals(7, column.children.size)
    }

    // --- Tests for new node types ---

    @Test
    fun `parses switch node`() {
        val json = """{"type":"switch","id":"dark_mode","label":"Dark Mode","checked":true}"""
        val node = assertIs<SwitchNode>(parseUi(json))
        assertEquals("dark_mode", node.id)
        assertEquals("Dark Mode", node.label)
        assertEquals(true, node.checked)
    }

    @Test
    fun `parses slider node`() {
        val json = """{"type":"slider","id":"volume","label":"Volume","value":75,"min":0,"max":100,"step":5}"""
        val node = assertIs<SliderNode>(parseUi(json))
        assertEquals("volume", node.id)
        assertEquals(75f, node.value)
        assertEquals(0f, node.min)
        assertEquals(100f, node.max)
        assertEquals(5f, node.step)
    }

    @Test
    fun `parses radio_group node`() {
        val json = """{"type":"radio_group","id":"size","label":"Size","options":["S","M","L","XL"],"selected":"M"}"""
        val node = assertIs<RadioGroupNode>(parseUi(json))
        assertEquals(listOf("S", "M", "L", "XL"), node.options)
        assertEquals("M", node.selected)
    }

    @Test
    fun `parses progress node determinate`() {
        val json = """{"type":"progress","value":0.7,"label":"Uploading..."}"""
        val node = assertIs<ProgressNode>(parseUi(json))
        assertEquals(0.7f, node.value)
        assertEquals("Uploading...", node.label)
    }

    @Test
    fun `parses progress node indeterminate`() {
        val json = """{"type":"progress","label":"Loading..."}"""
        val node = assertIs<ProgressNode>(parseUi(json))
        assertEquals(null, node.value)
    }

    @Test
    fun `parses alert node`() {
        val json = """{"type":"alert","message":"File saved successfully","title":"Success","severity":"success"}"""
        val node = assertIs<AlertNode>(parseUi(json))
        assertEquals("File saved successfully", node.message)
        assertEquals("Success", node.title)
        assertEquals(AlertSeverity.SUCCESS, node.severity)
    }

    @Test
    fun `parses chip_group node`() {
        val json = """{"type":"chip_group","id":"tags","chips":[{"label":"Kotlin","value":"kotlin"},{"label":"Java","value":"java"}],"selection":"multi"}"""
        val node = assertIs<ChipGroupNode>(parseUi(json))
        assertEquals(2, node.chips.size)
        assertEquals("Kotlin", node.chips[0].label)
        assertEquals("kotlin", node.chips[0].value)
        assertEquals("multi", node.selection)
    }

    @Test
    fun `migrates legacy multiSelect to selection`() {
        // Legacy kai-ui blocks in historical chat messages used multiSelect:Boolean.
        val multiJson = """{"type":"chip_group","id":"tags","chips":[{"label":"A"}],"multiSelect":true}"""
        val multiNode = assertIs<ChipGroupNode>(parseUi(multiJson))
        assertEquals("multi", multiNode.selection)

        val singleJson = """{"type":"chip_group","id":"tags","chips":[{"label":"A"}],"multiSelect":false}"""
        val singleNode = assertIs<ChipGroupNode>(parseUi(singleJson))
        assertEquals("single", singleNode.selection)
    }

    @Test
    fun `parses icon node`() {
        val json = """{"type":"icon","name":"star","size":32,"color":"primary"}"""
        val node = assertIs<IconNode>(parseUi(json))
        assertEquals("star", node.name)
        assertEquals(32, node.size)
        assertEquals("primary", node.color)
    }

    @Test
    fun `parses code node`() {
        val json = """{"type":"code","code":"fun main() { println(\"Hello\") }","language":"kotlin"}"""
        val node = assertIs<CodeNode>(parseUi(json))
        assertEquals("fun main() { println(\"Hello\") }", node.code)
        assertEquals("kotlin", node.language)
    }

    @Test
    fun `parses tabs node`() {
        val json = """{"type":"tabs","tabs":[{"label":"Tab 1","children":[{"type":"text","value":"Content 1"}]},{"label":"Tab 2","children":[{"type":"text","value":"Content 2"}]}],"selectedIndex":0}"""
        val node = assertIs<TabsNode>(parseUi(json))
        assertEquals(2, node.tabs.size)
        assertEquals("Tab 1", node.tabs[0].label)
        assertEquals(1, node.tabs[0].children.size)
        assertIs<TextNode>(node.tabs[0].children[0])
    }

    @Test
    fun `parses accordion node`() {
        val json = """{"type":"accordion","title":"More details","children":[{"type":"text","value":"Hidden content"}],"expanded":false}"""
        val node = assertIs<AccordionNode>(parseUi(json))
        assertEquals("More details", node.title)
        assertEquals(false, node.expanded)
        assertEquals(1, node.children.size)
    }

    @Test
    fun `unknown top-level node type produces error block`() {
        // Well-formed JSON but the type isn't one we render — shows up as a KaiUiError so the
        // renderer can at least fall back to displaying the raw JSON instead of silently
        // swallowing the assistant's response.
        val json = """{"type":"bottom_bar","buttons":[{"label":"Home","icon":"home"}]}"""
        val message = "```kai-ui\n$json\n```"
        val blocks = parseMarkdown(message).blocks
        assertEquals(1, blocks.size)
        assertIs<KaiUiError>(blocks[0])
    }

    @Test
    fun `strips unknown child node from children`() {
        val json = """{"type":"column","children":[{"type":"text","value":"Keep"},{"type":"bottom_bar","buttons":[]}]}"""
        val node = assertIs<ColumnNode>(parseUi(json))
        assertEquals(1, node.children.size)
        assertIs<TextNode>(node.children[0])
    }

    @Test
    fun `parses box node`() {
        val json = """{"type":"box","children":[{"type":"text","value":"Centered"}],"contentAlignment":"center"}"""
        val node = assertIs<BoxNode>(parseUi(json))
        assertEquals("center", node.contentAlignment)
        assertEquals(1, node.children.size)
    }

    @Test
    fun `parses button with outlined variant`() {
        val json = """{"type":"button","label":"Cancel","variant":"outlined"}"""
        val node = assertIs<ButtonNode>(parseUi(json))
        assertEquals(ButtonVariant.OUTLINED, node.variant)
    }

    @Test
    fun `parses button with text variant`() {
        val json = """{"type":"button","label":"Skip","variant":"text"}"""
        val node = assertIs<ButtonNode>(parseUi(json))
        assertEquals(ButtonVariant.TEXT, node.variant)
    }

    @Test
    fun `parses button with tonal variant`() {
        val json = """{"type":"button","label":"Maybe","variant":"tonal"}"""
        val node = assertIs<ButtonNode>(parseUi(json))
        assertEquals(ButtonVariant.TONAL, node.variant)
    }

    @Test
    fun `parses countdown node`() {
        val json = """{"type":"countdown","seconds":300,"label":"Time left"}"""
        val node = assertIs<CountdownNode>(parseUi(json))
        assertEquals(300, node.seconds)
        assertEquals("Time left", node.label)
        assertEquals(null, node.action)
    }

    @Test
    fun `parses countdown node with action`() {
        val json = """{"type":"countdown","seconds":60,"label":"Hurry!","action":{"type":"callback","event":"timer_done"}}"""
        val node = assertIs<CountdownNode>(parseUi(json))
        assertEquals(60, node.seconds)
        val action = assertIs<CallbackAction>(node.action)
        assertEquals("timer_done", action.event)
    }

    @Test
    fun `parses quote node`() {
        val json = """{"type":"quote","text":"Be the change.","source":"Gandhi"}"""
        val node = assertIs<QuoteNode>(parseUi(json))
        assertEquals("Be the change.", node.text)
        assertEquals("Gandhi", node.source)
    }

    @Test
    fun `parses quote node without source`() {
        val json = """{"type":"quote","text":"Hello world"}"""
        val node = assertIs<QuoteNode>(parseUi(json))
        assertEquals("Hello world", node.text)
        assertNull(node.source)
    }

    @Test
    fun `parses badge node`() {
        val json = """{"type":"badge","value":"3","color":"error"}"""
        val node = assertIs<BadgeNode>(parseUi(json))
        assertEquals("3", node.value)
        assertEquals("error", node.color)
    }

    @Test
    fun `parses stat node`() {
        val json = """{"type":"stat","value":"$1,234","label":"Revenue","description":"12% increase"}"""
        val node = assertIs<StatNode>(parseUi(json))
        assertEquals("$1,234", node.value)
        assertEquals("Revenue", node.label)
        assertEquals("12% increase", node.description)
    }

    @Test
    fun `parses avatar node`() {
        val json = """{"type":"avatar","name":"John Doe","size":48}"""
        val node = assertIs<AvatarNode>(parseUi(json))
        assertEquals("John Doe", node.name)
        assertNull(node.imageUrl)
        assertEquals(48, node.size)
    }

    @Test
    fun `parses avatar node with image url`() {
        val json = """{"type":"avatar","name":"Jane","imageUrl":"https://example.com/photo.jpg"}"""
        val node = assertIs<AvatarNode>(parseUi(json))
        assertEquals("Jane", node.name)
        assertEquals("https://example.com/photo.jpg", node.imageUrl)
    }

    @Test
    fun `infers text node from object with text field but no type`() {
        val json = """{"type":"column","children":[{"text":"Hello","icon":"check"}]}"""
        val node = assertIs<ColumnNode>(parseUi(json))
        val child = node.children[0]
        assertIs<TextNode>(child)
        assertEquals("Hello", child.value)
    }

    @Test
    fun `infers column from object with title and subtitle but no type`() {
        val json = """{"type":"column","children":[{"title":"My Title","subtitle":"My Subtitle","icon":"settings"}]}"""
        val node = assertIs<ColumnNode>(parseUi(json))
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
        val node = assertIs<ColumnNode>(parseUi(json))
        val child = node.children[0]
        assertIs<TextNode>(child)
        assertEquals("A Title", child.value)
    }

    @Test
    fun `infers text node from object with label but no type`() {
        val json = """{"type":"column","children":[{"label":"Click me"}]}"""
        val node = assertIs<ColumnNode>(parseUi(json))
        val child = node.children[0]
        assertIs<TextNode>(child)
        assertEquals("Click me", child.value)
    }

    // --- Truncated JSON recovery tests ---

    @Test
    fun `recovers truncated JSON mid-value in nested object`() {
        // Simulates LLM response cut off inside a deeply nested structure
        val json = """{"type":"column","children":[{"type":"text","value":"Complete"},{"type":"text","value":"Trun"""
        val node = assertIs<ColumnNode>(parseUi(json))
        // At least the first complete child should be recovered
        assertTrue(node.children.isNotEmpty())
        assertIs<TextNode>(node.children[0])
        assertEquals("Complete", (node.children[0] as TextNode).value)
    }

    @Test
    fun `recovers truncated JSON after comma`() {
        val json = """{"type":"column","children":[{"type":"text","value":"First"},"""
        val node = assertIs<ColumnNode>(parseUi(json))
        assertEquals(1, node.children.size)
        assertIs<TextNode>(node.children[0])
        assertEquals("First", (node.children[0] as TextNode).value)
    }

    @Test
    fun `recovers truncated JSON mid-key`() {
        val json = """{"type":"column","children":[{"type":"text","value":"OK"}],"spa"""
        val node = assertIs<ColumnNode>(parseUi(json))
        assertEquals(1, node.children.size)
    }

    @Test
    fun `flattens array value to string when primitive expected`() {
        val json = """{"type":"text","value":["line one","line two"]}"""
        val node = assertIs<TextNode>(parseUi(json))
        assertEquals("line one, line two", node.value)
    }

    @Test
    fun `parses list items with content field instead of type`() {
        val json = """{"type":"list","items":[{"content":"First item"},{"content":"Second item"}]}"""
        val node = assertIs<ListNode>(parseUi(json))
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
        assertTrue(hasUiBlocks(message))
        val blocks = parseMarkdown(message).blocks
        val uiBlocks = blocks.filterIsInstance<KaiUiBlock>()
        assertEquals(1, uiBlocks.size)
        val node = uiBlocks[0].node
        assertIs<TextNode>(node)
        assertEquals("Hello", node.value)
    }

    @Test
    fun `detects kai-ui as plain text followed by untagged code block`() {
        val message = "kai-ui\n```\n{\"type\":\"text\",\"value\":\"Hello\"}\n```"
        assertTrue(hasUiBlocks(message))
        val blocks = parseMarkdown(message).blocks
        val uiBlocks = blocks.filterIsInstance<KaiUiBlock>()
        assertEquals(1, uiBlocks.size)
    }

    // --- Shape coercion: LLMs sometimes put objects or wrong-shaped lists where the data
    // model expects a plain string or a List<String>. These regressions pin the behaviour of
    // fixMissingTypes so the parser recovers instead of crashing the whole block.

    @Test
    fun `coerces text value as object with nested text field`() {
        val text = assertIs<TextNode>(parseUi("""{"type":"text","value":{"text":"hello"}}"""))
        assertEquals("hello", text.value)
    }

    @Test
    fun `coerces button label as object with label field`() {
        val button = assertIs<ButtonNode>(parseUi("""{"type":"button","label":{"label":"Click me"},"action":{"type":"callback","event":"tap"}}"""))
        assertEquals("Click me", button.label)
    }

    @Test
    fun `coerces select options as list of objects`() {
        val select = assertIs<SelectNode>(parseUi("""{"type":"select","id":"pick","options":[{"label":"Alpha","value":"a"},{"label":"Beta","value":"b"}]}"""))
        assertEquals(listOf("a", "b"), select.options)
    }

    @Test
    fun `coerces radio group options as list of objects`() {
        val radio = assertIs<RadioGroupNode>(parseUi("""{"type":"radio_group","id":"pick","options":[{"label":"Yes","value":"y"},{"label":"No","value":"n"}]}"""))
        assertEquals(listOf("y", "n"), radio.options)
    }

    @Test
    fun `coerces table rows as list of objects`() {
        val table = assertIs<TableNode>(parseUi("""{"type":"table","headers":["col1","col2"],"rows":[{"col1":"1","col2":"2"},{"col1":"3","col2":"4"}]}"""))
        assertEquals(listOf("col1", "col2"), table.headers)
        assertEquals(listOf(listOf("1", "2"), listOf("3", "4")), table.rows)
    }

    @Test
    fun `coerces table headers as list of objects`() {
        val table = assertIs<TableNode>(parseUi("""{"type":"table","headers":[{"name":"Col A"},{"name":"Col B"}],"rows":[["a","b"]]}"""))
        assertEquals(listOf("Col A", "Col B"), table.headers)
        assertEquals(listOf(listOf("a", "b")), table.rows)
    }

    @Test
    fun `coerces chip group chips as bare strings`() {
        val chipGroup = assertIs<ChipGroupNode>(parseUi("""{"type":"chip_group","id":"tags","chips":["red","blue","green"]}"""))
        assertEquals(3, chipGroup.chips.size)
        assertEquals("red", chipGroup.chips[0].label)
        assertEquals("red", chipGroup.chips[0].value)
        assertEquals("green", chipGroup.chips[2].label)
    }

    @Test
    fun `coerces quote text as object`() {
        val quote = assertIs<QuoteNode>(parseUi("""{"type":"quote","text":{"content":"Be kind"},"source":"anon"}"""))
        assertEquals("Be kind", quote.text)
        assertEquals("anon", quote.source)
    }

    @Test
    fun `wraps untyped objects inside list items as text nodes`() {
        // Real-world kimi-k2.5 output: list items emitted as {"value":"..."} without a type field
        val list = assertIs<ListNode>(
            parseUi(
                """{"type":"list","items":[
                {"value":"AI employees as social creators"},
                {"value":"Nostalgia platforms comeback"},
                {"value":"Gut health micro-movements"}
            ]}""",
            ),
        )
        assertEquals(3, list.items.size)
        val first = assertIs<TextNode>(list.items[0])
        assertEquals("AI employees as social creators", first.value)
        val second = assertIs<TextNode>(list.items[1])
        assertEquals("Nostalgia platforms comeback", second.value)
        val third = assertIs<TextNode>(list.items[2])
        assertEquals("Gut health micro-movements", third.value)
    }

    @Test
    fun `wraps untyped objects inside children as text nodes`() {
        val column = assertIs<ColumnNode>(
            parseUi(
                """{"type":"column","children":[
                {"type":"text","value":"Header","style":"title"},
                {"text":"Untyped object with text field"},
                {"label":"Untyped object with label field"}
            ]}""",
            ),
        )
        assertEquals(3, column.children.size)
        assertEquals("Header", (column.children[0] as TextNode).value)
        assertEquals("Untyped object with text field", (column.children[1] as TextNode).value)
        assertEquals("Untyped object with label field", (column.children[2] as TextNode).value)
    }

    @Test
    fun `preserves style fields when wrapping untyped object in children`() {
        val column = assertIs<ColumnNode>(
            parseUi(
                """{"type":"column","children":[
                {"value":"Styled text","style":"title","bold":true,"color":"primary"}
            ]}""",
            ),
        )
        val text = assertIs<TextNode>(column.children.single())
        assertEquals("Styled text", text.value)
        assertEquals(TextNodeStyle.TITLE, text.style)
        assertEquals(true, text.bold)
        assertEquals("primary", text.color)
    }

    @Test
    fun `coerces bare string action to callback`() {
        val button = assertIs<ButtonNode>(
            parseUi("""{"type":"button","label":"Submit","action":"submit_form"}"""),
        )
        val callback = assertIs<CallbackAction>(button.action)
        assertEquals("submit_form", callback.event)
    }

    @Test
    fun `coerces untyped action object with event as callback`() {
        val button = assertIs<ButtonNode>(
            parseUi("""{"type":"button","label":"Go","action":{"event":"go_clicked","data":{"x":"1"}}}"""),
        )
        val callback = assertIs<CallbackAction>(button.action)
        assertEquals("go_clicked", callback.event)
    }

    @Test
    fun `coerces untyped action object with url as open_url`() {
        val button = assertIs<ButtonNode>(
            parseUi("""{"type":"button","label":"Docs","action":{"url":"https://example.com"}}"""),
        )
        val action = assertIs<OpenUrlAction>(button.action)
        assertEquals("https://example.com", action.url)
    }

    @Test
    fun `coerces untyped action object with targetId as toggle`() {
        val button = assertIs<ButtonNode>(
            parseUi("""{"type":"button","label":"Reveal","action":{"targetId":"hidden_box"}}"""),
        )
        val action = assertIs<ToggleAction>(button.action)
        assertEquals("hidden_box", action.targetId)
    }

    @Test
    fun `coerces chip label and value when sent as objects`() {
        val chipGroup = assertIs<ChipGroupNode>(
            parseUi(
                """{"type":"chip_group","id":"tags","chips":[
                {"label":{"text":"Kotlin"},"value":"kotlin"},
                {"label":"Rust","value":{"content":"rust"}}
            ]}""",
            ),
        )
        assertEquals(2, chipGroup.chips.size)
        assertEquals("Kotlin", chipGroup.chips[0].label)
        assertEquals("kotlin", chipGroup.chips[0].value)
        assertEquals("Rust", chipGroup.chips[1].label)
        assertEquals("rust", chipGroup.chips[1].value)
    }

    @Test
    fun `coerces tab label when sent as object`() {
        val tabs = assertIs<TabsNode>(
            parseUi(
                """{"type":"tabs","tabs":[
                {"label":{"title":"Overview"},"children":[{"type":"text","value":"a"}]},
                {"label":{"title":"Specs"},"children":[{"type":"text","value":"b"}]}
            ]}""",
            ),
        )
        assertEquals(2, tabs.tabs.size)
        assertEquals("Overview", tabs.tabs[0].label)
        assertEquals("Specs", tabs.tabs[1].label)
    }

    @Test
    fun `coerces string booleans for known boolean fields`() {
        val column = assertIs<ColumnNode>(
            parseUi(
                """{"type":"column","children":[
                {"type":"list","ordered":"true","items":[{"type":"text","value":"a"}]},
                {"type":"accordion","title":"More","expanded":"yes","children":[]},
                {"type":"checkbox","id":"c","label":"Agree","checked":"1"},
                {"type":"switch","id":"s","label":"Dark","checked":"false"}
            ]}""",
            ),
        )
        assertEquals(true, (column.children[0] as ListNode).ordered)
        assertEquals(true, (column.children[1] as AccordionNode).expanded)
        assertEquals(true, (column.children[2] as CheckboxNode).checked)
        assertEquals(false, (column.children[3] as SwitchNode).checked)
    }

    @Test
    fun `coerces text node bold and italic as strings`() {
        val text = assertIs<TextNode>(
            parseUi("""{"type":"text","value":"Hello","bold":"true","italic":"no"}"""),
        )
        assertEquals(true, text.bold)
        assertEquals(false, text.italic)
    }

    @Test
    fun `migrates image src to url`() {
        val image = assertIs<ImageNode>(
            parseUi("""{"type":"image","src":"https://example.com/a.jpg","alt":"photo"}"""),
        )
        assertEquals("https://example.com/a.jpg", image.url)
        assertEquals("photo", image.alt)
    }

    @Test
    fun `parses image with aspectRatio`() {
        val image = assertIs<ImageNode>(
            parseUi("""{"type":"image","url":"https://example.com/a.jpg","aspectRatio":1.78}"""),
        )
        assertEquals(1.78f, image.aspectRatio)
        assertNull(image.height)
    }

    @Test
    fun `parses image with snake_case aspect_ratio`() {
        val image = assertIs<ImageNode>(
            parseUi("""{"type":"image","url":"https://example.com/a.jpg","aspect_ratio":1.5}"""),
        )
        assertEquals(1.5f, image.aspectRatio)
    }

    @Test
    fun `parses image with integer aspectRatio`() {
        val image = assertIs<ImageNode>(
            parseUi("""{"type":"image","url":"https://example.com/a.jpg","aspectRatio":2}"""),
        )
        assertEquals(2.0f, image.aspectRatio)
    }

    @Test
    fun `legacy image without aspectRatio has null`() {
        val image = assertIs<ImageNode>(
            parseUi("""{"type":"image","url":"https://example.com/a.jpg","height":160}"""),
        )
        assertEquals(160, image.height)
        assertNull(image.aspectRatio)
    }

    @Test
    fun `coerces nested objects in CallbackAction data to primitives`() {
        // Covered indirectly by the "non-composite + JsonObject → primitive" rule inside
        // the data object. Verifies recursion walks into data correctly.
        val button = assertIs<ButtonNode>(
            parseUi(
                """{"type":"button","label":"Submit","action":{"type":"callback","event":"submit",
                "data":{"user":{"name":"alice"},"tags":["a","b"]}}}""",
            ),
        )
        val callback = assertIs<CallbackAction>(button.action)
        val data = callback.data!!
        assertEquals("alice", data["user"]?.content)
        assertEquals("a, b", data["tags"]?.content)
    }
}
