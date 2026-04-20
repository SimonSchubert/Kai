package com.inspiredandroid.kai.ui.markdown

import com.inspiredandroid.kai.ui.dynamicui.KaiUiParser

/**
 * Line-based block splitter. Scans raw markdown text into a list of [BlockNode]s.
 *
 * Scope (LLM-output subset — not full CommonMark):
 *  - ATX and setext headings
 *  - Fenced code blocks with info-string (```/~~~), including `kai-ui` and the split-block
 *    pattern (`kai-ui` on its own line followed by a `json` fence) from legacy LLM outputs.
 *  - Horizontal rules (`---`, `***`, `___`)
 *  - Blockquotes (`> `)
 *  - Bullet lists (`-`, `*`, `+`) and ordered lists (`1.`, `1)`), with nesting by indent
 *  - GFM tables (pipe-delimited with alignment separator row)
 *  - Paragraphs
 *
 * Not supported: reference-style links, HTML blocks, footnotes, task lists, definition lists.
 */
internal object BlockScanner {

    private val FENCE_REGEX = Regex("""^(\s{0,3})(`{3,}|~{3,})\s*(.*?)\s*$""")
    private val MATH_DISPLAY_INLINE_REGEX = Regex("""^\s*\$\$([\s\S]+?)\$\$\s*$""")
    private val MATH_DISPLAY_BRACKET_INLINE_REGEX = Regex("""^\s*\\\[([\s\S]+?)\\\]\s*$""")
    private val MATH_DISPLAY_DOLLAR_FENCE_REGEX = Regex("""^\s*\$\$\s*$""")
    private val MATH_DISPLAY_BRACKET_OPEN_REGEX = Regex("""^\s*\\\[\s*$""")
    private val MATH_DISPLAY_BRACKET_CLOSE_REGEX = Regex("""^\s*\\\]\s*$""")

    private enum class MathFence { Dollars, Brackets }
    private val ATX_HEADING_REGEX = Regex("""^\s{0,3}(#{1,6})(?:\s+(.*?))?\s*#*\s*$""")
    private val SETEXT_H1_REGEX = Regex("""^\s{0,3}=+\s*$""")
    private val SETEXT_H2_REGEX = Regex("""^\s{0,3}-+\s*$""")
    private val HR_REGEX = Regex(
        """^\s{0,3}(?:-(?:[ \t]*-){2,}|\*(?:[ \t]*\*){2,}|_(?:[ \t]*_){2,})\s*$""",
    )
    private val BLOCKQUOTE_REGEX = Regex("""^\s{0,3}>\s?(.*)$""")
    private val BULLET_REGEX = Regex("""^(\s*)([-*+])(\s+)(.*)$""")
    private val ORDERED_REGEX = Regex("""^(\s*)(\d{1,9})([.)])(\s+)(.*)$""")
    private val TABLE_SEPARATOR_REGEX = Regex("""^\s*\|?\s*:?-+:?\s*(\|\s*:?-+:?\s*)+\|?\s*$""")

    private const val MAX_BLOCK_DEPTH = 32
    private const val MAX_LINE_REGEX_LEN = 10_000

    fun scan(text: String): List<BlockNode> {
        val normalized = text.replace("\r\n", "\n").replace("\r", "\n")
        val lines = normalized.split("\n")
        return scanLines(lines, 0, lines.size, 0)
    }

