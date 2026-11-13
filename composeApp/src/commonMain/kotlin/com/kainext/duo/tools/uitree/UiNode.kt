package com.kainext.duo.tools.uitree

import kotlinx.serialization.Serializable

/**
 * One control from the on-screen accessibility tree, flattened into what a model needs to act.
 *
 * ## Why the field names are one or two characters
 *
 * Every `UiNode` is serialised into the model's prompt, and a prompt is billed per token. A
 * realistic phone screen (a chat list, a settings page) yields 40-80 actionable nodes, so the
 * field *names* are paid for dozens of times per turn, while each node's values are paid for
 * once. Renaming `contentDescription` (18 chars) to `desc` (4) and `className` (9) to `cls` (3)
 * removes roughly 30 characters — about 8 tokens — per node, i.e. a few hundred tokens per
 * screen. Those tokens buy nothing: the model never needs the long spelling to understand the
 * value, because the short names are self-evident and are explained once in the tool schema.
 *
 * The trade-off is deliberate and one-directional: this model is optimised for *reading* by a
 * model, not for hand-writing by a human, so readability of the Kotlin declaration is not the
 * constraint. Anything that only the app needs (parent links, window ids, accessibility flags
 * that never influence a decision) is simply not carried.
 *
 * ## Field semantics
 *
 * - [i] is the index used by [UiAction.Click] / [UiAction.Text] / [UiAction.Scroll]. It is
 *   assigned by the pruner in breadth-first order, so lower indices are nearer the screen root
 *   and are therefore the more "important" nodes when a slice was truncated.
 * - [cx] / [cy] are the centre of [bounds] in screen pixels, pre-computed because a model asked
 *   to tap a node otherwise has to average four numbers itself and often gets it wrong.
 * - [bounds] keeps the raw `[left,top][right,bottom]` form so an action can still reason about
 *   size (e.g. "this row is 140px tall, so swipe 70px") without a second round trip.
 * - [checked] is nullable rather than defaulting to `false`: `null` means "this control has no
 *   check state", which is different from "it is an unchecked checkbox". Collapsing the two
 *   makes the model report toggles that do not exist.
 */
@Serializable
data class UiNode(
    val i: Int,
    val text: String? = null,
    val desc: String? = null,
    val id: String? = null,
    val cls: String? = null,
    val bounds: String,
    val cx: Int,
    val cy: Int,
    val clickable: Boolean = false,
    val scrollable: Boolean = false,
    val editable: Boolean = false,
    val checked: Boolean? = null,
    val enabled: Boolean = true,
) {
    /**
     * Every string a user could reasonably call this control by, in priority order: visible
     * text first (that is what the user reads), then the accessibility description, then the
     * resource id (which the model only ever sees if it dumped the tree, but which is the most
     * stable handle across re-renders).
     */
    /**
     * The map form handed to the model as a tool result.
     *
     * Null and false values are omitted: a screen of 60 nodes would otherwise carry ~600 bytes of
     * `"clickable":false,"scrollable":false,"editable":false` that say nothing. Omitting them is
     * unambiguous because the tool schema documents the defaults once.
     */
    fun toToolMap(): Map<String, Any> = buildMap {
        put("i", i)
        text?.takeIf { it.isNotBlank() }?.let { put("text", it) }
        desc?.takeIf { it.isNotBlank() }?.let { put("desc", it) }
        id?.takeIf { it.isNotBlank() }?.let { put("id", it) }
        cls?.takeIf { it.isNotBlank() }?.let { put("cls", it) }
        put("bounds", bounds)
        put("cx", cx)
        put("cy", cy)
        if (clickable) put("clickable", true)
        if (scrollable) put("scrollable", true)
        if (editable) put("editable", true)
        checked?.let { put("checked", it) }
        if (!enabled) put("enabled", false)
    }

    fun labels(): List<String> {
        val out = ArrayList<String>(3)
        text?.trim()?.takeIf { it.isNotEmpty() }?.let { out += it }
        desc?.trim()?.takeIf { it.isNotEmpty() }?.let { out += it }
        id?.trim()?.takeIf { it.isNotEmpty() }?.let { out += it }
        return out
    }

    /** True when any label equals [needle] exactly, ignoring case. */
    fun matchesExactly(needle: String): Boolean {
        val trimmed = needle.trim()
        if (trimmed.isEmpty()) return false
        return labels().any { it.equals(trimmed, ignoreCase = true) }
    }

    /**
     * True when any label contains [needle], ignoring case. This is the *fallback* match used
     * when an exact hit does not exist: labels are frequently decorated ("Save (2)", "Search…")
     * and a contains match still lands on the right control, whereas an exact-only matcher would
     * make the agent fall back to blind coordinate taps.
     */
    fun containsLabel(needle: String): Boolean {
        val trimmed = needle.trim()
        if (trimmed.isEmpty()) return false
        return labels().any { it.contains(trimmed, ignoreCase = true) }
    }

    /** True when the control can receive typed text. */
    fun acceptsText(): Boolean = editable

    /**
     * Parses [bounds] back into numbers. Needed by the executor for the cases where the centre
     * point is not enough — a scroll has to start and end *inside* the container, and a hit-test
     * fallback wants the real edges. Returns null instead of throwing on a malformed string,
     * because bounds come from a platform API and from a hand-parsed `uiautomator` dump, and a
     * single odd node must not take down the whole action.
     */
    fun rect(): UiRect? = UiRect.parse(bounds)
}

/**
 * Screen rectangle in pixels. Deliberately a plain data class rather than `android.graphics.Rect`:
 * this package is shared code, and the whole point of the common model is that it carries no
 * Android types.
 */
data class UiRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2

    /** True when the rectangle encloses any area at all. */
    val hasArea: Boolean get() = width > 0 && height > 0

    companion object {
        private val PATTERN = Regex("\\[(-?\\d+),(-?\\d+)\\]\\[(-?\\d+),(-?\\d+)\\]")

        /** Accepts the `[left,top][right,bottom]` form that both Android and `uiautomator` emit. */
        fun parse(bounds: String): UiRect? {
            val match = PATTERN.find(bounds) ?: return null
            val values = match.destructured.toList().map { it.toIntOrNull() ?: return null }
            return UiRect(left = values[0], top = values[1], right = values[2], bottom = values[3])
        }
    }
}
