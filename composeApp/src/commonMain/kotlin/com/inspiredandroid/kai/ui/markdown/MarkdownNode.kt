package com.inspiredandroid.kai.ui.markdown

import androidx.compose.runtime.Immutable
import com.inspiredandroid.kai.ui.dynamicui.KaiUiNode
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class MarkdownDocument(val blocks: ImmutableList<BlockNode>)

@Immutable
sealed interface BlockNode

@Immutable
data class Heading(val level: Int, val inlines: ImmutableList<InlineNode>) : BlockNode

@Immutable
data class Paragraph(val inlines: ImmutableList<InlineNode>) : BlockNode

@Immutable
data class CodeFence(
    val language: String?,
    val code: String,
    val closed: Boolean,
) : BlockNode

@Immutable
data class Blockquote(val children: ImmutableList<BlockNode>) : BlockNode

@Immutable
data class BulletList(val items: ImmutableList<ListItem>, val tight: Boolean) : BlockNode

@Immutable
data class OrderedList(
    val start: Int,
    val items: ImmutableList<ListItem>,
    val tight: Boolean,
) : BlockNode

@Immutable
data class ListItem(val children: ImmutableList<BlockNode>)

@Immutable
data class Table(
    val headers: ImmutableList<ImmutableList<InlineNode>>,
    val alignments: ImmutableList<ColumnAlign>,
    val rows: ImmutableList<ImmutableList<ImmutableList<InlineNode>>>,
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
data class Emphasis(val children: ImmutableList<InlineNode>) : InlineNode

@Immutable
data class Strong(val children: ImmutableList<InlineNode>) : InlineNode

@Immutable
data class Strike(val children: ImmutableList<InlineNode>) : InlineNode

@Immutable
data class InlineCode(val code: String) : InlineNode

@Immutable
data class Link(val href: String, val children: ImmutableList<InlineNode>) : InlineNode

@Immutable
data class Image(val src: String, val alt: String) : InlineNode

@Immutable
data object LineBreak : InlineNode

@Immutable
data class InlineMath(val latex: String) : InlineNode
