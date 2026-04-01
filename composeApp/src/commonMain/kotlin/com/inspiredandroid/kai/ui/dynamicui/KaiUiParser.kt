package com.inspiredandroid.kai.ui.dynamicui

import com.inspiredandroid.kai.data.SharedJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

object KaiUiParser {

    private val kaiUiBlockRegex = Regex("```kai-ui\\s*\\n?(.*?)\\n?```", RegexOption.DOT_MATCHES_ALL)

    sealed interface MessageSegment

    data class MarkdownSegment(val content: String) : MessageSegment

    data class UiSegment(val node: KaiUiNode, val rawJson: String) : MessageSegment

    data class ErrorSegment(val rawJson: String) : MessageSegment

    fun containsUiBlocks(message: String): Boolean = kaiUiBlockRegex.containsMatchIn(message)

    /**
     * LLMs sometimes produce JSON with unbalanced trailing braces/brackets.
     * Walk the string tracking depth (respecting strings) and truncate at the
     * point where the root object/array closes.
     */
    private fun sanitizeJson(raw: String): String {
        if (raw.isEmpty()) return raw
        val open = raw[0]
        if (open != '{' && open != '[') return raw
        val close = if (open == '{') '}' else ']'
        var depth = 0
        var inString = false
        var escaped = false
        for (i in raw.indices) {
            val c = raw[i]
            if (escaped) { escaped = false; continue }
            if (c == '\\' && inString) { escaped = true; continue }
            if (c == '"') { inString = !inString; continue }
            if (inString) continue
            if (c == open || c == '[' || c == '{') depth++
            if (c == close || c == ']' || c == '}') depth--
            if (depth == 0) return raw.substring(0, i + 1)
        }
        return raw // unclosed — let the JSON parser produce the error
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

            val json = sanitizeJson(match.groupValues[1].trim())
            try {
                val jsonElement = SharedJson.parseToJsonElement(json)
                val jsonObject = jsonElement.jsonObject
                val node = if ("type" in jsonObject) {
                    SharedJson.decodeFromString<KaiUiNode>(json)
                } else {
                    // Wrap bare object as a column if it has children but no type
                    val wrapped = JsonObject(jsonObject + ("type" to kotlinx.serialization.json.JsonPrimitive("column")))
                    SharedJson.decodeFromJsonElement(KaiUiNode.serializer(), wrapped)
                }
                segments.add(UiSegment(node, json))
            } catch (e: Exception) {
                println("KaiUiParser: failed to deserialize kai-ui block: ${e.message}")
                segments.add(ErrorSegment(json))
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
