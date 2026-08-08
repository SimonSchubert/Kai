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

private fun blockToSpeakable(block: BlockNode): String = when (block) {
    is Heading -> inlinesToText(block.inlines)
    is Paragraph -> inlinesToText(block.inlines)
    is CodeFence -> ""
    is Blockquote -> block.children.joinToString(". ") { blockToSpeakable(it) }.trim()
    is BulletList -> block.items.joinToString("\n") { itemToSpeakable(it) }
    is OrderedList -> block.items.joinToString("\n") { itemToSpeakable(it) }
    is Table -> tableToSpeakable(block)
    HorizontalRule -> ""
    is DisplayMath -> block.latex
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
        is InlineMath -> sb.append(node.latex)
    }
}
