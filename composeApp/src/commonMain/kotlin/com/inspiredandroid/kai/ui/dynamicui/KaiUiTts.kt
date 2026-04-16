package com.inspiredandroid.kai.ui.dynamicui

import com.inspiredandroid.kai.stripMarkdownForTts

/**
 * TTS-friendly text for a message that may contain kai-ui fences. Markdown between/around
 * the fences is stripped of formatting (see `stripMarkdownForTts`); kai-ui segments are
 * walked for human-readable node content (text, labels, titles, alerts, chips, table cells)
 * so the user hears what the form says, not the JSON behind it.
 */
fun String.toSpeakableText(): String {
    val pieces = KaiUiParser.parse(this).mapNotNull { segment ->
        when (segment) {
            is KaiUiParser.MarkdownSegment -> segment.content.stripMarkdownForTts().takeIf { it.isNotBlank() }
            is KaiUiParser.UiSegment -> segment.node.collectSpeakableText().takeIf { it.isNotBlank() }
            is KaiUiParser.ErrorSegment -> null
        }
    }
    return pieces.joinToString(". ").trim()
}

private fun KaiUiNode.collectSpeakableText(): String {
    val parts = mutableListOf<String>()
    walk(parts)
    return parts.asSequence().filter { it.isNotBlank() }.joinToString(". ")
}

private fun KaiUiNode.walk(parts: MutableList<String>) {
    when (this) {
        is TextNode -> parts += value
        is ButtonNode -> parts += label
        is TextInputNode -> (value ?: label ?: placeholder)?.let { parts += it }
        is CheckboxNode -> parts += label
        is SwitchNode -> parts += label
        is SliderNode -> label?.let { parts += it }
        is SelectNode -> {
            label?.let { parts += it }
            selected?.let { parts += it }
        }

        is RadioGroupNode -> {
            label?.let { parts += it }
            selected?.let { parts += it }
        }

        is ChipGroupNode -> chips.forEach { parts += it.label }
        is ProgressNode -> label?.let { parts += it }
        is CountdownNode -> label?.let { parts += it }
        is AlertNode -> {
            title?.takeIf { it.isNotBlank() }?.let { parts += it }
            parts += message
        }

        is QuoteNode -> {
            parts += text
            source?.let { parts += it }
        }

        is BadgeNode -> parts += value
        is StatNode -> {
            parts += label
            parts += value
            description?.let { parts += it }
        }

        is AvatarNode -> name?.let { parts += it }
        is ImageNode -> alt?.let { parts += it }
        is AccordionNode -> {
            parts += title
            children.forEach { it.walk(parts) }
        }

        is ColumnNode -> children.forEach { it.walk(parts) }
        is RowNode -> children.forEach { it.walk(parts) }
        is CardNode -> children.forEach { it.walk(parts) }
        is BoxNode -> children.forEach { it.walk(parts) }
        is ListNode -> items.forEach { it.walk(parts) }
        is TabsNode -> tabs.forEach { tab ->
            parts += tab.label
            tab.children.forEach { it.walk(parts) }
        }

        is TableNode -> {
            if (headers.isNotEmpty()) parts += headers.joinToString(", ")
            rows.forEach { parts += it.joinToString(", ") }
        }

        is CodeNode -> Unit
        is IconNode -> Unit
        is DividerNode -> Unit
    }
}
