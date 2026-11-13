package com.kainext.duo.tools.uitree

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.kainext.duo.KaiAccessibilityService
import com.kainext.duo.tools.PrivilegedShell
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val TAG = "UiActionExecutor"

/**
 * Executes a [UiAction] against the live screen, with the accessibility service first and a
 * shell (`uiautomator` + `input`) fallback second.
 *
 * ## Why the tree is re-read before every action
 *
 * The model's index came from a slice that was printed at least one model round-trip ago — often
 * several seconds, and in the meantime a notification banner appeared, a list finished loading,
 * or the previous action already navigated. A stored coordinate is therefore not "slightly stale",
 * it is a guess: replaying `[540,1200]` from an old dump is exactly the blind tap that this whole
 * package exists to avoid, and when it misses it does not fail — it hits whatever moved into that
 * spot, which is worse than an error because the agent believes it succeeded.
 *
 * So every action resolves its target against a *fresh* snapshot. Indices are treated as a hint,
 * not an address: the executor remembers the labels of the node the index pointed at last time
 * and re-finds that node in the new tree, falling back to the raw index only when the old slice
 * had no label to match on. That keeps indices usable across a re-render without trusting them.
 *
 * ## Why actions wait afterwards
 *
 * A tap returns as soon as the gesture is dispatched, long before the app has reacted. Without a
 * wait, the `sliceAfter` attached to the result is the *old* screen, and the next step reasons
 * about a UI that no longer exists — the classic "clicked Login, saw the login form, clicked
 * Login again" loop. [awaitUiStable] polls until two consecutive snapshots agree, which is the
 * cheapest signal that the transition finished, and reports honestly when it never settled
 * instead of pretending the screen is final.
 */
