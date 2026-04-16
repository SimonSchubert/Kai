package com.inspiredandroid.kai.ui.dynamicui

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

// Tolerant field-by-field builders that turn a JsonElement tree into a KaiUiNode.
//
// Philosophy: every reader here knows the expected type because the builder calls the
// right one. Readers never throw — if a value can't be coerced, the field falls back to
// its data-class default and the node still builds. Unknown node `type` discriminators
// return null and are filtered out of `children`/`items` by readNodeList.
//
// This replaces the old field-name-keyed coercion pipeline in KaiUiParser with direct
// construction, so each LLM-mistake handler lives next to the field it owns.

// =============================================================================================
// Top-level dispatcher
// =============================================================================================

/**
 * Build a [KaiUiNode] from an arbitrary [JsonElement], or return null if the element can't
 * represent any known node type.
 */
internal fun parseNode(element: JsonElement): KaiUiNode? = when (element) {
    is JsonObject -> parseObjectNode(element)
    is JsonPrimitive -> if (element.isString) TextNode(value = element.content) else null
    else -> null
}

private fun parseObjectNode(obj: JsonObject): KaiUiNode? = when (obj.readNullableString("type")) {
    "column" -> parseColumnNode(obj)
    "row" -> parseRowNode(obj)
    "card" -> parseCardNode(obj)
    "box" -> parseBoxNode(obj)
    "divider" -> parseDividerNode(obj)
    "text" -> parseTextNode(obj)
    "image" -> parseImageNode(obj)
    "icon" -> parseIconNode(obj)
    "code" -> parseCodeNode(obj)
    "quote" -> parseQuoteNode(obj)
    "button" -> parseButtonNode(obj)
    "text_input" -> parseTextInputNode(obj)
    "checkbox" -> parseCheckboxNode(obj)
    "select" -> parseSelectNode(obj)
    "switch" -> parseSwitchNode(obj)
    "slider" -> parseSliderNode(obj)
    "radio_group" -> parseRadioGroupNode(obj)
    "chip_group" -> parseChipGroupNode(obj)
    "progress" -> parseProgressNode(obj)
    "alert" -> parseAlertNode(obj)
    "countdown" -> parseCountdownNode(obj)
    "badge" -> parseBadgeNode(obj)
    "stat" -> parseStatNode(obj)
    "avatar" -> parseAvatarNode(obj)
    "list" -> parseListNode(obj)
    "table" -> parseTableNode(obj)
    "tabs" -> parseTabsNode(obj)
    "accordion" -> parseAccordionNode(obj)
    null -> inferBareObject(obj)
    else -> null // unknown type → silently dropped
}

// =============================================================================================
// Scalar readers
// =============================================================================================

/**
 * Preferred keys when extracting a string from an arbitrary JsonObject, in priority order.
 * Matches the old `labelKeys` constant from the previous parser.
 */
private val LABEL_KEYS = listOf("value", "text", "label", "title", "name", "content")

/**
 * Read a string field, coercing objects/arrays best-effort.
 *
 * The [default] parameter only fires when the key is absent. If the key is present but
 * holds an uncoercible object, the returned value is `""` (empty join) — not [default] —
 * so that callers can distinguish "field missing" (default) from "field present but
 * unrecoverable" (empty).
 */
internal fun JsonObject.readString(key: String, default: String = ""): String {
    val element = this[key] ?: return default
    return element.toStringLike()
}

/** Read an optional string field. Absent keys and `JsonNull` both return null. */
internal fun JsonObject.readNullableString(key: String): String? {
    val element = this[key] ?: return null
    if (element is JsonNull) return null
    return element.toStringLike()
}

/** Thin wrapper for the nullable `override val id: String?` common to most nodes. */
internal fun JsonObject.readId(): String? = readNullableString("id")

/** For interactive nodes where `id: String` is non-nullable. Absent → `""`. */
internal fun JsonObject.readRequiredId(): String = readString("id")

