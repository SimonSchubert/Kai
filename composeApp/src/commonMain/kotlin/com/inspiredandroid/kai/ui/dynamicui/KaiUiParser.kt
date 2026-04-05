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

/**
 * Parses assistant messages that contain `kai-ui` fenced JSON blocks into a list of
 * [MessageSegment]s the renderer can consume.
 *
 * The parse pipeline runs each block through five stages:
 *   1. **Block extraction** — locate `kai-ui` fences in the message text
 *   2. **Syntax repair** — fix broken key syntax, trim mismatched braces, close truncated JSON
 *   3. **Shape coercion** — reshape the JsonElement tree so common LLM mistakes match the
 *      strict data model (wrong-typed fields, untyped objects, bare strings, legacy aliases)
 *   4. **Unknown-node stripping** — drop any objects with unrecognised `type` discriminators
 *      from `children`/`items` arrays
 *   5. **Decoding** — deserialize the normalised JsonObject into a [KaiUiNode] via
 *      kotlinx.serialization
 *
 * Only stages 1 and 5 can produce [ErrorSegment]; stages 2-4 never throw.
 */
object KaiUiParser {

    // =========================================================================================
    // Public API
    // =========================================================================================

    sealed interface MessageSegment

    data class MarkdownSegment(val content: String) : MessageSegment

    data class UiSegment(val node: KaiUiNode, val rawJson: String) : MessageSegment

    data class ErrorSegment(val rawJson: String) : MessageSegment

    fun containsUiBlocks(message: String): Boolean =
        kaiUiBlockRegex.containsMatchIn(message) || kaiUiSplitBlockRegex.containsMatchIn(message)

    fun stripUiBlocks(message: String): String =
        kaiUiSplitBlockRegex.replace(kaiUiBlockRegex.replace(message, ""), "").trim()

    fun parse(message: String): List<MessageSegment> {
        val segments = mutableListOf<MessageSegment>()
        var lastIndex = 0

        for (match in findAllUiBlockMatches(message)) {
            val before = message.substring(lastIndex, match.range.first)
            if (before.isNotBlank()) {
                segments.add(MarkdownSegment(before))
            }
            parseBlock(match.groupValues[1].trim())?.let { segments.add(it) }
            lastIndex = match.range.last + 1
        }

        val remaining = message.substring(lastIndex)
        if (remaining.isNotBlank()) {
            segments.add(MarkdownSegment(remaining))
        }
        return segments
    }

    // =========================================================================================
    // Block extraction
    // =========================================================================================

    private val kaiUiBlockRegex = Regex("```kai-ui\\s*\\n?([\\s\\S]*?)\\n?```")

    /** LLMs sometimes write "kai-ui" as plain text then a separate ```json block. */
    private val kaiUiSplitBlockRegex = Regex("(?:^|\\n)\\s*kai-ui\\s*\\n\\s*```(?:json)?\\s*\\n([\\s\\S]*?)\\n?```")

