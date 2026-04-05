package com.inspiredandroid.kai.ui.dynamicui

import com.inspiredandroid.kai.data.SharedJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object KaiUiParser {

    private val kaiUiBlockRegex = Regex("```kai-ui\\s*\\n?([\\s\\S]*?)\\n?```")

    /** LLMs sometimes write "kai-ui" as plain text then a separate ```json block. */
    private val kaiUiSplitBlockRegex = Regex("(?:^|\\n)\\s*kai-ui\\s*\\n\\s*```(?:json)?\\s*\\n([\\s\\S]*?)\\n?```")

    sealed interface MessageSegment

    data class MarkdownSegment(val content: String) : MessageSegment

    data class UiSegment(val node: KaiUiNode, val rawJson: String) : MessageSegment

    data class ErrorSegment(val rawJson: String) : MessageSegment

    fun containsUiBlocks(message: String): Boolean = kaiUiBlockRegex.containsMatchIn(message) || kaiUiSplitBlockRegex.containsMatchIn(message)

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
        // Unclosed JSON — trim trailing incomplete content and close open structures
        val trimmed = trimTrailingIncomplete(result.toString(), inString)
        val sb = StringBuilder(trimmed)
        for (i in stack.indices.reversed()) {
            sb.append(if (stack[i] == '{') '}' else ']')
        }
        return sb.toString()
    }

    /**
     * Trim trailing incomplete content from truncated JSON so that appending
     * closing delimiters produces valid JSON.
     *
     * Handles: incomplete strings, trailing commas, trailing colons, and
     * incomplete key-value pairs (e.g. `"key":` or `"key`).
     */
    private fun trimTrailingIncomplete(json: String, inString: Boolean): String {
        var s = json
        // If we were inside a string when input ended, backtrack to before that string opened.
        // This removes the incomplete string and any preceding key/colon.
        if (inString) {
            val lastQuote = s.lastIndexOf('"')
            if (lastQuote >= 0) {
                s = s.substring(0, lastQuote)
            }
        }
        // Strip trailing whitespace, commas, colons, and orphaned key strings
        // (e.g. `, "key` left after removing an incomplete value)
        s = s.trimEnd()
        while (s.isNotEmpty()) {
            val last = s.last()
            if (last == ',' || last == ':') {
                s = s.dropLast(1).trimEnd()
            } else if (last == '"') {
                // Possible orphaned key — find its opening quote
                val openQuote = s.lastIndexOf('"', s.lastIndex - 1)
                if (openQuote >= 0) {
                    val before = s.substring(0, openQuote).trimEnd()
                    if (before.isEmpty() || before.last() == ',' || before.last() == '{' || before.last() == '[') {
                        s = before
                    } else {
                        break
                    }
                } else {
                    break
                }
            } else {
                break
            }
        }
        return s
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
        println("kai-ui parse error: failed to deserialize line | ${line.take(500)}")
        return null
    }

    /**
     * Recursively fix objects that LLMs produce without a "type" field.
     * Handles common patterns: {"content":"..."}, {"text":"..."}, {"title":"...","subtitle":"..."}.
     */
    private fun fixMissingTypes(element: JsonElement): JsonElement = when (element) {
        is JsonArray -> JsonArray(element.map { fixMissingTypes(it) })

        is JsonObject -> {
            val fixed = JsonObject(
                element.mapValues { (key, value) ->
                    val processed = fixMissingTypes(value)
                    when {
                        // Wrap string primitives as text nodes in children/items
                        key in nodeListFields && processed is JsonArray ->
                            JsonArray(
                                processed.map { item ->
                                    if (item is JsonPrimitive && item.isString) {
                                        JsonObject(
                                            mapOf(
                                                "type" to JsonPrimitive("text"),
                                                "value" to item,
                                            ),
                                        )
                                    } else {
                                        item
                                    }
                                },
                            )

                        // chip_group.chips: LLMs sometimes send bare strings instead of {label, value}
                        key == "chips" && processed is JsonArray ->
                            JsonArray(
                                processed.map { chip ->
                                    if (chip is JsonPrimitive && chip.isString) {
                                        JsonObject(mapOf("label" to chip, "value" to chip))
                                    } else {
                                        chip
                                    }
                                },
                            )

                        // tabs.tabs: wrap bare strings as {label, children: []}
                        key == "tabs" && processed is JsonArray ->
                            JsonArray(
                                processed.map { tab ->
                                    if (tab is JsonPrimitive && tab.isString) {
                                        JsonObject(
                                            mapOf(
                                                "label" to tab,
                                                "children" to JsonArray(emptyList()),
                                            ),
                                        )
                                    } else {
                                        tab
                                    }
                                },
                            )

                        // table.rows (List<List<String>>): accept arrays of arrays, arrays of
                        // objects (values become cells), or arrays of primitives (one cell each)
                        key == "rows" && processed is JsonArray ->
                            JsonArray(
                                processed.map { row ->
                                    when (row) {
                                        is JsonArray -> JsonArray(row.map { coerceToStringPrimitive(it) })
                                        is JsonObject -> JsonArray(row.values.map { coerceToStringPrimitive(it) })
                                        is JsonPrimitive -> JsonArray(listOf(row))
                                    }
                                },
                            )

                        // options/headers/collectFrom (List<String>): coerce every element
                        key in stringListFields && processed is JsonArray ->
                            JsonArray(processed.map { coerceToStringPrimitive(it) })

                        // LLMs sometimes put arrays where a primitive is expected
                        // e.g. "value": ["line1", "line2"] → join as comma-separated string
                        key !in knownCompositeFields && processed is JsonArray ->
                            flattenToString(processed)

                        // LLMs sometimes put objects where a primitive is expected
                        // e.g. "value": {"text": "hello"} → extract as "hello"
                        key !in knownCompositeFields && processed is JsonObject ->
                            coerceToStringPrimitive(processed)

                        else -> processed
                    }
                },
            )
            val migrated = migrateLegacyFields(fixed)
            if ("type" in migrated) {
                migrated
            } else {
                inferMissingType(migrated)
            }
        }

        else -> element
    }

    /**
     * Migrate deprecated JSON field names so historical kai-ui blocks keep working.
     * Runs before deserialization.
     */
    private fun migrateLegacyFields(obj: JsonObject): JsonObject {
        val type = (obj["type"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        // chip_group: multiSelect (Boolean) → selection (String)
        if (type == "chip_group" && "multiSelect" in obj && "selection" !in obj) {
            val multi = (obj["multiSelect"] as? JsonPrimitive)?.booleanOrNull == true
            val newMap = obj.toMutableMap()
            newMap.remove("multiSelect")
            newMap["selection"] = JsonPrimitive(if (multi) "multi" else "single")
            return JsonObject(newMap)
        }
        return obj
    }

    /** Known fields on helper data classes (ChipItem, TabItem) — skip inference. */
    private val helperObjectFields = setOf("value", "children", "action")

    /** Known node type discriminators derived from the KaiUiNode sealed hierarchy. */
    private val knownNodeTypes: Set<String> by lazy {
        val valueDescriptor = KaiUiNode.serializer().descriptor.getElementDescriptor(1)
        (0 until valueDescriptor.elementsCount).map { valueDescriptor.getElementName(it) }.toSet()
    }

    /** Fields that hold List<KaiUiNode> and need unknown-type filtering. */
    private val nodeListFields = setOf("children", "items")

    /** Fields typed as List<String> on the data model — each element must be coerced to a string. */
    private val stringListFields = setOf("options", "headers", "collectFrom")

    /** Fields whose JSON values are expected to be arrays or objects (not primitives). */
    private val knownCompositeFields = nodeListFields + setOf(
        "chips",
        "tabs",
        "options",
        "headers",
        "rows",
        "collectFrom",
        "action",
        "data",
    )

    /** Join array elements into a single comma-separated string primitive. */
    private fun flattenToString(arr: JsonArray): JsonPrimitive = JsonPrimitive(arr.joinToString(", ") { if (it is JsonPrimitive) it.content else it.toString() })

    /**
     * Coerce any JsonElement into a string primitive, best-effort. Used when an LLM puts an
     * object or array into a slot that the data model declares as a plain `String`.
     *
     * - Primitive → itself
     * - Array → comma-joined contents (via `flattenToString`)
     * - Object → first "label-ish" primitive field (`value`/`text`/`label`/`title`/`name`/`content`),
     *   else comma-joined values
     */
    private fun coerceToStringPrimitive(element: JsonElement): JsonPrimitive = when (element) {
        is JsonPrimitive -> element

        is JsonArray -> flattenToString(element)

        is JsonObject -> {
            val preferred = listOf("value", "text", "label", "title", "name", "content")
                .firstNotNullOfOrNull { element[it] as? JsonPrimitive }
            preferred ?: JsonPrimitive(
                element.values.joinToString(", ") { if (it is JsonPrimitive) it.content else it.toString() },
            )
        }
    }

    /**
     * Recursively remove objects with unrecognized "type" values from node-list fields
     * (children, items). Returns null if the top-level element itself is an unknown node type.
     */
    private fun stripUnknownNodes(element: JsonElement): JsonElement? = when (element) {
        is JsonObject -> {
            val type = element["type"]?.jsonPrimitive?.contentOrNull
            if (type != null && type !in knownNodeTypes) {
                null
            } else {
                JsonObject(
                    element.mapValues { (key, value) ->
                        if (key in nodeListFields && value is JsonArray) {
                            JsonArray(value.mapNotNull { stripUnknownNodes(it) })
                        } else {
                            stripUnknownNodes(value) ?: value
                        }
                    },
                )
            }
        }

        is JsonArray -> JsonArray(element.map { stripUnknownNodes(it) ?: it })

        else -> element
    }

    private fun inferMissingType(obj: JsonObject): JsonObject {
        // Skip inference for helper objects (ChipItem, TabItem)
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

    private fun parseSingleNode(json: String): KaiUiNode? {
        val jsonElement = fixMissingTypes(SharedJson.parseToJsonElement(json))
        val filtered = stripUnknownNodes(jsonElement) ?: return null
        val jsonObject = filtered.jsonObject
        return if ("type" in jsonObject) {
            SharedJson.decodeFromJsonElement(KaiUiNode.serializer(), jsonObject)
        } else {
            // Wrap bare object as a column if it has children but no type
            val wrapped = JsonObject(jsonObject + ("type" to JsonPrimitive("column")))
            SharedJson.decodeFromJsonElement(KaiUiNode.serializer(), wrapped)
        }
    }

    fun stripUiBlocks(message: String): String = kaiUiSplitBlockRegex.replace(kaiUiBlockRegex.replace(message, ""), "").trim()

    /** Find all kai-ui block matches (from both regex patterns), sorted by position, non-overlapping. */
    private fun findAllUiBlockMatches(message: String): List<MatchResult> {
        val all = (kaiUiBlockRegex.findAll(message) + kaiUiSplitBlockRegex.findAll(message))
            .sortedBy { it.range.first }
            .toList()
        // Remove overlapping matches (keep the one that starts first)
        val result = mutableListOf<MatchResult>()
        var lastEnd = -1
        for (match in all) {
            if (match.range.first > lastEnd) {
                result.add(match)
                lastEnd = match.range.last
            }
        }
        return result
    }

    fun parse(message: String): List<MessageSegment> {
        val segments = mutableListOf<MessageSegment>()
        var lastIndex = 0

        for (match in findAllUiBlockMatches(message)) {
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
                    val node = parseSingleNode(json)
                    if (node != null) {
                        segments.add(UiSegment(node, json))
                    }
                } catch (e: Exception) {
                    println("kai-ui parse error: ${e.message} | ${json.take(500)}")
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