/**
 * Read a boolean field. Accepts:
 * - Bool primitives (`true`/`false`)
 * - String `"true"`/`"yes"`/`"1"` → true; `"false"`/`"no"`/`"0"` → false (case-insensitive)
 * - Numeric `1`/`0` → true/false
 * Anything else or absent → null.
 */
internal fun JsonObject.readNullableBoolean(key: String): Boolean? {
    val element = this[key] ?: return null
    if (element !is JsonPrimitive) return null
    element.booleanOrNull?.let { return it }
    element.longOrNull?.let {
        return if (it == 1L) {
            true
        } else if (it == 0L) {
            false
        } else {
            null
        }
    }
    if (!element.isString) return null
    return when (element.content.lowercase()) {
        "true", "yes", "1" -> true
        "false", "no", "0" -> false
        else -> null
    }
}

/**
 * Read an integer field. Accepts ints, floats (via `toInt()`), and numeric strings.
 * Anything else or absent → null.
 */
internal fun JsonObject.readNullableInt(key: String): Int? {
    val element = this[key] ?: return null
    if (element !is JsonPrimitive) return null
    element.intOrNull?.let { return it }
    element.longOrNull?.let { return it.toInt() }
    element.doubleOrNull?.let { return it.toInt() }
    return element.content.toIntOrNull()
}

/** Read an integer field with a default for the missing/uncoercible case. */
internal fun JsonObject.readInt(key: String, default: Int = 0): Int = readNullableInt(key) ?: default

/**
 * Read a float field. Accepts numeric primitives of any size and numeric strings.
 * LLMs commonly send ints like `75` for `Float?` fields — this handles that.
 */
internal fun JsonObject.readNullableFloat(key: String): Float? {
    val element = this[key] ?: return null
    if (element !is JsonPrimitive) return null
    element.doubleOrNull?.let { return it.toFloat() }
    element.longOrNull?.let { return it.toFloat() }
    return element.content.toFloatOrNull()
}

// =============================================================================================
// String-like coercion — the single source of truth for primitive/array/object → string
// =============================================================================================

/**
 * Coerce any [JsonElement] to a string, best-effort:
 * - Primitive → its `content`
 * - Array → comma-joined contents
 * - Object → first [LABEL_KEYS] primitive match, else comma-joined values
 * - JsonNull → `""`
 */
internal fun JsonElement.toStringLike(): String = when (this) {
    is JsonPrimitive -> if (this is JsonNull) "" else content

    is JsonArray -> joinToString(", ") { it.toStringLike() }

    is JsonObject -> {
        LABEL_KEYS.firstNotNullOfOrNull { (this[it] as? JsonPrimitive)?.content }
            ?: values.joinToString(", ") { it.toStringLike() }
    }
}

// =============================================================================================
// Enum parsers — hand-rolled to avoid reflection, match @SerialName strings exactly
// =============================================================================================

private fun parseTextStyle(raw: String?): TextNodeStyle? = when (raw) {
    "headline" -> TextNodeStyle.HEADLINE
    "title" -> TextNodeStyle.TITLE
    "body" -> TextNodeStyle.BODY
    "caption" -> TextNodeStyle.CAPTION
    else -> null
}

private fun parseButtonVariant(raw: String?): ButtonVariant? = when (raw) {
    "filled" -> ButtonVariant.FILLED
    "outlined" -> ButtonVariant.OUTLINED
    "text" -> ButtonVariant.TEXT
    "tonal" -> ButtonVariant.TONAL
    else -> null
}

private fun parseAlertSeverity(raw: String?): AlertSeverity? = when (raw) {
    "info" -> AlertSeverity.INFO
    "success" -> AlertSeverity.SUCCESS
    "warning" -> AlertSeverity.WARNING
    "error" -> AlertSeverity.ERROR
    else -> null
}

