package com.inspiredandroid.kai.ui.markdown

import com.inspiredandroid.kai.ui.dynamicui.collectSpeakableText

/**
 * TTS-friendly text extracted from a parsed [MarkdownDocument]. Strips markdown formatting,
 * drops code blocks, reads link text (not URLs), and walks kai-ui blocks for their human-
 * readable labels.
 */
fun MarkdownDocument.toSpeakableText(): String {
    val pieces = blocks.mapNotNull { blockToSpeakable(it).takeIf { p -> p.isNotBlank() } }
    return pieces.joinToString("\n\n").trim()
}

/**
 * Flat plain text with all markdown formatting removed. Useful for copy-to-clipboard fallbacks.
 */
fun MarkdownDocument.toPlainText(): String {
    val pieces = blocks.mapNotNull { blockToPlain(it).takeIf { p -> p.isNotBlank() } }
    return pieces.joinToString("\n\n").trim()
}

private fun blockToSpeakable(block: BlockNode): String = when (block) {
    is Heading -> inlinesToText(block.inlines)
    is Paragraph -> inlinesToText(block.inlines)
    is CodeFence -> ""
    is Blockquote -> block.children.joinToString(". ") { blockToSpeakable(it) }.trim()
    is BulletList -> block.items.joinToString("\n") { itemToSpeakable(it) }
    is OrderedList -> block.items.joinToString("\n") { itemToSpeakable(it) }
    is Table -> tableToSpeakable(block)
    HorizontalRule -> ""
    is KaiUiBlock -> block.node.collectSpeakableText()
    is KaiUiError -> ""
}

private fun itemToSpeakable(item: ListItem): String {
    val text = item.children.joinToString(". ") { blockToSpeakable(it) }.trim()
    return ensureSentenceEnd(text)
}

private fun ensureSentenceEnd(text: String): String {
    if (text.isEmpty()) return text
    val last = text.last()
    return if (last == '.' || last == '?' || last == '!') text else "$text."
}

private fun tableToSpeakable(table: Table): String {
    val pieces = mutableListOf<String>()
    if (table.headers.any { it.isNotEmpty() }) {
        pieces += table.headers.joinToString(", ") { inlinesToText(it) }
    }
    for (row in table.rows) {
        pieces += row.joinToString(", ") { inlinesToText(it) }
    }
    return pieces.joinToString(". ")
}

private fun inlinesToText(inlines: List<InlineNode>): String {
    val sb = StringBuilder()
    for (n in inlines) appendInline(sb, n)
    return sb.toString()
}

private fun appendInline(sb: StringBuilder, node: InlineNode) {
    when (node) {
        is Text -> sb.append(node.value)
        is Emphasis -> node.children.forEach { appendInline(sb, it) }
        is Strong -> node.children.forEach { appendInline(sb, it) }
        is Strike -> node.children.forEach { appendInline(sb, it) }
        is InlineCode -> sb.append(node.code)
        is Link -> node.children.forEach { appendInline(sb, it) }
        is Image -> sb.append(node.alt)
        LineBreak -> sb.append(' ')
    }
}

private fun blockToPlain(block: BlockNode): String = when (block) {
    is Heading -> inlinesToText(block.inlines)

    is Paragraph -> inlinesToText(block.inlines)

    is CodeFence -> block.code

    is Blockquote -> block.children.joinToString("\n") { blockToPlain(it) }

    is BulletList -> block.items.joinToString("\n") { "- " + itemToPlain(it) }

    is OrderedList -> block.items.mapIndexed { index, item ->
        "${block.start + index}. " + itemToPlain(item)
    }.joinToString("\n")

    is Table -> tableToPlain(block)

    HorizontalRule -> ""

    is KaiUiBlock -> block.node.collectSpeakableText()

    is KaiUiError -> ""
}

private fun itemToPlain(item: ListItem): String = item.children.joinToString("\n") { blockToPlain(it) }.trim()

private fun tableToPlain(table: Table): String {
    val rows = mutableListOf<String>()
    if (table.headers.isNotEmpty()) {
        rows += table.headers.joinToString("\t") { inlinesToText(it) }
    }
    for (row in table.rows) {
        rows += row.joinToString("\t") { inlinesToText(it) }
    }
    return rows.joinToString("\n")
}
