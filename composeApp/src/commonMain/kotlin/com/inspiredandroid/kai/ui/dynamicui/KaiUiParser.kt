package com.inspiredandroid.kai.ui.dynamicui

import com.inspiredandroid.kai.data.SharedJson

/**
 * Parses assistant messages that contain `kai-ui` fenced JSON blocks into a list of
 * [MessageSegment]s the renderer can consume.
 *
 * The parse pipeline runs each block through three stages:
 *   1. **Block extraction** — locate `kai-ui` fences in the message text
 *   2. **Syntax repair** — fix broken key syntax, trim mismatched braces, close truncated JSON
 *      so `parseToJsonElement` can succeed
 *   3. **Direct build** — walk the resulting [kotlinx.serialization.json.JsonElement] tree via
 *      [parseNode] in `KaiUiNodeBuilders.kt`, constructing [KaiUiNode] instances field-by-field.
 *      Each reader tolerates common LLM mistakes locally, so missing or miscoerced fields fall
 *      back to their data-class defaults and the node still builds.
 *
 * Only stages 1 and the `parseToJsonElement` call in stage 3 can produce an [ErrorSegment];
 * everything downstream of that returns a best-effort node or a null that callers filter out.
 */
object KaiUiParser {

    // =========================================================================================
    // Public API
    // =========================================================================================

    sealed interface MessageSegment

    data class MarkdownSegment(val content: String) : MessageSegment

    data class UiSegment(val node: KaiUiNode, val rawJson: String) : MessageSegment

    data class ErrorSegment(val rawJson: String) : MessageSegment

    fun containsUiBlocks(message: String): Boolean = kaiUiBlockRegex.containsMatchIn(message) || kaiUiSplitBlockRegex.containsMatchIn(message)

    fun stripUiBlocks(message: String): String = kaiUiSplitBlockRegex.replace(kaiUiBlockRegex.replace(message, ""), "").trim()

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
    private fun tryParseLine(line: String): KaiUiNode? = runCatching { parseSingleNode(line) }.getOrNull()
        ?: runCatching { parseSingleNode(sanitizeJson(line)) }.getOrNull()
        ?: run {
            println("kai-ui parse error: failed to deserialize line | ${line.take(500)}")
            null
        }

    /** Parse a repaired JSON string into a [KaiUiNode] via the direct builder pipeline. */
    private fun parseSingleNode(json: String): KaiUiNode? = parseNode(SharedJson.parseToJsonElement(json))

    // =========================================================================================
    // Stage 2: syntax repair
    // =========================================================================================

    /** Fix common LLM JSON syntax errors like `"key=[` instead of `"key":[`. */
    private val brokenKeySyntax = Regex(""""(\w+)=([{\[])""")

    private fun fixJsonSyntax(raw: String): String = brokenKeySyntax.replace(raw) { "\"${it.groupValues[1]}\":${it.groupValues[2]}" }

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
        // Last structural char emitted outside of strings. Used to detect `,{` / `,[`
        // inside an object, which signals a missing closing `}` before the next array
        // element (LLMs sometimes forget to close each object in an array of objects).
        var lastSig: Char = ' '

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
                lastSig = c
                continue
            }
            if (inString) {
                result.append(c)
                continue
            }
            if (c.isWhitespace()) {
                result.append(c)
                continue
            }
            when (c) {
                '{', '[' -> {
                    // Repair `,{` or `,[` appearing where an object expects a key: the
                    // LLM forgot to close the object before the next array element.
                    // Only repair when the parent of the open object is an array.
                    if (lastSig == ',' &&
                        stack.lastOrNull() == '{' &&
                        stack.getOrNull(stack.size - 2) == '['
                    ) {
                        val commaIdx = result.lastIndexOf(',')
                        if (commaIdx >= 0) {
                            result.insert(commaIdx, '}')
                            stack.removeAt(stack.lastIndex)
                        }
                    }
                    stack.add(c)
                    result.append(c)
                    lastSig = c
                }

                '}' -> if (stack.isNotEmpty() && stack.last() == '{') {
                    stack.removeAt(stack.lastIndex)
                    result.append(c)
                    lastSig = c
                }

                ']' -> if (stack.isNotEmpty() && stack.last() == '[') {
                    stack.removeAt(stack.lastIndex)
                    result.append(c)
                    lastSig = c
                }

                else -> {
                    result.append(c)
                    lastSig = c
                }
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
}