// =============================================================================================
// Collection readers
// =============================================================================================

/**
 * Read a list of strings. Accepts:
 * - `JsonArray` of any elements (each coerced via [toStringLike])
 * - Single primitive → `listOf(content)`
 * - Single object → `listOf(labelKey-hit)` if any
 * - Missing/null → `emptyList()`
 *
 * Covers `options`, `headers`, `collectFrom`.
 */
internal fun JsonObject.readStringList(key: String): ImmutableList<String> {
    val element = this[key] ?: return persistentListOf()
    return when (element) {
        is JsonArray -> element.map { it.toStringLike() }.toImmutableList()
        is JsonPrimitive -> if (element is JsonNull) persistentListOf() else persistentListOf(element.content)
        is JsonObject -> persistentListOf(element.toStringLike())
    }
}

/**
 * Read a list of child nodes. Accepts a `JsonArray` where each element is:
 * - A `JsonObject` → recursively built via [parseNode]; unknown types are filtered out
 * - A primitive string → wrapped as `TextNode(value = content)`
 * - `JsonNull` or anything else → skipped
 *
 * Covers `children` and `items`.
 */
internal fun JsonObject.readNodeList(key: String): ImmutableList<KaiUiNode> {
    val array = this[key] as? JsonArray ?: return persistentListOf()
    return array.mapNotNull { element ->
        when (element) {
            is JsonObject -> parseNode(element)
            is JsonPrimitive -> if (element !is JsonNull && element.isString) TextNode(value = element.content) else null
            else -> null
        }
    }.toImmutableList()
}

/**
 * Read `TableNode.rows`. Accepts:
 * - Array of arrays → each row coerced element-wise to string
 * - Array of objects → each object's values in insertion order
 * - Array of primitives → each wrapped as a single-cell row
 * - Missing → `emptyList()`
 */
internal fun JsonObject.readTableRows(key: String): ImmutableList<ImmutableList<String>> {
    val array = this[key] as? JsonArray ?: return persistentListOf()
    return array.map { row ->
        when (row) {
            is JsonArray -> row.map { it.toStringLike() }.toImmutableList()
            is JsonObject -> row.values.map { it.toStringLike() }.toImmutableList()
            is JsonPrimitive -> if (row is JsonNull) persistentListOf("") else persistentListOf(row.content)
        }
    }.toImmutableList()
}

/**
 * Read `ChipGroupNode.chips`. Accepts bare strings (wrapped as `{label=s, value=s}`)
 * or full `{label, value}` objects.
 */
internal fun JsonObject.readChipList(key: String): ImmutableList<ChipItem> {
    val array = this[key] as? JsonArray ?: return persistentListOf()
    return array.mapNotNull { item ->
        when (item) {
            is JsonPrimitive -> if (item !is JsonNull && item.isString) {
                ChipItem(label = item.content, value = item.content)
            } else {
                null
            }

            is JsonObject -> {
                val label = item.readString("label")
                val value = item.readString("value", default = label)
                ChipItem(label = label, value = value)
            }

            else -> null
        }
    }.toImmutableList()
}

/**
 * Read `TabsNode.tabs`. Accepts bare strings (wrapped with empty children) or
 * `{label, children}` objects.
 */
internal fun JsonObject.readTabList(key: String): ImmutableList<TabItem> {
    val array = this[key] as? JsonArray ?: return persistentListOf()
    return array.mapNotNull { item ->
        when (item) {
            is JsonPrimitive -> if (item !is JsonNull && item.isString) {
                TabItem(label = item.content, children = persistentListOf())
            } else {
                null
            }

            is JsonObject -> TabItem(
                label = item.readString("label"),
                children = item.readNodeList("children"),
            )

            else -> null
        }
    }.toImmutableList()
}

// =============================================================================================
// inferBareObject — typeless shortcuts used by LLMs
// =============================================================================================