    private fun scanLines(lines: List<String>, start: Int, end: Int, depth: Int): List<BlockNode> {
        if (depth >= MAX_BLOCK_DEPTH) return flattenToParagraph(lines, start, end)
        val blocks = mutableListOf<BlockNode>()
        var i = start
        while (i < end) {
            val line = lines[i]
            if (line.isBlank()) {
                i++
                continue
            }
            if (line.length > MAX_LINE_REGEX_LEN) {
                val (paragraph, next) = parseParagraph(lines, i, end)
                blocks += paragraph
                i = next
                continue
            }

            val fenceMatch = FENCE_REGEX.matchEntire(line)
            if (fenceMatch != null) {
                val (block, next) = parseFence(lines, i, end, fenceMatch)
                blocks += block
                i = next
                continue
            }

            val mathInline = MATH_DISPLAY_INLINE_REGEX.matchEntire(line)
                ?: MATH_DISPLAY_BRACKET_INLINE_REGEX.matchEntire(line)
            if (mathInline != null) {
                blocks += DisplayMath(mathInline.groupValues[1].trim())
                i++
                continue
            }
            if (MATH_DISPLAY_DOLLAR_FENCE_REGEX.matchEntire(line) != null) {
                val (math, next) = parseDisplayMath(lines, i, end, MathFence.Dollars)
                blocks += math
                i = next
                continue
            }
            if (MATH_DISPLAY_BRACKET_OPEN_REGEX.matchEntire(line) != null) {
                val (math, next) = parseDisplayMath(lines, i, end, MathFence.Brackets)
                blocks += math
                i = next
                continue
            }

            if (line.trim() == "kai-ui") {
                val splitResult = tryParseKaiUiSplit(lines, i, end)
                if (splitResult != null) {
                    blocks += splitResult.first
                    i = splitResult.second
                    continue
                }
            }

            val atx = ATX_HEADING_REGEX.matchEntire(line)
            if (atx != null) {
                val level = atx.groupValues[1].length
                val content = atx.groupValues[2]
                blocks += Heading(level, InlineTokenizer.tokenize(content.trim()))
                i++
                continue
            }

            if (i + 1 < end && !isListOpener(line) && !isBlockquoteOpener(line) &&
                HR_REGEX.matchEntire(line) == null
            ) {
                val next = lines[i + 1]
                if (SETEXT_H1_REGEX.matchEntire(next) != null) {
                    blocks += Heading(1, InlineTokenizer.tokenize(line.trim()))
                    i += 2
                    continue
                }
                if (SETEXT_H2_REGEX.matchEntire(next) != null) {
                    blocks += Heading(2, InlineTokenizer.tokenize(line.trim()))
                    i += 2
                    continue
                }
            }

            if (HR_REGEX.matchEntire(line) != null) {
                blocks += HorizontalRule
                i++
                continue
            }

            if (BLOCKQUOTE_REGEX.matchEntire(line) != null) {
                val (bq, next) = parseBlockquote(lines, i, end, depth)
                blocks += bq
                i = next
                continue
            }

            val bullet = BULLET_REGEX.matchEntire(line)
            val ordered = ORDERED_REGEX.matchEntire(line)
            if (bullet != null || ordered != null) {
                val (list, next) = parseList(lines, i, end, isOrdered = ordered != null, depth = depth)
                blocks += list
                i = next
                continue
            }

            if (line.contains('|') && i + 1 < end &&
                TABLE_SEPARATOR_REGEX.matchEntire(lines[i + 1]) != null
            ) {
                val tableResult = parseTable(lines, i, end)
                if (tableResult != null) {
                    blocks += tableResult.first
                    i = tableResult.second
                    continue
                }
            }

            val (paragraph, next) = parseParagraph(lines, i, end)
            blocks += paragraph
            i = next
        }
        return blocks
    }

    // =========================================================================================
    // Fenced code (including kai-ui)
    // =========================================================================================

    private fun parseFence(
        lines: List<String>,
        start: Int,
        end: Int,
        openerMatch: MatchResult,
    ): Pair<BlockNode, Int> {
        val indent = openerMatch.groupValues[1].length
        val fence = openerMatch.groupValues[2]
        val fenceChar = fence[0]
        val fenceLen = fence.length
        val info = openerMatch.groupValues[3].trim()
        val (body, closed, next) = readFenceBody(lines, start + 1, end, fenceChar, fenceLen, indent)

        if (info.equals("kai-ui", ignoreCase = true)) {
            return decodeKaiUi(body) to next
        }

        if (info.equals("latex", ignoreCase = true) ||
            info.equals("tex", ignoreCase = true) ||
            info.equals("math", ignoreCase = true)
        ) {
            return DisplayMath(body.trim()) to next
        }

        val language = info.takeIf { it.isNotEmpty() }
        return CodeFence(language, body, closed) to next
    }

    private data class FenceBody(val body: String, val closed: Boolean, val nextIndex: Int)

    private fun readFenceBody(
        lines: List<String>,
        start: Int,
        end: Int,
        fenceChar: Char,
        fenceLen: Int,
        indent: Int,
    ): FenceBody {
        val bodyLines = mutableListOf<String>()
        var i = start
        while (i < end) {
            val l = lines[i]
            val closer = FENCE_REGEX.matchEntire(l)
            if (closer != null) {
                val c = closer.groupValues[2]
                if (c.isNotEmpty() && c[0] == fenceChar && c.length >= fenceLen && closer.groupValues[3].isBlank()) {
                    return FenceBody(bodyLines.joinToString("\n"), closed = true, nextIndex = i + 1)
                }
            }
            bodyLines += stripIndent(l, indent)
            i++
        }
        return FenceBody(bodyLines.joinToString("\n"), closed = false, nextIndex = i)
    }

    private fun stripIndent(line: String, indent: Int): String {
        if (indent == 0) return line
        var strip = 0
        while (strip < indent && strip < line.length && line[strip] == ' ') strip++
        return line.substring(strip)
    }

    private fun decodeKaiUi(body: String): BlockNode = when (val result = KaiUiParser.parseUiBlockBody(body)) {
        is KaiUiParser.UiBlockResult.Ui -> KaiUiBlock(result.node, result.rawJson)
        is KaiUiParser.UiBlockResult.Error -> KaiUiError(result.rawJson)
        null -> KaiUiError(body)
    }

