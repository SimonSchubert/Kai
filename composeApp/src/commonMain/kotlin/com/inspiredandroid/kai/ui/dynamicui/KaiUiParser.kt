package com.inspiredandroid.kai.ui.dynamicui

import com.inspiredandroid.kai.data.SharedJson

/**
 * Decodes the body of a `kai-ui` fenced JSON block into a [KaiUiNode].
 *
 * The parse pipeline runs each block through three stages:
 *   1. **Block extraction** — the markdown parser locates `kai-ui` fences and hands the raw
 *      body to [parseUiBlockBody].
 *   2. **Syntax repair** — fix broken key syntax, trim mismatched braces, close truncated JSON
 *      so `parseToJsonElement` can succeed.
 *   3. **Direct build** — walk the resulting [kotlinx.serialization.json.JsonElement] tree via
 *      [parseNode] in `KaiUiNodeBuilders.kt`, constructing [KaiUiNode] instances field-by-field.
 *      Each reader tolerates common LLM mistakes locally, so missing or miscoerced fields fall
 *      back to their data-class defaults and the node still builds.
 *
 * Only the `parseToJsonElement` call in stage 3 can produce a [UiBlockResult.Error];
 * everything downstream of that returns a best-effort node or a null that callers filter out.
 */
object KaiUiParser {

    // =========================================================================================
    // Public API
    // =========================================================================================

    /** Result of decoding a kai-ui fence body; consumed by the markdown parser. */
    sealed interface UiBlockResult {
        data class Ui(val node: KaiUiNode, val rawJson: String) : UiBlockResult
        data class Error(val rawJson: String) : UiBlockResult
    }

    /**
     * Decode the raw body of a kai-ui fence (everything between the opening and closing triple
     * backticks). Returns either a decoded [KaiUiNode] or an [UiBlockResult.Error] carrying the
     * repaired JSON so callers can display it as a code block.
     *
     * Supports two shapes:
     *  - A single JSON object (e.g. `{"type":"column", ...}`)
     *  - NDJSON: one object per line (wrapped in an implicit `ColumnNode` by callers that want
     *    the historical behavior, but returned here as-is for per-node composition).
     */
    fun parseUiBlockBody(rawBlock: String): UiBlockResult? {
        val repaired = fixJsonSyntax(rawBlock)
        val lines = repaired.lines().map { it.trim() }.filter { it.isNotEmpty() }

        if (lines.size > 1 && lines.all { it.startsWith("{") }) {
            val children = lines.mapNotNull { tryParseLine(it) }
            return if (children.isNotEmpty()) {
                UiBlockResult.Ui(ColumnNode(children = children), repaired)
            } else {
                UiBlockResult.Error(repaired)
            }
        }

        val json = sanitizeJson(repaired)
        return try {
            parseSingleNode(json)?.let { UiBlockResult.Ui(it, json) }
        } catch (e: Exception) {
            println("kai-ui parse error: ${e.message} | ${json.take(500)}")
            UiBlockResult.Error(json)
        }
    }

    // =========================================================================================
    // Internals
    // =========================================================================================

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
