package com.inspiredandroid.kai.network.dtos.openaicompatible

import com.inspiredandroid.kai.network.tools.ParameterSchema
import com.inspiredandroid.kai.network.tools.Tool
import com.inspiredandroid.kai.network.tools.ToolSchema
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InlineToolCallParserTest {

    private val execShell = stubTool(
        name = "execute_shell_command",
        parameters = mapOf(
            "command" to "string",
            "timeout" to "integer",
        ),
    )

    @Test
    fun `passes through text without any tool_call`() {
        val extracted = extractInlineToolCalls("just a regular message", listOf(execShell))
        assertEquals("just a regular message", extracted.cleanedText)
        assertTrue(extracted.calls.isEmpty())
    }

    @Test
    fun `parses hermes style xml block and coerces integer timeout`() {
        val raw = """
            下载链接中的空格需要 URL 编码。让我用正确格式重新下载:
            <tool_call>
            <function=execute_shell_command>
            <parameter=command>
            ssh hk-server "echo hi"
            </parameter>
            <parameter=timeout>
            180
            </parameter>
            </function>
            </tool_call>
        """.trimIndent()

        val extracted = extractInlineToolCalls(raw, listOf(execShell))

        assertEquals(1, extracted.calls.size)
        val call = extracted.calls[0]
        assertEquals("execute_shell_command", call.name)
        // timeout coerced to a JSON number; command stays a string
        assertTrue(call.arguments.contains("\"timeout\":180"), "expected unquoted integer, got: ${call.arguments}")
        assertTrue(call.arguments.contains("\"command\":\"ssh hk-server \\\"echo hi\\\"\""), "command not preserved: ${call.arguments}")
        // surrounding prose preserved, xml stripped
        assertEquals("下载链接中的空格需要 URL 编码。让我用正确格式重新下载:", extracted.cleanedText)
    }

    @Test
    fun `parses json flavor with arguments field`() {
        val raw = """before<tool_call>{"name":"execute_shell_command","arguments":{"command":"ls","timeout":5}}</tool_call>after"""

        val extracted = extractInlineToolCalls(raw, listOf(execShell))

        assertEquals(1, extracted.calls.size)
        val call = extracted.calls[0]
        assertEquals("execute_shell_command", call.name)
        assertTrue(call.arguments.contains("\"command\":\"ls\""))
        assertTrue(call.arguments.contains("\"timeout\":5"))
        assertEquals("beforeafter", extracted.cleanedText)
    }

    @Test
    fun `keeps unparseable block in cleaned text rather than dropping it`() {
        val raw = "intro <tool_call>not xml and not json</tool_call> tail"
        val extracted = extractInlineToolCalls(raw, listOf(execShell))
        assertTrue(extracted.calls.isEmpty())
        assertTrue(
            extracted.cleanedText.contains("<tool_call>"),
            "unparseable block should remain visible: ${extracted.cleanedText}",
        )
    }

    @Test
    fun `handles multiple inline tool calls in one message`() {
        val raw = """
            <tool_call>
            <function=execute_shell_command>
            <parameter=command>echo one</parameter>
            </function>
            </tool_call>
            mid
            <tool_call>
            <function=execute_shell_command>
            <parameter=command>echo two</parameter>
            </function>
            </tool_call>
        """.trimIndent()

        val extracted = extractInlineToolCalls(raw, listOf(execShell))

        assertEquals(2, extracted.calls.size)
        assertEquals("mid", extracted.cleanedText)
    }

    @Test
    fun `unknown tool falls back to string parameters`() {
        val raw = """<tool_call><function=mystery_tool><parameter=count>42</parameter></function></tool_call>"""
        val extracted = extractInlineToolCalls(raw, tools = emptyList())
        assertEquals(1, extracted.calls.size)
        // without a schema we can't know the type, so it stays a string
        assertTrue(extracted.calls[0].arguments.contains("\"count\":\"42\""))
    }

    private fun stubTool(name: String, parameters: Map<String, String>): Tool = object : Tool {
        override val schema = ToolSchema(
            name = name,
            description = "",
            parameters = parameters.mapValues { (_, type) ->
                ParameterSchema(type = type, description = "", required = false)
            },
        )

        override suspend fun execute(args: Map<String, Any>): Any = ""
    }
}