class UiActionExecutor(
    private val pruner: UiTreePruner = UiTreePruner(),
    private val shell: PrivilegedShell = PrivilegedShell(),
    private val fallback: UiTreeShellFallback = UiTreeShellFallback(shell),
    /** How long [awaitUiStable] waits before giving up on a transition. */
    private val stableTimeoutMs: Long = DEFAULT_STABLE_TIMEOUT_MS,
    /** Poll interval for [awaitUiStable]. */
    private val stablePollMs: Long = DEFAULT_STABLE_POLL_MS,
) {

    /**
     * The last slice this executor produced or observed. Used only to translate an index into a
     * label before re-resolving it against the fresh tree — never as a source of coordinates.
     */
    private var lastSlice: UiSlice? = null

    /** Runs [action] and reports what happened, including the screen it produced. */
    suspend fun execute(action: UiAction): UiActionResult = when (action) {
        is UiAction.Click -> click(action.index, action.text)
        is UiAction.Tap -> tap(action.x, action.y)
        is UiAction.LongPress -> longPress(action)
        is UiAction.Swipe -> swipe(action.x1, action.y1, action.x2, action.y2, action.durationMs)
        is UiAction.Text -> type(action.value, action.index)
        is UiAction.Key -> pressKey(action.name)
        is UiAction.Scroll -> scroll(action.index, action.forward)
    }

    /** Current screen, preferring accessibility and falling back to the shell dump. */
    suspend fun snapshot(): UiSlice {
        val live = pruner.snapshot()
        if (live != null && !live.slice.isEmpty) {
            lastSlice = live.slice
            return live.slice
        }
        // An accessibility snapshot with zero nodes is not the same as "no accessibility": it can
        // be a game surface, a blank WebView or a just-opened window. Only the shell dump can tell
        // those apart, so ask it and keep whichever answer actually has content.
        val dumped = fallback.snapshot()
        val chosen = if (dumped.nodes.size > (live?.slice?.nodes?.size ?: 0)) dumped else live?.slice ?: dumped
        lastSlice = chosen
        return chosen
    }

    /**
     * Polls the screen until two consecutive snapshots have the same [UiSlice.signature], or until
     * [timeoutMs] elapses.
     *
     * Two identical reads mean the UI stopped changing: animations finished, the list settled, the
     * new screen was laid out. One read is not enough — a single sample taken 150 ms after a tap
     * routinely catches the transition half-drawn (spinner still there, new labels not yet
     * applied), which is why the comparison is between two *successive* reads and not between one
     * read and a stored baseline.
     *
     * Timing out is not a failure: slow screens (a network-backed list) legitimately take longer
     * than the budget, and the caller gets [UiStability.settled] = false plus the latest slice so
     * it can decide to wait again instead of retrying the action.
     */
    suspend fun awaitUiStable(
        timeoutMs: Long = stableTimeoutMs,
        pollMs: Long = stablePollMs,
    ): UiStability = withContext(Dispatchers.Default) {
        val started = now()
        var previous: Long? = null
        var polls = 0
        var latest: UiSlice? = null
        var settled = false
        while (true) {
            val current = pruner.slice() ?: fallback.snapshot()
            latest = current
            polls++
            val signature = current.signature()
            if (previous != null && previous == signature) {
                settled = true
                break
            }
            previous = signature
            if (now() - started >= timeoutMs) break
            delay(effectivePollMs(pollMs))
        }
        val result = latest ?: UiSlice.empty()
        lastSlice = result
        UiStability(settled = settled, waitedMs = now() - started, polls = polls, slice = result)
    }

    // ---- click ----

    /**
     * Activates a control by index or by label.
     *
     * Index resolution is label-first (see the class KDoc): the remembered label is what survives
     * a re-render, the integer does not. When neither index nor label is supplied the call fails
     * with an explicit message rather than tapping the centre of the screen.
     */
    private suspend fun click(index: Int?, text: String?): UiActionResult {
        val needle = text?.trim()?.takeIf { it.isNotEmpty() }
        if (index == null && needle == null) {
            return failure("click needs either an index or a text label", UiActionResult.BACKEND_ACCESSIBILITY)
        }
        val service = KaiAccessibilityService.current()
        if (service == null) return fallback.click(index, needle, lastSlice)

        val snap = pruner.snapshot() ?: return fallback.click(index, needle, lastSlice)
        val target = resolveTarget(snap.slice, index, needle)
            ?: return failure(missingTargetMessage(index, needle, snap.slice), UiActionResult.BACKEND_ACCESSIBILITY)

        if (!target.enabled) {
            return failure(
                "#${target.i} ${describe(target)} is disabled; wait for it to become enabled",
                UiActionResult.BACKEND_ACCESSIBILITY,
            )
        }
        val live = snap.liveNode(target.i)
        if (live != null && service.clickNode(live)) {
            lastSlice = snap.slice
            return settle(
                backend = UiActionResult.BACKEND_ACCESSIBILITY,
                message = "clicked #${target.i} ${describe(target)}",
            )
        }

        // Present but not marked clickable (custom-drawn row, canvas, text inside a clickable
        // parent): tap the centre of its *current* bounds, which is what a user would do.
        val rect = target.rect()
        if (rect == null || !rect.hasArea) {
            return failure("#${target.i} has no usable bounds", UiActionResult.BACKEND_ACCESSIBILITY)
        }
        val dispatched = service.gestureTap(rect.centerX.toFloat(), rect.centerY.toFloat())
        if (!dispatched) {
            return fallback.click(index, needle, lastSlice)
        }
        lastSlice = snap.slice
        return settle(
            backend = UiActionResult.BACKEND_ACCESSIBILITY_GESTURE,
            message = "tapped #${target.i} at [${rect.centerX},${rect.centerY}] ${describe(target)}",
        )
    }

    /**
     * Finds the node an action refers to. An explicit label wins over the index, and the index is
     * only trusted as far as the labels of the node it used to point at — see the class KDoc.
     */
    private fun resolveTarget(slice: UiSlice, index: Int?, needle: String?): UiNode? {
        if (needle != null) return slice.matchByText(needle)
        if (index == null) return null
        val remembered = lastSlice?.nodeAt(index)
        val rememberedLabel = remembered?.let { node -> node.labels().firstOrNull() }
        if (rememberedLabel != null) {
            slice.matchByText(rememberedLabel)?.let { return it }
        }
        return slice.nodeAt(index)
    }

    /** Failure text that tells the model what *is* on screen instead of just that it failed. */
    private fun missingTargetMessage(index: Int?, needle: String?, slice: UiSlice): String {
        val wanted = needle?.let { "'$it'" } ?: "#$index"
        val candidates = slice.nodes
            .mapNotNull { it.text ?: it.desc }
            .distinct()
            .take(MAX_SUGGESTIONS)
        return if (candidates.isEmpty()) {
            "no node matches $wanted; the screen has ${slice.nodes.size} nodes with no labels " +
                "(try UiAction.Tap with coordinates from a fresh dump)"
        } else {
            "no node matches $wanted; the screen has ${slice.nodes.size} nodes, e.g. " +
                candidates.joinToString(", ") { "'$it'" }
        }
    }

    private fun describe(node: UiNode): String {
        val label = node.labels().firstOrNull() ?: node.cls ?: "unlabelled"
        return "(\"$label\")"
    }

    // ---- tap / swipe ----

    /**
     * Taps raw coordinates. There is nothing to re-resolve here — the caller supplied a position,
     * not a control — so the only correctness question is which backend can inject input.
     */
    private suspend fun tap(x: Int, y: Int): UiActionResult {
        val service = KaiAccessibilityService.current()
        if (service != null && service.gestureTap(x.toFloat(), y.toFloat())) {
            return settle(UiActionResult.BACKEND_ACCESSIBILITY_GESTURE, "tapped [$x,$y]")
        }
        return fallback.tap(x, y)
    }

    private suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int): UiActionResult {
        val duration = durationMs.coerceIn(MIN_GESTURE_MS, MAX_GESTURE_MS)
        val service = KaiAccessibilityService.current()
        if (service != null && service.gestureSwipe(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), duration.toLong())) {
            return settle(UiActionResult.BACKEND_ACCESSIBILITY_GESTURE, "swiped [$x1,$y1] -> [$x2,$y2] over ${duration}ms")
        }
        return fallback.swipe(x1, y1, x2, y2, duration)
    }

    // ---- long press ----

    /**
     * Presses and holds, which is the only way to reach the second layer of most apps: context
     * menus on list rows, message actions in a chat, drag handles, multi-select entry.
     *
     * The press point is resolved the same way a click resolves its target — a label or an index
     * against the *fresh* tree — because holding a stale coordinate is the most damaging miss in
     * the whole action set: a long press on the wrong row opens that row's destructive menu, and
     * the agent has no way to tell it was the wrong row except by reading the new screen.
     *
     * Raw coordinates are accepted for canvases and custom-drawn views, but only when the caller
     * also had no node to address; coordinates win over a node when both are given, since that is
     * an explicit decision by the caller to point at something the tree does not describe.
     */
    private suspend fun longPress(action: UiAction.LongPress): UiActionResult {
        val duration = action.durationMs.coerceIn(MIN_LONG_PRESS_MS, MAX_LONG_PRESS_MS)
        val pointX = action.x
        val pointY = action.y
        if (pointX != null && pointY != null) {
            val service = KaiAccessibilityService.current()
            if (service != null && service.gestureTap(pointX.toFloat(), pointY.toFloat(), duration.toLong())) {
                return settle(
                    UiActionResult.BACKEND_ACCESSIBILITY_GESTURE,
                    "long-pressed [$pointX,$pointY] for ${duration}ms",
                )
            }
            return fallback.longPress(pointX, pointY, duration)
        }

        val needle = action.text?.trim()?.takeIf { it.isNotEmpty() }
        if (action.index == null && needle == null) {
            return failure(
                "long_press needs an index, a text label, or x/y coordinates",
                UiActionResult.BACKEND_ACCESSIBILITY,
            )
        }
        val service = KaiAccessibilityService.current()
        if (service == null) return fallback.longPressAtNode(action.index, needle, lastSlice, duration)

        val snap = pruner.snapshot() ?: return fallback.longPressAtNode(action.index, needle, lastSlice, duration)
        val target = resolveTarget(snap.slice, action.index, needle)
            ?: return failure(
                missingTargetMessage(action.index, needle, snap.slice),
                UiActionResult.BACKEND_ACCESSIBILITY,
            )
        if (!target.enabled) {
            return failure(
                "#${target.i} ${describe(target)} is disabled; wait for it to become enabled",
                UiActionResult.BACKEND_ACCESSIBILITY,
            )
        }
        val rect = target.rect()
        if (rect == null || !rect.hasArea) {
            return failure("#${target.i} has no usable bounds", UiActionResult.BACKEND_ACCESSIBILITY)
        }
        if (!service.gestureTap(rect.centerX.toFloat(), rect.centerY.toFloat(), duration.toLong())) {
            return failure(
                "the accessibility service could not dispatch a press on #${target.i} ${describe(target)}",
                UiActionResult.BACKEND_ACCESSIBILITY_GESTURE,
            )
        }
        lastSlice = snap.slice
        return settle(
            UiActionResult.BACKEND_ACCESSIBILITY_GESTURE,
            "long-pressed #${target.i} ${describe(target)} at [${rect.centerX},${rect.centerY}] for ${duration}ms",
        )
    }

    // ---- text ----

    /**
     * Types [value] into the field at [index], or into the focused field when [index] is null.
     *
     * `ACTION_SET_TEXT` is used instead of key events because it is atomic: it cannot be
     * interleaved with the IME, it preserves spaces and newlines verbatim (shell `input text`
     * needs `%s` and mangles the rest), and it does not depend on the keyboard being on screen.
     */
    private suspend fun type(value: String, index: Int?): UiActionResult {
        if (value.isEmpty()) return failure("text action needs a value (use it to clear a field too)", UiActionResult.BACKEND_ACCESSIBILITY)
        val service = KaiAccessibilityService.current()
            ?: return fallback.type(value, index, lastSlice)

        if (index == null) {
            // Null node means "whatever holds input focus", which is what the service resolves.
            if (service.setText(null, value)) {
                return settle(UiActionResult.BACKEND_ACCESSIBILITY, "typed ${value.length} chars into the focused field")
            }
            return failure(
                "no field has input focus; click the field first or pass its index",
                UiActionResult.BACKEND_ACCESSIBILITY,
            )
        }

        val snap = pruner.snapshot() ?: return fallback.type(value, index, lastSlice)
        val target = resolveTarget(snap.slice, index, null)
            ?: return failure(missingTargetMessage(index, null, snap.slice), UiActionResult.BACKEND_ACCESSIBILITY)
        if (!target.editable && !target.clickable) {
            return failure("#${target.i} ${describe(target)} is neither editable nor clickable", UiActionResult.BACKEND_ACCESSIBILITY)
        }
        val live = snap.liveNode(target.i)
        if (!service.setText(live, value)) {
            return failure("could not set text on #${target.i} ${describe(target)}", UiActionResult.BACKEND_ACCESSIBILITY)
        }
        lastSlice = snap.slice
        return settle(UiActionResult.BACKEND_ACCESSIBILITY, "typed ${value.length} chars into #${target.i} ${describe(target)}")
    }

    // ---- key ----

    /**
     * Presses a navigation key. BACK/HOME/RECENTS go through the service's global actions because
     * they are the same pipeline TalkBack uses and cannot be swallowed by the focused app; every
     * other key (ENTER, TAB, KEYCODE_*) has to go through `input keyevent`.
     */
    private suspend fun pressKey(name: String): UiActionResult {
        val normalized = name.trim().uppercase()
        if (normalized.isEmpty()) return failure("key action needs a key name", UiActionResult.BACKEND_ACCESSIBILITY)
        val global = when (normalized) {
            "BACK", "KEYCODE_BACK" -> KaiAccessibilityService.GlobalAction.BACK
            "HOME", "KEYCODE_HOME" -> KaiAccessibilityService.GlobalAction.HOME
            "RECENTS", "APP_SWITCH", "KEYCODE_APP_SWITCH" -> KaiAccessibilityService.GlobalAction.RECENTS
            "NOTIFICATIONS", "KEYCODE_NOTIFICATION" -> KaiAccessibilityService.GlobalAction.NOTIFICATIONS
            else -> null
        }
        if (global != null) {
            val service = KaiAccessibilityService.current()
            if (service != null && service.performGlobal(global)) {
                return settle(UiActionResult.BACKEND_ACCESSIBILITY, "pressed $normalized")
            }
        }
        return fallback.key(normalized)
    }

    // ---- scroll ----

    /**
     * Scrolls the container at [index].
     *
     * `ACTION_SCROLL_FORWARD` is tried first because it moves exactly one page and does not depend
     * on the container's pixel geometry; the gesture path is only needed for containers that
     * expose `isScrollable` but ignore the action (custom lists, WebViews), and it derives its
     * endpoints from the container's own bounds so the swipe stays inside the scrollable area
     * instead of landing on a fixed point that happens to be a toolbar on another screen.
     */
    private suspend fun scroll(index: Int, forward: Boolean): UiActionResult {
        val service = KaiAccessibilityService.current() ?: return fallback.scroll(index, forward, lastSlice)
        val snap = pruner.snapshot() ?: return fallback.scroll(index, forward, lastSlice)
        val target = resolveTarget(snap.slice, index, null)
            ?: return failure(missingTargetMessage(index, null, snap.slice), UiActionResult.BACKEND_ACCESSIBILITY)

        val live = snap.liveNode(target.i)
        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        val performed = try {
            live?.performAction(action) == true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.w(TAG, "performAction(SCROLL) failed", e)
            false
        }
        if (performed) {
            lastSlice = snap.slice
            return settle(
                UiActionResult.BACKEND_ACCESSIBILITY,
                "scrolled #${target.i} ${describe(target)} ${if (forward) "forward" else "backward"}",
            )
        }

        val rect = target.rect()
        if (rect == null || !rect.hasArea) {
            return failure("#${target.i} is not scrollable and has no bounds", UiActionResult.BACKEND_ACCESSIBILITY)
        }
        // Travel across 60% of the container, not its full height: starting at the very edge is
        // where the status bar and the navigation gesture area live, and a swipe that begins
        // there is intercepted by the system instead of the list.
        val span = (rect.height * SCROLL_TRAVEL_RATIO).toInt().coerceAtLeast(MIN_SCROLL_SPAN)
        val startY = if (forward) rect.top + (rect.height - span) / 2 else rect.bottom - (rect.height - span) / 2
        val endY = if (forward) startY + span else startY - span
        if (service.gestureSwipe(rect.centerX.toFloat(), startY.toFloat(), rect.centerX.toFloat(), endY.toFloat(), DEFAULT_SCROLL_MS)) {
            lastSlice = snap.slice
            return settle(
                UiActionResult.BACKEND_ACCESSIBILITY_GESTURE,
                "swiped #${target.i} ${describe(target)} ${if (forward) "forward" else "backward"}",
            )
        }
        return failure("#${target.i} ${describe(target)} did not respond to scrolling", UiActionResult.BACKEND_ACCESSIBILITY)
    }

    /**
     * Waits for the UI to settle and attaches the resulting slice. Every successful action goes
     * through here so callers never have to remember to wait — the wait is part of the action.
     */
    private suspend fun settle(backend: String, message: String): UiActionResult {
        val stability = awaitUiStable()
        val suffix = if (stability.settled) {
            ""
        } else {
            " (UI may still be changing after ${stability.waitedMs}ms; ${stability.polls} polls)"
        }
        return UiActionResult(
            success = true,
            backend = backend,
            message = message + suffix,
            sliceAfter = stability.slice,
        )
    }

    /**
     * Poll interval, widened when the tree comes from the shell fallback.
     *
     * Each shell poll spawns `uiautomator dump`, which takes hundreds of milliseconds and starts a
     * process — polling that every 150 ms would spend the whole budget queueing dumps and would
     * measure our own load rather than the app's. The accessibility path is an in-process read and
     * can afford the fast interval.
     */
    private fun effectivePollMs(requested: Long): Long {
        val floor = if (KaiAccessibilityService.current() == null) SHELL_POLL_MS else MIN_POLL_MS
        return requested.coerceAtLeast(floor)
    }

    private fun now(): Long = System.currentTimeMillis()

    companion object {
        /** Long enough for a local transition, short enough not to stall a multi-step task. */
        const val DEFAULT_STABLE_TIMEOUT_MS: Long = 3_000

        /** 150 ms is roughly one frame burst — fast enough to catch a settled screen quickly. */
        const val DEFAULT_STABLE_POLL_MS: Long = 150

        private const val MIN_POLL_MS = 50L

        /** `uiautomator dump` is a process spawn; polling it faster only adds queueing noise. */
        private const val SHELL_POLL_MS = 600L
        private const val MIN_GESTURE_MS = 50
        private const val MAX_GESTURE_MS = 5_000

        /**
         * Below ~500 ms Android reports a plain click, so a "long press" shorter than that is a
         * click with extra steps — the floor makes the difference real instead of nominal.
         */
        private const val MIN_LONG_PRESS_MS = 500

        /** Beyond a few seconds the finger reads as stuck; views start cancelling the gesture. */
        private const val MAX_LONG_PRESS_MS = 10_000
        private const val DEFAULT_SCROLL_MS = 300L
        private const val SCROLL_TRAVEL_RATIO = 0.6
        private const val MIN_SCROLL_SPAN = 80
        private const val MAX_SUGGESTIONS = 8
    }
}

/**
 * Result of [UiActionExecutor.awaitUiStable].
 *
 * [settled] distinguishes "the screen stopped changing" from "we ran out of time". Callers must
 * branch on it: treating a timeout as settled is how an agent concludes a page is empty while it
 * is still loading.
 */
data class UiStability(
    val settled: Boolean,
    val waitedMs: Long,
    val polls: Int,
    val slice: UiSlice,
)
