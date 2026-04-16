package com.inspiredandroid.kai.ui.markdown

/**
 * Parse markdown text into a [MarkdownDocument].
 *
 * The parser targets the subset of CommonMark / GFM that LLM chat output actually uses; see
 * [BlockScanner] for the block-level scope and [InlineTokenizer] for the inline scope.
 *
 * Robust to streaming input: unclosed code fences, unterminated emphasis, or partial links
 * degrade to their nearest sensible rendering instead of throwing. The returned document is
 * always well-formed and renderable.
 */
fun parseMarkdown(text: String): MarkdownDocument {
    if (text.isEmpty()) return MarkdownDocument(emptyList())
    return try {
        MarkdownDocument(BlockScanner.scan(text))
    } catch (_: Throwable) {
        MarkdownDocument(listOf(Paragraph(listOf(Text(text)))))
    }
}
