package com.inspiredandroid.kai.network.dtos.openaicompatible

import com.inspiredandroid.kai.network.tools.Tool
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal data class ParsedInlineToolCall(
    val name: String,
    val arguments: String,
)

internal data class InlineToolCallExtraction(
    val cleanedText: String,
    val calls: List<ParsedInlineToolCall>,
)

private const val OPEN_TAG = "<tool_call>"
private const val CLOSE_TAG = "</tool_call>"

private val functionTagRegex = Regex("<function=([\\w.\\-]+)>([\\s\\S]*?)</function>")
private val parameterTagRegex = Regex("<parameter=([\\w.\\-]+)>([\\s\\S]*?)</parameter>")

private val lenientJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * Some OpenAI-compatible models (Qwen, Hermes-style fine-tunes, and a number of
 * self-hosted endpoints) occasionally emit tool calls as inline `<tool_call>` XML
 * inside the assistant content instead of populating the structured `tool_calls`
 * field. Detect those blocks, convert each one into a synthetic tool call, and
 * return the surrounding natural-language text with the blocks removed.
 *
 * Two block flavors are accepted:
 *  - Hermes / OpenHands XML: `<function=NAME><parameter=KEY>VALUE</parameter>…</function>`
 *  - JSON: `{ "name": "...", "arguments": { … } }`
 *
 * Parameter values are coerced to JSON primitive types using the tool's schema so
 * `timeout=180` becomes a number rather than the string "180".
 */
internal fun extractInlineToolCalls(
    content: String,
    tools: List<Tool>,
): InlineToolCallExtraction {
    if (!content.contains(OPEN_TAG)) return InlineToolCallExtraction(content, emptyList())

    val calls = mutableListOf<ParsedInlineToolCall>()
    val cleaned = StringBuilder()
    var pos = 0
    while (pos < content.length) {
        val openIdx = content.indexOf(OPEN_TAG, pos)
        if (openIdx < 0) {
            cleaned.append(content, pos, content.length)
            break
        }
        cleaned.append(content, pos, openIdx)
        val closeIdx = content.indexOf(CLOSE_TAG, openIdx + OPEN_TAG.length)
        val blockEnd = if (closeIdx >= 0) closeIdx + CLOSE_TAG.length else content.length
        val innerEnd = if (closeIdx >= 0) closeIdx else content.length
        val inner = content.substring(openIdx + OPEN_TAG.length, innerEnd).trim()

        val parsed = parseToolCallBlock(inner, tools)
        if (parsed != null) {
            calls.add(parsed)
        } else {
            // Couldn't make sense of this block — keep it visible rather than silently dropping it.
            cleaned.append(content, openIdx, blockEnd)
        }
        pos = blockEnd
    }
    return InlineToolCallExtraction(cleaned.toString().trim(), calls)
}

private fun parseToolCallBlock(inner: String, tools: List<Tool>): ParsedInlineToolCall? {
    if (inner.isEmpty()) return null
    return when {
        inner.startsWith("{") -> parseJsonFlavor(inner)
        inner.contains("<function=") -> parseXmlFlavor(inner, tools)
        else -> null
    }
}

private fun parseJsonFlavor(inner: String): ParsedInlineToolCall? = try {
    val obj = lenientJson.parseToJsonElement(inner).jsonObject
    val name = obj["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        ?: return null
    val argsElement = obj["arguments"] ?: obj["parameters"]
    val argsJson = when {
        argsElement == null -> "{}"
        argsElement is JsonObject -> argsElement.toString()
        else -> argsElement.toString()
    }
    ParsedInlineToolCall(name = name, arguments = argsJson)
} catch (_: Throwable) {
    null
}

private fun parseXmlFlavor(inner: String, tools: List<Tool>): ParsedInlineToolCall? {
    val funcMatch = functionTagRegex.find(inner) ?: return null
    val name = funcMatch.groupValues[1]
    val body = funcMatch.groupValues[2]
    val schema = tools.firstOrNull { it.schema.name == name }?.schema

    val json = buildJsonObject {
        for (match in parameterTagRegex.findAll(body)) {
            val key = match.groupValues[1]
            val raw = match.groupValues[2].trim()
            val type = schema?.parameters?.get(key)?.type
            put(key, coerceParameterValue(raw, type))
        }
    }
    return ParsedInlineToolCall(name = name, arguments = json.toString())
}

private fun coerceParameterValue(
    raw: String,
    declaredType: String?,
): kotlinx.serialization.json.JsonElement = when (declaredType) {
    "integer" -> raw.toLongOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(raw)

    "number" -> raw.toDoubleOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(raw)

    "boolean" -> raw.toBooleanStrictOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(raw)

    "array", "object" -> parseJsonOrNull(raw) ?: JsonPrimitive(raw)

    // No schema hint — keep as string. Numeric-looking values stay strings to match
    // the model's literal output unless the schema explicitly asked for a number.
    else -> JsonPrimitive(raw)
}

private fun parseJsonOrNull(raw: String): kotlinx.serialization.json.JsonElement? = try {
    lenientJson.parseToJsonElement(raw)
} catch (_: Throwable) {
    null
}
