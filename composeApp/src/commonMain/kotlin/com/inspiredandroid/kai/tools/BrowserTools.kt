package com.inspiredandroid.kai.tools

import com.inspiredandroid.kai.network.tools.ParameterSchema
import com.inspiredandroid.kai.network.tools.ToolInfo
import com.inspiredandroid.kai.network.tools.ToolSchema
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.*

object BrowserTools {

    val browsePageToolInfo = ToolInfo(
        id = "browse_page",
        name = "Browse Page",
        description = "Open a URL in the browser",
        nameRes = Res.string.tool_browse_page_name,
        descriptionRes = Res.string.tool_browse_page_description,
    )

    val getPageTextToolInfo = ToolInfo(
        id = "get_page_text",
        name = "Get Page Text",
        description = "Read all text on the current page",
        nameRes = Res.string.tool_get_page_text_name,
        descriptionRes = Res.string.tool_get_page_text_description,
    )

    val clickElementToolInfo = ToolInfo(
        id = "click_element",
        name = "Click Element",
        description = "Click an element on the page",
        nameRes = Res.string.tool_click_element_name,
        descriptionRes = Res.string.tool_click_element_description,
    )

    val fillFormToolInfo = ToolInfo(
        id = "fill_form",
        name = "Fill Form",
        description = "Fill a form field",
        nameRes = Res.string.tool_fill_form_name,
        descriptionRes = Res.string.tool_fill_form_description,
    )

    val captureScreenshotToolInfo = ToolInfo(
        id = "capture_screenshot",
        name = "Capture Screenshot",
        description = "Take a screenshot of the page",
        nameRes = Res.string.tool_capture_screenshot_name,
        descriptionRes = Res.string.tool_capture_screenshot_description,
    )

    val executeScriptToolInfo = ToolInfo(
        id = "execute_script",
        name = "Execute Script",
        description = "Run JavaScript on the page",
        nameRes = Res.string.tool_execute_script_name,
        descriptionRes = Res.string.tool_execute_script_description,
    )

    val browserToolDefinitions = listOf(
        browsePageToolInfo,
        getPageTextToolInfo,
        clickElementToolInfo,
        fillFormToolInfo,
        captureScreenshotToolInfo,
        executeScriptToolInfo,
    )

    val browsePageSchema = ToolSchema(
        name = "browse_page",
        description = "Open a URL in the floating browser. This allows you to interact with the page and see the content.",
        parameters = mapOf(
            "url" to ParameterSchema(type = "string", description = "The URL to open", required = true),
        ),
    )

    val getPageTextSchema = ToolSchema(
        name = "get_page_text",
        description = "Retrieve all visible text from the current web page in the floating browser.",
        parameters = emptyMap(),
    )

    val clickElementSchema = ToolSchema(
        name = "click_element",
        description = "Click a DOM element on the current page using a CSS selector.",
        parameters = mapOf(
            "selector" to ParameterSchema(type = "string", description = "CSS selector of the element to click", required = true),
        ),
    )

    val fillFormSchema = ToolSchema(
        name = "fill_form",
        description = "Fill an input or textarea on the current page using a CSS selector.",
        parameters = mapOf(
            "selector" to ParameterSchema(type = "string", description = "CSS selector of the element to fill", required = true),
            "value" to ParameterSchema(type = "string", description = "The value to enter", required = true),
        ),
    )

    val captureScreenshotSchema = ToolSchema(
        name = "capture_screenshot",
        description = "Take a screenshot of the current viewport in the floating browser. Returns the image as a base64 encoded string.",
        parameters = emptyMap(),
    )

    val executeScriptSchema = ToolSchema(
        name = "execute_script",
        description = "Execute arbitrary JavaScript code on the current page.",
        parameters = mapOf(
            "script" to ParameterSchema(type = "string", description = "The JavaScript code to execute", required = true),
        ),
    )
}
