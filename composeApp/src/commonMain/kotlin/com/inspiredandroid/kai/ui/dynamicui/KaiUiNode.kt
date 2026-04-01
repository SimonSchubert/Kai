package com.inspiredandroid.kai.ui.dynamicui

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Immutable
@Serializable
sealed interface KaiUiNode {
    val id: String?
}

// --- Layout nodes ---

@Immutable
@Serializable
@SerialName("column")
data class ColumnNode(
    override val id: String? = null,
    val children: List<KaiUiNode> = emptyList(),
    val spacing: Int? = null,
    val padding: Int? = null,
) : KaiUiNode

@Immutable
@Serializable
@SerialName("row")
data class RowNode(
    override val id: String? = null,
    val children: List<KaiUiNode> = emptyList(),
    val spacing: Int? = null,
    val padding: Int? = null,
) : KaiUiNode

@Immutable
@Serializable
@SerialName("card")
data class CardNode(
    override val id: String? = null,
    val children: List<KaiUiNode> = emptyList(),
    val padding: Int? = null,
) : KaiUiNode

@Immutable
@Serializable
@SerialName("spacer")
data class SpacerNode(
    override val id: String? = null,
    val height: Int? = null,
) : KaiUiNode

@Immutable
@Serializable
@SerialName("divider")
data class DividerNode(
    override val id: String? = null,
) : KaiUiNode

// --- Content nodes ---

@Immutable
@Serializable
@SerialName("text")
data class TextNode(
    override val id: String? = null,
    val value: String,
    val style: TextNodeStyle? = null,
    val bold: Boolean? = null,
    val italic: Boolean? = null,
    val color: String? = null,
) : KaiUiNode

@Immutable
@Serializable
@SerialName("image")
data class ImageNode(
    override val id: String? = null,
    val url: String,
    val alt: String? = null,
    val height: Int? = null,
) : KaiUiNode

// --- Interactive nodes ---

@Immutable
@Serializable
@SerialName("button")
data class ButtonNode(
    override val id: String? = null,
    val label: String,
    val action: UiAction,
    val variant: ButtonVariant? = null,
    val enabled: Boolean? = null,
) : KaiUiNode

@Immutable
@Serializable
@SerialName("text_input")
data class TextInputNode(
    override val id: String,
    val label: String? = null,
    val placeholder: String? = null,
    val value: String? = null,
    val multiline: Boolean? = null,
) : KaiUiNode

@Immutable
@Serializable
@SerialName("checkbox")
data class CheckboxNode(
    override val id: String,
    val label: String,
    val checked: Boolean? = null,
) : KaiUiNode

@Immutable
@Serializable
@SerialName("select")
data class SelectNode(
    override val id: String,
    val label: String? = null,
    val options: List<String>,
    val selected: String? = null,
) : KaiUiNode

// --- Data display nodes ---

@Immutable
@Serializable
@SerialName("list")
data class ListNode(
    override val id: String? = null,
    val items: List<KaiUiNode> = emptyList(),
    val ordered: Boolean? = null,
) : KaiUiNode

@Immutable
@Serializable
@SerialName("table")
data class TableNode(
    override val id: String? = null,
    val headers: List<String> = emptyList(),
    val rows: List<List<String>> = emptyList(),
) : KaiUiNode

// --- Enums ---

@Serializable
enum class TextNodeStyle {
    @SerialName("headline")
    HEADLINE,

    @SerialName("title")
    TITLE,

    @SerialName("body")
    BODY,

    @SerialName("caption")
    CAPTION,
}

@Serializable
enum class ButtonVariant {
    @SerialName("filled")
    FILLED,
}