    private fun parseDisplayMath(
        lines: List<String>,
        start: Int,
        end: Int,
        fence: MathFence,
    ): Pair<BlockNode, Int> {
        val closerRegex = when (fence) {
            MathFence.Dollars -> MATH_DISPLAY_DOLLAR_FENCE_REGEX
            MathFence.Brackets -> MATH_DISPLAY_BRACKET_CLOSE_REGEX
        }
        val bodyLines = mutableListOf<String>()
        var i = start + 1
        while (i < end) {
            if (closerRegex.matchEntire(lines[i]) != null) {
                return DisplayMath(bodyLines.joinToString("\n").trim()) to (i + 1)
            }
            bodyLines += lines[i]
            i++
        }
        // Unclosed: tolerate for streaming — emit what we have.
        return DisplayMath(bodyLines.joinToString("\n").trim()) to i
    }

    private fun tryParseKaiUiSplit(
        lines: List<String>,
        start: Int,
        end: Int,
    ): Pair<BlockNode, Int>? {
        var j = start + 1
        while (j < end && lines[j].isBlank()) j++
        if (j >= end) return null
        val fence = FENCE_REGEX.matchEntire(lines[j]) ?: return null
        val info = fence.groupValues[3].trim()
        if (info.isNotEmpty() && !info.equals("json", ignoreCase = true)) return null
        val fenceChar = fence.groupValues[2][0]
        val fenceLen = fence.groupValues[2].length
        val indent = fence.groupValues[1].length
        val (body, _, next) = readFenceBody(lines, j + 1, end, fenceChar, fenceLen, indent)
        return decodeKaiUi(body) to next
    }

    // =========================================================================================
    // Blockquote
    // =========================================================================================

    private fun parseBlockquote(lines: List<String>, start: Int, end: Int, depth: Int): Pair<BlockNode, Int> {
        val inner = mutableListOf<String>()
        var i = start
        while (i < end) {
            val m = BLOCKQUOTE_REGEX.matchEntire(lines[i])
            if (m == null) {
                if (lines[i].isBlank()) break
                inner += lines[i]
                i++
                continue
            }
            inner += m.groupValues[1]
            i++
        }
        val children = scanLines(inner, 0, inner.size, depth + 1)
        return Blockquote(children) to i
    }

    private fun flattenToParagraph(lines: List<String>, start: Int, end: Int): List<BlockNode> {
        val text = (start until end).joinToString("\n") { lines[it] }.trim()
        if (text.isEmpty()) return emptyList()
        return listOf(Paragraph(InlineTokenizer.tokenize(text)))
    }

    // =========================================================================================
    // Lists
    // =========================================================================================

    private fun isListOpener(line: String): Boolean = BULLET_REGEX.matchEntire(line) != null || ORDERED_REGEX.matchEntire(line) != null

    private fun isBlockquoteOpener(line: String): Boolean = BLOCKQUOTE_REGEX.matchEntire(line) != null

    private fun parseList(
        lines: List<String>,
        start: Int,
        end: Int,
        isOrdered: Boolean,
        depth: Int,
    ): Pair<BlockNode, Int> {
        val firstMatch = if (isOrdered) ORDERED_REGEX.matchEntire(lines[start])!! else BULLET_REGEX.matchEntire(lines[start])!!
        val listIndent = firstMatch.groupValues[1].length
        val startNum = if (isOrdered) firstMatch.groupValues[2].toIntOrNull() ?: 1 else 1

        val items = mutableListOf<ListItem>()
        var i = start
        var sawBlankBetweenItems = false

        while (i < end) {
            val line = lines[i]
            if (line.isBlank()) {
                var k = i + 1
                while (k < end && lines[k].isBlank()) k++
                if (k >= end) break
                val m = if (isOrdered) ORDERED_REGEX.matchEntire(lines[k]) else BULLET_REGEX.matchEntire(lines[k])
                if (m == null || m.groupValues[1].length != listIndent) break
                sawBlankBetweenItems = true
                i = k
                continue
            }

            val match = if (isOrdered) ORDERED_REGEX.matchEntire(line) else BULLET_REGEX.matchEntire(line)
            if (match == null || match.groupValues[1].length != listIndent) break

            val marker = if (isOrdered) match.groupValues[2] + match.groupValues[3] else match.groupValues[2]
            val spacing = if (isOrdered) match.groupValues[4] else match.groupValues[3]
            val content = if (isOrdered) match.groupValues[5] else match.groupValues[4]
            val contentCol = listIndent + marker.length + spacing.length

            val itemLines = mutableListOf(content)
            var j = i + 1
            while (j < end) {
                val l = lines[j]
                if (l.isBlank()) {
                    var k = j + 1
                    while (k < end && lines[k].isBlank()) k++
                    if (k >= end) break
                    val lk = lines[k]
                    if (isSiblingOrOuterMarker(lk, listIndent)) break
                    if (indentOf(lk) < contentCol) break
                    itemLines += ""
                    j++
                    continue
                }
                if (isSiblingOrOuterMarker(l, listIndent)) break
                val lineIndent = indentOf(l)
                if (lineIndent >= contentCol) {
                    itemLines += l.substring(contentCol.coerceAtMost(l.length))
                } else {
                    itemLines += l.trimStart()
                }
                j++
            }
            while (itemLines.isNotEmpty() && itemLines.last().isBlank()) {
                itemLines.removeAt(itemLines.lastIndex)
            }

            val children = scanLines(itemLines, 0, itemLines.size, depth + 1)
            items += ListItem(children)
            i = j
        }

        val tight = !sawBlankBetweenItems
        val listBlock = if (isOrdered) OrderedList(startNum, items, tight) else BulletList(items, tight)
        return listBlock to i
    }