    /** Non-overlapping matches from both fence patterns, sorted by position. */
    private fun findAllUiBlockMatches(message: String): List<MatchResult> {
        val all = (kaiUiBlockRegex.findAll(message) + kaiUiSplitBlockRegex.findAll(message))
            .sortedBy { it.range.first }
            .toList()
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

    /**
     * Parse the content of a single kai-ui fence into a segment, or `null` if the block's
     * top-level type is unknown (silently dropped). Multi-line fences (NDJSON) are parsed
     * line-by-line and wrapped in a column; a block that fails to decode at all becomes an
     * [ErrorSegment].
     */
    private fun parseBlock(rawBlock: String): MessageSegment? {
        val repaired = fixJsonSyntax(rawBlock)
        val lines = repaired.lines().map { it.trim() }.filter { it.isNotEmpty() }

        if (lines.size > 1 && lines.all { it.startsWith("{") }) {
            val children = lines.mapNotNull { tryParseLine(it) }
            return if (children.isNotEmpty()) {
                UiSegment(ColumnNode(children = children), repaired)
            } else {
                ErrorSegment(repaired)
            }
        }

        val json = sanitizeJson(repaired)
        return try {
            // Null result → unknown top-level node type → silently drop the block.
            parseSingleNode(json)?.let { UiSegment(it, json) }
        } catch (e: Exception) {
            println("kai-ui parse error: ${e.message} | ${json.take(500)}")
            ErrorSegment(json)
        }
    }

    /** Try to parse a single NDJSON line, retrying with `sanitizeJson` on the first failure. */
    private fun tryParseLine(line: String): KaiUiNode? =
        runCatching { parseSingleNode(line) }.getOrNull()
            ?: runCatching { parseSingleNode(sanitizeJson(line)) }.getOrNull()
            ?: run {
                println("kai-ui parse error: failed to deserialize line | ${line.take(500)}")
                null
            }

    /** Run a repaired JSON string through stages 3-5 of the pipeline. */
    private fun parseSingleNode(json: String): KaiUiNode? {
        val coerced = fixMissingTypes(SharedJson.parseToJsonElement(json))
        val filtered = stripUnknownNodes(coerced) ?: return null
        val root = filtered.jsonObject
        val withType = if ("type" in root) {
            root
        } else {
            // Bare object with children but no type → treat as a column.
            JsonObject(root + ("type" to JsonPrimitive("column")))
        }
        return SharedJson.decodeFromJsonElement(KaiUiNode.serializer(), withType)
    }

    // =========================================================================================
    // Field classifications
    // =========================================================================================

    /** Fields that hold `List<KaiUiNode>` — get text-node wrapping and unknown-type filtering. */
    private val nodeListFields = setOf("children", "items")

    /** Fields typed as `List<String>` — each element is coerced to a string. */
    private val stringListFields = setOf("options", "headers", "collectFrom")

    /** Fields typed as `Boolean` — string values like `"true"`/`"yes"`/`"1"` are coerced. */
    private val booleanFields = setOf("bold", "italic", "enabled", "multiline", "checked", "expanded", "ordered")

    /** Fields whose JSON values are legitimately arrays or objects (skip primitive-slot coercions). */
    private val compositeFields = nodeListFields + setOf(
        "chips",
        "tabs",
        "options",
        "headers",
        "rows",
        "collectFrom",
        "action",
        "data",
    )

    /** Keys preferred when extracting a string from an arbitrary JsonObject. */
    private val labelKeys = listOf("value", "text", "label", "title", "name", "content")

    /** Helper data class field names — used by `inferMissingType` to skip ChipItem/TabItem. */
    private val helperObjectFields = setOf("value", "children", "action")

    /** Recognised polymorphic `UiAction` discriminators. */
    private val knownActionTypes = setOf("callback", "toggle", "open_url")

    /** Known `KaiUiNode` discriminators, derived reflectively from the sealed hierarchy. */
    private val knownNodeTypes: Set<String> by lazy {
        val valueDescriptor = KaiUiNode.serializer().descriptor.getElementDescriptor(1)
        (0 until valueDescriptor.elementsCount).map { valueDescriptor.getElementName(it) }.toSet()
    }

    // =========================================================================================
    // Stage 2: syntax repair
    // =========================================================================================

    /** Fix common LLM JSON syntax errors like `"key=[` instead of `"key":[`. */
    private val brokenKeySyntax = Regex(""""(\w+)=([{\[])""")

    private fun fixJsonSyntax(raw: String): String =
        brokenKeySyntax.replace(raw) { "\"${it.groupValues[1]}\":${it.groupValues[2]}" }

    /**
     * Repair JSON with extra closing braces/brackets using stack-based matching.
     * Mismatched closers are skipped; unclosed structures are trimmed and then closed.
     */
    private fun sanitizeJson(raw: String): String {
        if (raw.isEmpty()) return raw
        if (raw[0] != '{' && raw[0] != '[') return raw

        val stack = mutableListOf<Char>()
        val result = StringBuilder()
        var inString = false
        var escaped = false

        for (c in raw) {
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

        // Unclosed JSON — trim trailing incomplete content and then close open structures.
        val trimmed = trimTrailingIncomplete(result.toString(), inString)
        return buildString {
            append(trimmed)
            for (i in stack.indices.reversed()) {
                append(if (stack[i] == '{') '}' else ']')
            }
        }
    }

    /**
     * Trim trailing incomplete content from truncated JSON so appending closers produces valid
     * JSON. Handles incomplete strings, trailing commas, trailing colons, and orphaned keys.
     */
    private fun trimTrailingIncomplete(json: String, inString: Boolean): String {
        var s = json
        // If we were inside a string when input ended, backtrack to before that string opened.
        if (inString) {
            val lastQuote = s.lastIndexOf('"')
            if (lastQuote >= 0) s = s.substring(0, lastQuote)
        }
        // Strip trailing whitespace, commas, colons, and orphaned key strings.
        s = s.trimEnd()
        while (s.isNotEmpty()) {
            val last = s.last()
            if (last == ',' || last == ':') {
                s = s.dropLast(1).trimEnd()
                continue
            }
            if (last != '"') break
            // Possible orphaned key — find its opening quote.
            val openQuote = s.lastIndexOf('"', s.lastIndex - 1)
            if (openQuote < 0) break
            val before = s.substring(0, openQuote).trimEnd()
            if (before.isEmpty() || before.last() in setOf(',', '{', '[')) {
                s = before
            } else {
                break
            }
        }
        return s
    }

    // =========================================================================================
    // Stage 3: shape coercion
    //
    // Every rule here is driven by field NAME so the data model stays the source of truth.
    // Rules never throw: if a value is unrecoverable, the encompassing `coerceField` returns
    // the original value and lets stages 4/5 deal with it.
    // =========================================================================================

    /** Recursively reshape a JsonElement so it matches the strict KaiUiNode data model. */
    private fun fixMissingTypes(element: JsonElement): JsonElement = when (element) {
        is JsonArray -> JsonArray(element.map { fixMissingTypes(it) })
        is JsonObject -> coerceObject(element)
        else -> element
    }

    /** Shape-coerce all fields of [obj], then apply field migrations and type inference. */
    private fun coerceObject(obj: JsonObject): JsonObject {
        val fixed = JsonObject(
            obj.mapValues { (key, value) -> coerceField(key, fixMissingTypes(value)) },
        )
        val migrated = migrateLegacyFields(fixed)
        return if ("type" in migrated) migrated else inferMissingType(migrated)
    }

    /** Apply the correct coercion rule for [key] based on what the data model declares there. */
    private fun coerceField(key: String, value: JsonElement): JsonElement = when {
        key in nodeListFields && value is JsonArray -> normalizeNodeList(value)
        key == "chips" && value is JsonArray -> normalizeChips(value)
        key == "tabs" && value is JsonArray -> normalizeTabs(value)
        key == "rows" && value is JsonArray -> normalizeTableRows(value)
        key in stringListFields && value is JsonArray ->
            JsonArray(value.map { coerceToStringPrimitive(it) })
        key == "action" -> coerceToActionObject(value)
        key in booleanFields && value is JsonPrimitive && value.isString ->
            stringToBoolean(value) ?: value
        key !in compositeFields && value is JsonArray -> flattenToString(value)
        key !in compositeFields && value is JsonObject -> coerceToStringPrimitive(value)
        else -> value
    }

    // -- Node-list normalization -------------------------------------------------------------

    /** `children`/`items`: wrap bare strings and untyped objects as text nodes. */
    private fun normalizeNodeList(array: JsonArray): JsonArray =
        JsonArray(
            array.map { item ->
                when {
                    item is JsonPrimitive && item.isString -> textNode(item)
                    // Merge (not rebuild) so style/bold/italic/color survive on the original.
                    item is JsonObject && "type" !in item ->
                        JsonObject(item + textNodeFields(coerceToStringPrimitive(item)))
                    else -> item
                }
            },
        )

    /** `chip_group.chips`: wrap bare strings as `{label, value}`; coerce nested label/value. */
    private fun normalizeChips(array: JsonArray): JsonArray =
        JsonArray(
            array.map { chip ->
                when {
                    chip is JsonPrimitive && chip.isString ->
                        JsonObject(mapOf("label" to chip, "value" to chip))
                    chip is JsonObject ->
                        JsonObject(chip.mapValues { (k, v) -> coerceHelperStringField(k, v, chipStringKeys) })
                    else -> chip
                }
            },
        )

    /** `tabs.tabs`: wrap bare strings as `{label, children: []}`; coerce nested label. */
    private fun normalizeTabs(array: JsonArray): JsonArray =
        JsonArray(
            array.map { tab ->
                when {
                    tab is JsonPrimitive && tab.isString ->
                        JsonObject(mapOf("label" to tab, "children" to JsonArray(emptyList())))
                    tab is JsonObject ->
                        JsonObject(tab.mapValues { (k, v) -> coerceHelperStringField(k, v, tabStringKeys) })
                    else -> tab
                }
            },
        )

    /**
     * `table.rows` (`List<List<String>>`): accept array-of-arrays, array-of-objects (values
     * become cells in key order), or array-of-primitives (wrapped as single-cell rows).
     */
    private fun normalizeTableRows(array: JsonArray): JsonArray =
        JsonArray(
            array.map { row ->
                when (row) {
                    is JsonArray -> JsonArray(row.map { coerceToStringPrimitive(it) })
                    is JsonObject -> JsonArray(row.values.map { coerceToStringPrimitive(it) })
                    is JsonPrimitive -> JsonArray(listOf(row))
                }
            },
        )

    private val chipStringKeys = setOf("label", "value")
    private val tabStringKeys = setOf("label")

    /** Coerce a helper data class field to a string primitive if it's in [stringKeys]. */
    private fun coerceHelperStringField(
        key: String,
        value: JsonElement,
        stringKeys: Set<String>,
    ): JsonElement =
        if (key in stringKeys && value !is JsonPrimitive) coerceToStringPrimitive(value) else value

    // -- Primitive coercion helpers ----------------------------------------------------------

    /** `{type: "text", value: ...}` — the most common shape in the parser. */
    private fun textNode(value: JsonPrimitive): JsonObject = JsonObject(textNodeFields(value))

    private fun textNodeFields(value: JsonPrimitive): Map<String, JsonPrimitive> =
        mapOf("type" to JsonPrimitive("text"), "value" to value)

    /** `{type: "text", value: ..., style: "..."}` — for inference of title/subtitle shapes. */
    private fun styledTextNode(value: JsonPrimitive, style: String): JsonObject =
        JsonObject(textNodeFields(value) + ("style" to JsonPrimitive(style)))

    /** Join array elements into a single comma-separated string primitive. */
    private fun flattenToString(arr: JsonArray): JsonPrimitive =
        JsonPrimitive(arr.joinToString(", ") { if (it is JsonPrimitive) it.content else it.toString() })

    /**
     * Coerce any JsonElement into a string primitive, best-effort.
     * - Primitive → itself
     * - Array → comma-joined contents
     * - Object → first preferred key with a primitive value, else comma-joined values
     */
    private fun coerceToStringPrimitive(element: JsonElement): JsonPrimitive = when (element) {
        is JsonPrimitive -> element
        is JsonArray -> flattenToString(element)
        is JsonObject ->
            labelKeys.firstNotNullOfOrNull { element[it] as? JsonPrimitive }
                ?: flattenToString(JsonArray(element.values.toList()))
    }

    /** Coerce a bare string/array or untyped object into a well-formed `UiAction` JsonObject. */
    private fun coerceToActionObject(element: JsonElement): JsonObject = when (element) {
        is JsonPrimitive ->
            JsonObject(mapOf("type" to JsonPrimitive("callback"), "event" to element))
        is JsonArray ->
            JsonObject(mapOf("type" to JsonPrimitive("callback"), "event" to flattenToString(element)))
        is JsonObject -> {
            val type = (element["type"] as? JsonPrimitive)?.contentOrNull
            if (type in knownActionTypes) {
                element
            } else {
                val inferred = when {
                    "event" in element -> "callback"
                    "targetId" in element -> "toggle"
                    "url" in element -> "open_url"
                    else -> "callback"
                }
                JsonObject(element.toMutableMap().apply { this["type"] = JsonPrimitive(inferred) })
            }
        }
    }

    /** Parse `"true"`/`"false"`/`"yes"`/`"no"`/`"1"`/`"0"` (case-insensitive) as a boolean. */
    private fun stringToBoolean(primitive: JsonPrimitive): JsonPrimitive? =
        when (primitive.content.lowercase()) {
            "true", "yes", "1" -> JsonPrimitive(true)
            "false", "no", "0" -> JsonPrimitive(false)
            else -> null
        }

    // -- Field migration + type inference for bare objects -----------------------------------

    /** Rename deprecated/aliased JSON field names so historical or HTML-style kai-ui keeps working. */
    private fun migrateLegacyFields(obj: JsonObject): JsonObject {
        val type = (obj["type"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        return when {
            // chip_group: multiSelect (Boolean) → selection (String)
            type == "chip_group" && "multiSelect" in obj && "selection" !in obj -> {
                val multi = (obj["multiSelect"] as? JsonPrimitive)?.booleanOrNull == true
                JsonObject(
                    obj.toMutableMap().apply {
                        remove("multiSelect")
                        this["selection"] = JsonPrimitive(if (multi) "multi" else "single")
                    },
                )
            }
            // image: src (HTML attribute name) → url (our model)
            type == "image" && "src" in obj && "url" !in obj -> {
                JsonObject(obj.toMutableMap().apply { this["url"] = remove("src")!! })
            }
            else -> obj
        }
    }

    /**
     * Recover a typed node from a bare object that lacks a `type` discriminator by matching
     * common LLM shortcuts: `{"content":"..."}`, `{"text":"..."}`, `{"title":"...","subtitle":"..."}`, etc.
     *
     * Skips objects that look like ChipItem/TabItem helpers (detected by the presence of
     * `value`/`children`/`action` keys) to avoid corrupting them.
     */
    private fun inferMissingType(obj: JsonObject): JsonObject {
        if (obj.keys.any { it in helperObjectFields }) return obj

        fun prim(key: String): JsonPrimitive = obj[key]?.jsonPrimitive ?: JsonPrimitive("")

        return when {
            "content" in obj -> textNode(prim("content"))
            "title" in obj && "subtitle" in obj -> JsonObject(
                mapOf(
                    "type" to JsonPrimitive("column"),
                    "children" to JsonArray(
                        listOf(
                            styledTextNode(prim("title"), "title"),
                            styledTextNode(prim("subtitle"), "caption"),
                        ),
                    ),
                ),
            )
            "text" in obj -> textNode(prim("text"))
            "title" in obj -> styledTextNode(prim("title"), "title")
            "label" in obj -> textNode(prim("label"))
            else -> obj
        }
    }

    // =========================================================================================
    // Stage 4: strip unknown node types
    // =========================================================================================

    /**
     * Recursively drop JsonObjects with unrecognised `type` discriminators from `children`/`items`
     * arrays. For non-list fields the original value is kept when the recursive call returns null
     * (this preserves `action` payloads, whose discriminators aren't KaiUiNode types).
     *
     * Returns null only if the *top-level* element itself is an unknown node type.
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
}