/**
 * Recover a typed node from an object that lacks a `type` discriminator by matching
 * common LLM shortcuts. Returns null if nothing matches so the caller can drop the object.
 *
 * Style fields (`bold`, `italic`, `color`, `style`, `id`) on the source object are
 * carried over when constructing a `TextNode`, so `{"value":"x","bold":true}` inside a
 * `children` array keeps its formatting.
 */
private fun inferBareObject(obj: JsonObject): KaiUiNode? {
    // `{"title":"...","subtitle":"..."}` → column with styled title + caption. Checked
    // first because both fields in one object is a strong signal.
    if ("title" in obj && "subtitle" in obj) {
        return ColumnNode(
            id = obj.readId(),
            children = persistentListOf(
                TextNode(value = obj.readString("title"), style = TextNodeStyle.TITLE),
                TextNode(value = obj.readString("subtitle"), style = TextNodeStyle.CAPTION),
            ),
        )
    }

    // Prefer the most-specific text-bearing key. Priority mirrors the old LABEL_KEYS
    // order so typeless `{"value":"..."}` items inside `children`/`items` lists render
    // as text nodes. `title` gets an implicit TITLE style unless the object specifies one.
    val textKey = listOf("value", "content", "text", "title", "label").firstOrNull { it in obj }
    if (textKey != null) {
        val explicitStyle = parseTextStyle(obj.readNullableString("style"))
        val style = explicitStyle ?: if (textKey == "title") TextNodeStyle.TITLE else null
        return TextNode(
            id = obj.readId(),
            value = obj.readString(textKey),
            style = style,
            bold = obj.readNullableBoolean("bold"),
            italic = obj.readNullableBoolean("italic"),
            color = obj.readNullableString("color"),
        )
    }

    // `{"children": [...]}` with no type and no text-bearing key → treat as column
    // (preserves the old top-level block fallback for bare untyped objects).
    if ("children" in obj) {
        return parseColumnNode(obj)
    }

    return null
}

// =============================================================================================
// Layout node builders
// =============================================================================================

private fun parseColumnNode(obj: JsonObject): ColumnNode = ColumnNode(
    id = obj.readId(),
    children = obj.readNodeList("children"),
)

private fun parseRowNode(obj: JsonObject): RowNode = RowNode(
    id = obj.readId(),
    children = obj.readNodeList("children"),
)

private fun parseCardNode(obj: JsonObject): CardNode = CardNode(
    id = obj.readId(),
    children = obj.readNodeList("children"),
)

private fun parseBoxNode(obj: JsonObject): BoxNode = BoxNode(
    id = obj.readId(),
    children = obj.readNodeList("children"),
    contentAlignment = obj.readNullableString("contentAlignment"),
)

private fun parseDividerNode(obj: JsonObject): DividerNode = DividerNode(id = obj.readId())

// =============================================================================================
// Content node builders
// =============================================================================================

private fun parseTextNode(obj: JsonObject): TextNode = TextNode(
    id = obj.readId(),
    value = obj.readString("value"),
    style = parseTextStyle(obj.readNullableString("style")),
    bold = obj.readNullableBoolean("bold"),
    italic = obj.readNullableBoolean("italic"),
    color = obj.readNullableString("color"),
)

private fun parseImageNode(obj: JsonObject): ImageNode = ImageNode(
    id = obj.readId(),
    // Legacy migration: HTML-style `src` → `url`.
    url = obj.readString("url").ifEmpty { obj.readString("src") },
    alt = obj.readNullableString("alt"),
    height = obj.readNullableInt("height"),
    aspectRatio = obj.readNullableFloat("aspectRatio")
        ?: obj.readNullableFloat("aspect_ratio"),
)

private fun parseIconNode(obj: JsonObject): IconNode = IconNode(
    id = obj.readId(),
    name = obj.readString("name"),
    size = obj.readNullableInt("size"),
    color = obj.readNullableString("color"),
)

