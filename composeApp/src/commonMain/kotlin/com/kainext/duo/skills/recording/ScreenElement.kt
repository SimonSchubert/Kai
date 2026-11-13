package com.kainext.duo.skills.recording

import kotlinx.serialization.Serializable

/**
 * One interactable node of the UI tree at the moment an event happened — the platform's flattened
 * view of an `AccessibilityNodeInfo` (or of a Compose semantics tree).
 *
 * **Why the platform has to provide this:** `commonMain` cannot walk an accessibility tree, so the
 * Android side flattens it into this list once per recorded event (pre-order, one entry per node
 * that carries any of text / description / id / class, bounds clamped to the screen). Flattening
 * rather than nesting keeps [ActionAbstractor] simple: the containment relation between the boxes
 * already encodes the tree, and "smallest box containing the point" is a cheaper and more portable
 * nesting test than re-deriving parent links on every platform.
 *
 * All four identity fields are nullable because apps expose different things: Compose screens often
 * have no `resourceId`, WebViews often only have `description`, and custom-drawn canvases have none
 * of them — only bounds.
 *
 * Bounds are screen pixels, `[x1, y1]` top-left and `[x2, y2]` bottom-right, as reported by
 * `AccessibilityNodeInfo.getBoundsInScreen`. Nothing here trusts that `x1 <= x2`: some providers
 * report inverted or degenerate bounds for animated and clipped nodes, so every geometric helper
 * normalises first.
 */
@Serializable
data class ScreenElement(
    val text: String? = null,
    val description: String? = null,
    val resourceId: String? = null,
    val className: String? = null,
    val x1: Int,
    val y1: Int,
    val x2: Int,
    val y2: Int,
) {
    /** Horizontal centre of the normalised box. */
    val cx: Int get() = (minX + maxX) / 2

    /** Vertical centre of the normalised box. */
    val cy: Int get() = (minY + maxY) / 2

    /**
     * Box area in square pixels, computed from the normalised edges so inverted or degenerate bounds
     * can never yield a negative area — which would otherwise win the "smallest element" search in
     * [ActionAbstractor] and abstract a tap onto a nonsense node.
     */
    val area: Int get() = (maxX - minX) * (maxY - minY)

    /** Left edge, normalised so an inverted node still has a sensible box. */
    val minX: Int get() = minOf(x1, x2)

    /** Top edge, normalised so an inverted node still has a sensible box. */
    val minY: Int get() = minOf(y1, y2)

    /** Right edge, normalised so an inverted node still has a sensible box. */
    val maxX: Int get() = maxOf(x1, x2)

    /** Bottom edge, normalised so an inverted node still has a sensible box. */
    val maxY: Int get() = maxOf(y1, y2)

    /** True when the point lies inside the normalised bounds. Both axes are inclusive. */
    fun contains(x: Int, y: Int): Boolean = x in minX..maxX && y in minY..maxY

    /** True when this node exposes anything that could serve as a semantic handle for replay. */
    fun hasSemanticHandle(): Boolean =
        !text.isNullOrBlank() || !description.isNullOrBlank() || !resourceId.isNullOrBlank()
}

/**
 * Best-effort screen width derived from the snapshot itself.
 *
 * The accessibility root spans the whole screen, so the widest right edge approximates the display.
 * Only used as a fallback for [ActionAbstractor]'s screen-size parameters: [SkillRecorder] always
 * passes the real size it was started with, so recordings made through the recorder never depend on
 * this estimate.
 */
fun List<ScreenElement>.screenWidthEstimate(): Int = maxOfOrNull { it.maxX } ?: 0

/** Vertical counterpart of [screenWidthEstimate]. */
fun List<ScreenElement>.screenHeightEstimate(): Int = maxOfOrNull { it.maxY } ?: 0

/** Every element whose bounds contain the point, in the order the platform supplied them. */
fun List<ScreenElement>.elementsContainingPoint(x: Int, y: Int): List<ScreenElement> = filter { it.contains(x, y) }
