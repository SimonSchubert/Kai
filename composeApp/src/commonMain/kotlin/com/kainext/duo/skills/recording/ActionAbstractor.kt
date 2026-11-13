package com.kainext.duo.skills.recording

import kotlin.math.abs
import kotlinx.serialization.Serializable

/**
 * Dominant axis of a drag gesture, derived from the relative start/end points.
 *
 * Kept separate from [RecordedAction.Swipe]'s raw coordinates because "swipe up to scroll" is what
 * the model and a replayer actually reason about; the numbers are only there to reproduce it.
 */
@Serializable
enum class SwipeDirection {
    UP,
    DOWN,
    LEFT,
    RIGHT,

    /** Movement below the dead zone on both axes — a long-press wobble, not a real swipe. */
    NONE,
}

/**
 * The semantic reading of a recorded drag: what was under the finger at each end, which way it went,
 * and where it started/ended as screen ratios.
 *
 * Separate from [RecordedAction.Swipe] on purpose. The stored action has to stay replayable with
 * nothing but numbers, so it cannot carry [ScreenElement]s; this type is the *explanation* of a
 * swipe, computed at abstraction time or on demand by a replayer that wants to say "scroll the
 * message list up" instead of "swipe from 50%,80% to 50%,20%".
 */
@Serializable
data class SwipeSemantic(
    val fromElement: ScreenElement? = null,
    val toElement: ScreenElement? = null,
    val direction: SwipeDirection,
    val relativeStart: RelativePoint,
    val relativeEnd: RelativePoint,
)

/** A point expressed as a fraction of the screen, `(0,0)` top-left to `(1,1)` bottom-right. */
@Serializable
data class RelativePoint(
    val relativeX: Float,
    val relativeY: Float,
)

/**
 * Turns raw pixel events into resolution-independent [RecordedAction]s.
 *
 * This is the heart of the feature: the difference between a recording that works tomorrow and one
 * that only worked once. Three decisions carry that weight.
 *
 * **1. Smallest containing element wins.** A tap at (540, 1200) is contained by the window, the
 * scroll container, the list, the row, and finally the "签到" button — all of which report bounds
 * that include the point. The window is useless ("tap the screen"), the button is the intent. Area
 * is the cheapest proxy for depth: a child's bounds are always contained by its parent's, so the
 * innermost node is the one with the smallest area. This avoids needing parent links, which differ
 * per platform and per tree flavour (Compose vs. View vs. WebView).
 *
 * **2. An element that covers most of the screen is a miss, not a target.** If the smallest hit is
 * still >80% of the screen, the user tapped empty space inside a full-bleed background or a
 * `fillMaxSize` container. Abstracting that to "tap the element" would replay as a tap on a
 * meaningless node, so we prefer an honest relative coordinate instead — a ratio still lands on the
 * same visual spot on another resolution, which is exactly what the user meant.
 *
 * **3. Semantic handles are ranked text > description > resourceId, applied *per element*.** Text is
 * what the user reads, so it is the most likely to survive an app update and the most meaningful to
 * a model. A content description is the only handle icon-only buttons expose. Resource ids are the
 * most stable across locales and versions but the least portable (app-internal, and Compose screens
 * often expose none), so they rank last.
 *
 * The ranking is resolved *within the chosen element*, not across the candidate list — a deliberate
 * departure from "prefer whichever candidate has text". Candidates are ordered by area first because
 * area encodes depth, and depth is what identifies the thing the user actually touched. Ranking by
 * handle type instead would let a large ancestor outrank a small descendant: tapping the ">" chevron
 * inside a card titled "签到记录" would abstract to `TapText("签到记录")` — the card's label, a
 * completely different control. Depth first, then handle type, keeps the tap on the chevron.
 *
 * When the innermost node has no handle at all (a bare `ImageView` wrapper around a vector icon), the
 * smallest-area walk continues outward to the first ancestor that does, rather than dropping straight
 * to coordinates — an ancestor's label is still a better pointer than a pixel.
 *
 * All functions are pure and allocation-light so they can run on the accessibility callback thread.
 */
object ActionAbstractor {