private fun parseCodeNode(obj: JsonObject): CodeNode = CodeNode(
    id = obj.readId(),
    code = obj.readString("code"),
    language = obj.readNullableString("language"),
)

private fun parseQuoteNode(obj: JsonObject): QuoteNode = QuoteNode(
    id = obj.readId(),
    text = obj.readString("text"),
    source = obj.readNullableString("source"),
)

// =============================================================================================
// Interactive node builders
// =============================================================================================

private fun parseButtonNode(obj: JsonObject): ButtonNode = ButtonNode(
    id = obj.readId(),
    label = obj.readString("label"),
    action = obj.readAction("action"),
    variant = parseButtonVariant(obj.readNullableString("variant")),
    enabled = obj.readNullableBoolean("enabled"),
)

private fun parseTextInputNode(obj: JsonObject): TextInputNode = TextInputNode(
    id = obj.readRequiredId(),
    label = obj.readNullableString("label"),
    placeholder = obj.readNullableString("placeholder"),
    value = obj.readNullableString("value"),
    multiline = obj.readNullableBoolean("multiline"),
)

private fun parseCheckboxNode(obj: JsonObject): CheckboxNode = CheckboxNode(
    id = obj.readRequiredId(),
    label = obj.readString("label"),
    checked = obj.readNullableBoolean("checked"),
)

private fun parseSelectNode(obj: JsonObject): SelectNode = SelectNode(
    id = obj.readRequiredId(),
    label = obj.readNullableString("label"),
    options = obj.readStringList("options"),
    selected = obj.readNullableString("selected"),
)

private fun parseSwitchNode(obj: JsonObject): SwitchNode = SwitchNode(
    id = obj.readRequiredId(),
    label = obj.readString("label"),
    checked = obj.readNullableBoolean("checked"),
)

private fun parseSliderNode(obj: JsonObject): SliderNode = SliderNode(
    id = obj.readRequiredId(),
    label = obj.readNullableString("label"),
    value = obj.readNullableFloat("value"),
    min = obj.readNullableFloat("min"),
    max = obj.readNullableFloat("max"),
    step = obj.readNullableFloat("step"),
)

private fun parseRadioGroupNode(obj: JsonObject): RadioGroupNode = RadioGroupNode(
    id = obj.readRequiredId(),
    label = obj.readNullableString("label"),
    options = obj.readStringList("options"),
    selected = obj.readNullableString("selected"),
)

private fun parseChipGroupNode(obj: JsonObject): ChipGroupNode {
    // Legacy migration: multiSelect:Boolean → selection:String
    val explicitSelection = obj.readString("selection")
    val selection = if (explicitSelection.isNotEmpty()) {
        explicitSelection
    } else {
        when (obj.readNullableBoolean("multiSelect")) {
            true -> "multi"
            false -> "single"
            null -> "single"
        }
    }
    return ChipGroupNode(
        id = obj.readRequiredId(),
        chips = obj.readChipList("chips"),
        selection = selection,
    )
}

// =============================================================================================
// Feedback / display node builders
// =============================================================================================

private fun parseProgressNode(obj: JsonObject): ProgressNode = ProgressNode(
    id = obj.readId(),
    value = obj.readNullableFloat("value"),
    label = obj.readNullableString("label"),
)

private fun parseAlertNode(obj: JsonObject): AlertNode = AlertNode(
    id = obj.readId(),
    message = obj.readString("message"),
    title = obj.readNullableString("title"),
    severity = parseAlertSeverity(obj.readNullableString("severity")),
)

private fun parseCountdownNode(obj: JsonObject): CountdownNode = CountdownNode(
    id = obj.readId(),
    seconds = obj.readInt("seconds"),
    label = obj.readNullableString("label"),
    action = obj.readAction("action"),
)

