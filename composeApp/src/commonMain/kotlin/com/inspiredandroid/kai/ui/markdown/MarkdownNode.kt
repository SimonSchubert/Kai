package com.inspiredandroid.kai.ui.markdown

import androidx.compose.runtime.Immutable
import com.inspiredandroid.kai.ui.dynamicui.KaiUiNode

@Immutable
data class MarkdownDocument(val blocks: List<BlockNode>)

@Immutable
sealed interface BlockNode

@Immutable
data class Heading(val level: Int, val inlines: List<InlineNode>) : BlockNode

@Immutable
data class Paragraph(val inlines: List<InlineNode>) : BlockNode

@Immutable
data class CodeFence(
    val language: String?,
    val code: String,
    val closed: Boolean,
) : BlockNode

@Immutable
data class Blockquote(val children: List<BlockNode>) : BlockNode

@Immutable
data class BulletList(val items: List<ListItem>, val tight: Boolean) : BlockNode

@Immutable
data class OrderedList(
    val start: Int,
    val items: List<ListItem>,
    val tight: Boolean,
) : BlockNode

@Immutable
data class ListItem(val children: List<BlockNode>)

@Immutable
data class Table(
    val headers: List<List<InlineNode>>,
    val alignments: List<ColumnAlign>,
    val rows: List<List<List<InlineNode>>>,
) : BlockNode

enum class ColumnAlign { LEFT, CENTER, RIGHT, NONE }

@Immutable
data object HorizontalRule : BlockNode

@Immutable
data class DisplayMath(val latex: String) : BlockNode

@Immutable
data class KaiUiBlock(val node: KaiUiNode, val rawJson: String) : BlockNode

@Immutable
data class KaiUiError(val rawJson: String) : BlockNode

@Immutable
sealed interface InlineNode

@Immutable
data class Text(val value: String) : InlineNode

@Immutable
data class Emphasis(val children: List<InlineNode>) : InlineNode

@Immutable
data class Strong(val children: List<InlineNode>) : InlineNode

@Immutable
data class Strike(val children: List<InlineNode>) : InlineNode

@Immutable
data class InlineCode(val code: String) : InlineNode

@Immutable
data class Link(val href: String, val children: List<InlineNode>) : InlineNode

@Immutable
data class Image(val src: String, val alt: String) : InlineNode

@Immutable
data object LineBreak : InlineNode

@Immutable
data class InlineMath(val latex: String) : InlineNode