    /**
     * Above this fraction of the screen area a matched element is treated as a background/container
     * rather than a target. 0.8 is deliberately generous: real buttons never cover four fifths of a
     * display, while dialogs and `fillMaxSize` boxes routinely do.
     */
    const val MAX_ELEMENT_SCREEN_AREA_RATIO = 0.8f

    /** [MAX_ELEMENT_SCREEN_AREA_RATIO] as an integer percentage, for overflow-safe comparison. */
    private const val MAX_ELEMENT_SCREEN_AREA_PERCENT = 80L

    /**
     * Below this fraction of screen width/height, movement is noise rather than a gesture. Guards
     * against reporting a direction for a tap whose down/up points drifted a few pixels.
     */
    const val SWIPE_DEAD_ZONE_RATIO = 0.03f

    /**
     * Abstract a tap into the most specific replayable action.
     *
     * @param tap the raw event; its `x`/`y` are screen pixels.
     * @param elements the flattened accessibility snapshot taken at the same moment.
     * @param screenWidth display width in pixels; defaults to the snapshot's own estimate so a
     *   caller that does not track the display still gets sane ratios. [SkillRecorder] always
     *   passes the real size.
     * @param screenHeight vertical counterpart of [screenWidth].
     * @return [RecordedAction.TapText], [RecordedAction.TapDescription], [RecordedAction.TapId], or
     *   [RecordedAction.TapPoint] when no element is a plausible target.
     */
    fun abstractTap(
        tap: RecordedEvent.Tap,
        elements: List<ScreenElement>,
        screenWidth: Int = elements.screenWidthEstimate(),
        screenHeight: Int = elements.screenHeightEstimate(),
    ): RecordedAction {
        val target = selectTarget(tap.x, tap.y, elements, screenWidth, screenHeight)
            ?: return RecordedAction.TapPoint(
                relativeX = ratio(tap.x, screenWidth),
                relativeY = ratio(tap.y, screenHeight),
            )

        // Priority order is per-element, not per-candidate-list: the chosen node's own text beats
        // another node's description, because the chosen node is the one under the finger.
        target.text?.takeIf { it.isNotBlank() }?.let { text ->
            return RecordedAction.TapText(text.trim(), tap.x, tap.y)
        }
        target.description?.takeIf { it.isNotBlank() }?.let { description ->
            return RecordedAction.TapDescription(description.trim(), tap.x, tap.y)
        }
        target.resourceId?.takeIf { it.isNotBlank() }?.let { id ->
            return RecordedAction.TapId(id.trim(), tap.x, tap.y)
        }
        // Unreachable in practice — selectTarget only returns nodes with a handle — but keeping the
        // coordinate fallback makes the function total if that invariant ever changes.
        return RecordedAction.TapPoint(
            relativeX = ratio(tap.x, screenWidth),
            relativeY = ratio(tap.y, screenHeight),
        )
    }

    /**
     * Abstract a drag into a replayable [RecordedAction.Swipe].
     *
     * Only the geometry survives into storage: the start/end *elements* are not part of the action
     * format, because scrolling is defined by the motion, not by what happened to be under the
     * finger at the ends. Use [swipeSemantic] when the elements are wanted for explanation.
     *
     * The default screen size is derived from the swipe's own extreme coordinates. That is a lower
     * bound rather than the true display size, but it keeps the produced ratios inside `0..1`
     * instead of collapsing to zero — and [SkillRecorder] always passes the real display size, so
     * the approximation only affects direct callers.
     */
    fun abstractSwipe(
        swipe: RecordedEvent.Swipe,
        screenWidth: Int = maxOf(swipe.x1, swipe.x2),
        screenHeight: Int = maxOf(swipe.y1, swipe.y2),
    ): RecordedAction.Swipe = RecordedAction.Swipe(
        fromRelativeX = ratio(swipe.x1, screenWidth),
        fromRelativeY = ratio(swipe.y1, screenHeight),
        toRelativeX = ratio(swipe.x2, screenWidth),
        toRelativeY = ratio(swipe.y2, screenHeight),
        durationMs = swipe.durationMs.coerceAtLeast(0),
    )