private fun parseBadgeNode(obj: JsonObject): BadgeNode = BadgeNode(
    id = obj.readId(),
    value = obj.readString("value"),
    color = obj.readNullableString("color"),
)

private fun parseStatNode(obj: JsonObject): StatNode = StatNode(
    id = obj.readId(),
    value = obj.readString("value"),
    label = obj.readString("label"),
    description = obj.readNullableString("description"),
)

private fun parseAvatarNode(obj: JsonObject): AvatarNode = AvatarNode(
    id = obj.readId(),
    name = obj.readNullableString("name"),
    imageUrl = obj.readNullableString("imageUrl"),
    size = obj.readNullableInt("size"),
)

// =============================================================================================
// Data display node builders
// =============================================================================================

private fun parseListNode(obj: JsonObject): ListNode = ListNode(
    id = obj.readId(),
    items = obj.readNodeList("items"),
    ordered = obj.readNullableBoolean("ordered"),
)

private fun parseTableNode(obj: JsonObject): TableNode = TableNode(
    id = obj.readId(),
    headers = obj.readStringList("headers"),
    rows = obj.readTableRows("rows"),
)

private fun parseTabsNode(obj: JsonObject): TabsNode = TabsNode(
    id = obj.readId(),
    tabs = obj.readTabList("tabs"),
    selectedIndex = obj.readNullableInt("selectedIndex"),
)

private fun parseAccordionNode(obj: JsonObject): AccordionNode = AccordionNode(
    id = obj.readId(),
    title = obj.readString("title"),
    children = obj.readNodeList("children"),
    expanded = obj.readNullableBoolean("expanded"),
)

// =============================================================================================
// Action readers
// =============================================================================================

/**
 * Read a [UiAction] field. Absent or `JsonNull` → null. Primitive/array → wrapped as a
 * [CallbackAction] with the content as the event. Object with a known `type` discriminator
 * is built via the matching action builder; otherwise the type is inferred from which
 * fields are present.
 */
internal fun JsonObject.readAction(key: String): UiAction? {
    val element = this[key] ?: return null
    if (element is JsonNull) return null
    return when (element) {
        is JsonPrimitive ->
            if (element.isString) CallbackAction(event = element.content) else CallbackAction(event = element.content)

        is JsonArray -> CallbackAction(event = element.toStringLike())

        is JsonObject -> buildActionFromObject(element)
    }
}

private fun buildActionFromObject(obj: JsonObject): UiAction {
    val declaredType = obj.readNullableString("type")
    val type = when (declaredType) {
        "callback", "toggle", "open_url", "copy_to_clipboard" -> declaredType

        else -> when {
            "event" in obj -> "callback"
            "targetId" in obj -> "toggle"
            "url" in obj -> "open_url"
            else -> "callback"
        }
    }
    return when (type) {
        "toggle" -> ToggleAction(targetId = obj.readString("targetId"))

        "open_url" -> OpenUrlAction(url = obj.readString("url"))

        "copy_to_clipboard" -> CopyToClipboardAction(text = obj.readString("text"))

        else -> CallbackAction(
            event = obj.readString("event"),
            data = obj.readCallbackDataMap(),
            collectFrom = obj.readStringList("collectFrom").takeIf { it.isNotEmpty() },
        )
    }
}

/**
 * Read [CallbackAction.data] — a `Map<String, JsonPrimitive>?`. Preserves existing
 * primitives (so bools/numbers stay typed for `dataAsStrings`), flattens nested objects
 * and arrays to string primitives via [toStringLike].
 */
internal fun JsonObject.readCallbackDataMap(key: String = "data"): Map<String, JsonPrimitive>? {
    val dataObj = this[key] as? JsonObject ?: return null
    return dataObj.mapValues { (_, value) ->
        when (value) {
            is JsonPrimitive -> if (value is JsonNull) JsonPrimitive("") else value
            is JsonArray, is JsonObject -> JsonPrimitive(value.toStringLike())
        }
    }
}
