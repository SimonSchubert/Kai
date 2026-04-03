package com.inspiredandroid.kai.ui.dynamicui

import com.inspiredandroid.kai.data.SharedJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object KaiUiParser {

    private val kaiUiBlockRegex = Regex("```kai-ui\\s*\\n?([\\s\\S]*?)\\n?```")

    sealed interface MessageSegment

    data class MarkdownSegment(val content: String) : MessageSegment

    data class UiSegment(val node: KaiUiNode, val rawJson: String) : MessageSegment

    data class ErrorSegment(val rawJson: String) : MessageSegment

    fun containsUiBlocks(message: String): Boolean = kaiUiBlockRegex.containsMatchIn(message)

    /** Fix common LLM JSON syntax errors like `"key=[` instead of `"key":[`. */
    private val brokenKeySyntax = Regex(""""(\w+)=([{\[])""")
    private fun fixJsonSyntax(raw: String): String = brokenKeySyntax.replace(raw) { "\"${it.groupValues[1]}\":${it.groupValues[2]}" }

    /**
     * LLMs sometimes produce JSON with extra closing braces/brackets.
     * Uses stack-based matching: mismatched closers (e.g. `}` when `[` is
     * on top) are skipped, effectively removing the LLM's erroneous characters.
     * Returns as soon as the root object/array is fully closed.
     */
    private fun sanitizeJson(raw: String): String {
        if (raw.isEmpty()) return raw
        if (raw[0] != '{' && raw[0] != '[') return raw
        val stack = mutableListOf<Char>()
        val result = StringBuilder()
        var inString = false
        var escaped = false
        for (i in raw.indices) {
            val c = raw[i]
            if (escaped) {
                escaped = false
                result.append(c)
                continue
            }
            if (c == '\\' && inString) {
                escaped = true
                result.append(c)
                continue
            }
            if (c == '"') {
                inString = !inString
                result.append(c)
                continue
            }
            if (inString) {
                result.append(c)
                continue
            }
            when (c) {
                '{', '[' -> {
                    stack.add(c)
                    result.append(c)
                }

                '}' -> if (stack.isNotEmpty() && stack.last() == '{') {
                    stack.removeAt(stack.lastIndex)
                    result.append(c)
                }

                ']' -> if (stack.isNotEmpty() && stack.last() == '[') {
                    stack.removeAt(stack.lastIndex)
                    result.append(c)
                }

                else -> result.append(c)
            }
            if (stack.isEmpty()) return result.toString()
        }
        return result.toString() // unclosed — return what we have
    }

    /**
     * Try multiple strategies to parse a single JSON line into a KaiUiNode.
     * Returns null if all strategies fail.
     */
    private fun tryParseLine(line: String): KaiUiNode? {
        // Strategy 1: parse directly
        try {
            return parseSingleNode(line)
        } catch (_: Exception) {
        }
        // Strategy 2: sanitize trailing braces/brackets then parse
        try {
            return parseSingleNode(sanitizeJson(line))
        } catch (_: Exception) {
        }
        ParseErrorCollector.log("failed to deserialize kai-ui line", line)
        return null
    }

    /**
     * Recursively fix objects that LLMs produce without a "type" field.
     * Handles common patterns: {"content":"..."}, {"text":"..."}, {"title":"...","subtitle":"..."}.
     */
    private fun fixMissingTypes(element: JsonElement): JsonElement = when (element) {
        is JsonArray -> JsonArray(element.map { fixMissingTypes(it) })

        is JsonObject -> {
            val fixed = JsonObject(element.mapValues { fixMissingTypes(it.value) })
            if ("type" in fixed) {
                fixed
            } else {
                inferMissingType(fixed)
            }
        }

        else -> element
    }

    /** Known fields on helper data classes (ChipItem, TabItem, BottomBarButton) — skip inference. */
    private val helperObjectFields = setOf("value", "children", "action")

    private fun inferMissingType(obj: JsonObject): JsonObject {
        // Skip inference for helper objects (ChipItem, TabItem, BottomBarButton)
        // that have known non-node fields alongside label/title.
        if (obj.keys.any { it in helperObjectFields }) return obj

        // {"content":"..."} → text node
        if ("content" in obj) {
            return JsonObject(
                mapOf(
                    "type" to JsonPrimitive("text"),
                    "value" to (obj["content"]?.jsonPrimitive ?: JsonPrimitive("")),
                ),
            )
        }
        // {"title":"...","subtitle":"..."} → column with title + subtitle text nodes
        if ("title" in obj && "subtitle" in obj) {
            val children = JsonArray(
                listOf(
                    JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("text"),
                            "value" to (obj["title"]?.jsonPrimitive ?: JsonPrimitive("")),
                            "style" to JsonPrimitive("title"),
                        ),
                    ),
                    JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("text"),
                            "value" to (obj["subtitle"]?.jsonPrimitive ?: JsonPrimitive("")),
                            "style" to JsonPrimitive("caption"),
                        ),
                    ),
                ),
            )
            return JsonObject(mapOf("type" to JsonPrimitive("column"), "children" to children))
        }
        // {"text":"..."} → text node (LLMs often use "text" instead of "value")
        if ("text" in obj) {
            return JsonObject(
                mapOf(
                    "type" to JsonPrimitive("text"),
                    "value" to (obj["text"]?.jsonPrimitive ?: JsonPrimitive("")),
                ),
            )
        }
        // {"title":"..."} → text node with title style
        if ("title" in obj) {
            return JsonObject(
                mapOf(
                    "type" to JsonPrimitive("text"),
                    "value" to (obj["title"]?.jsonPrimitive ?: JsonPrimitive("")),
                    "style" to JsonPrimitive("title"),
                ),
            )
        }
        // {"label":"..."} → text node (only when no other known fields present)
        if ("label" in obj) {
            return JsonObject(
                mapOf(
                    "type" to JsonPrimitive("text"),
                    "value" to (obj["label"]?.jsonPrimitive ?: JsonPrimitive("")),
                ),
            )
        }
        return obj
    }

    private fun parseSingleNode(json: String): KaiUiNode {
        val jsonElement = fixMissingTypes(SharedJson.parseToJsonElement(json))
        val jsonObject = jsonElement.jsonObject
        return if ("type" in jsonObject) {
            SharedJson.decodeFromJsonElement(KaiUiNode.serializer(), jsonObject)
        } else {
            // Wrap bare object as a column if it has children but no type
            val wrapped = JsonObject(jsonObject + ("type" to JsonPrimitive("column")))
            SharedJson.decodeFromJsonElement(KaiUiNode.serializer(), wrapped)
        }
    }

    fun stripUiBlocks(message: String): String = kaiUiBlockRegex.replace(message, "").trim()

    fun parse(message: String): List<MessageSegment> {
        val segments = mutableListOf<MessageSegment>()
        var lastIndex = 0

        for (match in kaiUiBlockRegex.findAll(message)) {
            val before = message.substring(lastIndex, match.range.first)
            if (before.isNotBlank()) {
                segments.add(MarkdownSegment(before))
            }

            val rawBlock = fixJsonSyntax(match.groupValues[1].trim())
            val lines = rawBlock.lines().map { it.trim() }.filter { it.isNotEmpty() }

            // Multi-line: each line is a separate JSON object → parse individually and wrap in a column
            if (lines.size > 1 && lines.all { it.startsWith("{") }) {
                val children = mutableListOf<KaiUiNode>()
                for (line in lines) {
                    val node = tryParseLine(line)
                    if (node != null) {
                        children.add(node)
                    }
                }
                if (children.isNotEmpty()) {
                    val columnNode = ColumnNode(children = children)
                    segments.add(UiSegment(columnNode, rawBlock))
                } else {
                    segments.add(ErrorSegment(rawBlock))
                }
            } else {
                // Single JSON object (possibly with trailing braces from LLM)
                val json = sanitizeJson(rawBlock)
                try {
                    segments.add(UiSegment(parseSingleNode(json), json))
                } catch (e: Exception) {
                    ParseErrorCollector.log("failed to deserialize kai-ui block: ${e.message}", json)
                    segments.add(ErrorSegment(json))
                }
            }

            lastIndex = match.range.last + 1
        }

        val remaining = message.substring(lastIndex)
        if (remaining.isNotBlank()) {
            segments.add(MarkdownSegment(remaining))
        }

        return segments
    }
}