    /**
     * Explain a drag: direction plus the elements under each end.
     *
     * @param screenWidth / `screenHeight` default to the snapshot's own extent estimate.
     */
    fun swipeSemantic(
        swipe: RecordedEvent.Swipe,
        elements: List<ScreenElement>,
        screenWidth: Int = elements.screenWidthEstimate(),
        screenHeight: Int = elements.screenHeightEstimate(),
    ): SwipeSemantic {
        val start = RelativePoint(ratio(swipe.x1, screenWidth), ratio(swipe.y1, screenHeight))
        val end = RelativePoint(ratio(swipe.x2, screenWidth), ratio(swipe.y2, screenHeight))
        return SwipeSemantic(
            fromElement = selectTarget(swipe.x1, swipe.y1, elements, screenWidth, screenHeight),
            toElement = selectTarget(swipe.x2, swipe.y2, elements, screenWidth, screenHeight),
            direction = swipeDirectionOf(start.relativeX, start.relativeY, end.relativeX, end.relativeY),
            relativeStart = start,
            relativeEnd = end,
        )
    }

    /**
     * Direction of travel from one screen ratio to another, by dominant axis.
     *
     * Note the y-axis inversion: screen y grows downward, so "finger moves up" means the end point
     * has a *smaller* y. A swipe up scrolls content up (revealing what is below), which is what a
     * reader means by "swipe up" — matching the gesture rather than the content motion avoids an
     * off-by-a-sign in every replayer.
     */
    fun swipeDirectionOf(fromX: Float, fromY: Float, toX: Float, toY: Float): SwipeDirection {
        val dx = toX - fromX
        val dy = toY - fromY
        val horizontalDominant = abs(dx) > abs(dy)
        val magnitude = if (horizontalDominant) abs(dx) else abs(dy)
        if (magnitude < SWIPE_DEAD_ZONE_RATIO) return SwipeDirection.NONE
        return if (horizontalDominant) {
            if (dx > 0f) SwipeDirection.RIGHT else SwipeDirection.LEFT
        } else {
            if (dy > 0f) SwipeDirection.DOWN else SwipeDirection.UP
        }
    }

    /**
     * The element a tap at ([x], [y]) most plausibly meant, or null when there is none.
     *
     * Returns the smallest element that both contains the point and exposes a semantic handle, after
     * discarding anything larger than [MAX_ELEMENT_SCREEN_AREA_RATIO] of the screen. Ties are broken
     * by top-most then left-most edge, so two identically sized siblings resolve deterministically
     * to the one nearer the top-left of the screen — the container-first order the platform usually
     * reports, which keeps the result stable across runs.
     */
    fun selectTarget(
        x: Int,
        y: Int,
        elements: List<ScreenElement>,
        screenWidth: Int = elements.screenWidthEstimate(),
        screenHeight: Int = elements.screenHeightEstimate(),
    ): ScreenElement? {
        val screenArea = screenWidth.toLong() * screenHeight.toLong()
        return elements
            .asSequence()
            .filter { it.contains(x, y) }
            .filter { it.hasSemanticHandle() }
            // With no screen size the ratio test is meaningless; accepting the candidate is better
            // than discarding every element and losing all semantics. Compared as percentages in
            // Long so a 4K display cannot overflow the multiplication.
            .filter { screenArea <= 0L || it.area.toLong() * 100L <= screenArea * MAX_ELEMENT_SCREEN_AREA_PERCENT }
            .minWithOrNull(compareBy<ScreenElement>({ it.area }, { it.minY }, { it.minX }))
    }

    /**
     * Pixel offset as a fraction of the axis length, clamped to `0..1`.
     *
     * Clamping matters: accessibility bounds and raw touch points can both stray outside the display
     * (overscroll, edge gestures, a stale tree), and an unclamped ratio would replay off-screen.
     * A zero/negative axis length (unknown display) yields `0f`, the only safe answer.
     */
    fun ratio(offset: Int, axisLength: Int): Float {
        if (axisLength <= 0) return 0f
        return (offset.toFloat() / axisLength.toFloat()).coerceIn(0f, 1f)
    }
}