    private fun isSiblingOrOuterMarker(line: String, currentIndent: Int): Boolean {
        val bm = BULLET_REGEX.matchEntire(line)
        val om = ORDERED_REGEX.matchEntire(line)
        val indent = when {
            bm != null -> bm.groupValues[1].length
            om != null -> om.groupValues[1].length
            else -> return false
        }
        return indent <= currentIndent
    }

    private fun indentOf(line: String): Int {
        var n = 0
        while (n < line.length && line[n] == ' ') n++
        return n
    }

    // =========================================================================================
    // Table
    // =========================================================================================

    private fun parseTable(
        lines: List<String>,
        start: Int,
        end: Int,
    ): Pair<BlockNode, Int>? {
        val headerCells = splitRow(lines[start])
        val sepCells = splitRow(lines[start + 1])
        if (headerCells.isEmpty() || sepCells.size != headerCells.size) return null

        val alignments = sepCells.map { cell ->
            val t = cell.trim()
            when {
                t.startsWith(":") && t.endsWith(":") -> ColumnAlign.CENTER
                t.endsWith(":") -> ColumnAlign.RIGHT
                t.startsWith(":") -> ColumnAlign.LEFT
                else -> ColumnAlign.NONE
            }
        }

        val headers = headerCells.map { InlineTokenizer.tokenize(it.trim()) }
        val rows = mutableListOf<List<List<InlineNode>>>()
        var i = start + 2
        while (i < end) {
            val l = lines[i]
            if (l.isBlank()) break
            if (!l.contains('|')) break
            val cells = splitRow(l)
            val padded = if (cells.size < headers.size) {
                cells + List(headers.size - cells.size) { "" }
            } else {
                cells.take(headers.size)
            }
            rows += padded.map { InlineTokenizer.tokenize(it.trim()) }
            i++
        }
        return Table(headers, alignments, rows) to i
    }

    private fun splitRow(line: String): List<String> {
        var s = line.trim()
        if (s.startsWith("|")) s = s.substring(1)
        if (s.endsWith("|") && !s.endsWith("\\|")) s = s.substring(0, s.length - 1)
        val cells = mutableListOf<String>()
        val cur = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length && s[i + 1] == '|') {
                cur.append('|')
                i += 2
                continue
            }
            if (c == '|') {
                cells += cur.toString()
                cur.clear()
                i++
                continue
            }
            cur.append(c)
            i++
        }
        cells += cur.toString()
        return cells
    }

    // =========================================================================================
    // Paragraph
    // =========================================================================================

    private fun parseParagraph(lines: List<String>, start: Int, end: Int): Pair<BlockNode, Int> {
        val accum = StringBuilder()
        var i = start
        while (i < end) {
            val line = lines[i]
            if (line.isBlank()) break
            if (i != start) {
                if (FENCE_REGEX.matchEntire(line) != null) break
                if (ATX_HEADING_REGEX.matchEntire(line) != null) break
                if (HR_REGEX.matchEntire(line) != null) break
                if (BLOCKQUOTE_REGEX.matchEntire(line) != null) break
                if (isListOpener(line)) break
                if (line.trim() == "kai-ui") break
                if (MATH_DISPLAY_DOLLAR_FENCE_REGEX.matchEntire(line) != null) break
                if (MATH_DISPLAY_BRACKET_OPEN_REGEX.matchEntire(line) != null) break
                if (MATH_DISPLAY_INLINE_REGEX.matchEntire(line) != null) break
                if (MATH_DISPLAY_BRACKET_INLINE_REGEX.matchEntire(line) != null) break
            }
            if (accum.isNotEmpty()) accum.append('\n')
            accum.append(line)
            i++
        }
        return Paragraph(InlineTokenizer.tokenize(accum.toString())) to i
    }
}
