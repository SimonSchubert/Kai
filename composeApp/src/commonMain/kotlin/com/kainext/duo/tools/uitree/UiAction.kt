package com.kainext.duo.tools.uitree

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One thing to do to the screen, addressed by node index, by label, or by raw coordinates.
 *
 * The point of modelling actions as a sealed hierarchy instead of a `Map<String, Any>` is that
 * the model emits exactly one of a known set: it cannot invent `"action": "click_but_not_really"`
 * or forget the coordinates for a swipe, and the serialised form is discriminated by a short
 * `type` tag, so a full action costs well under a line of JSON.
 *
 * Every variant carries enough to be executed *without* a previously-dumped slice wherever that
 * is possible, because the slice may be stale by the time the action arrives — see
 * `UiActionExecutor`, which always re-reads the tree before acting on an index or a label.
 */
@Serializable
sealed interface UiAction {

    /**
     * Activates a control. Prefer [text] over [index] when the model can read a label: indices
     * are only valid against the slice they were printed from, whereas a label survives the tree
     * changing underneath (a notification arriving, a keyboard opening).
     */
    @Serializable
    @SerialName("click")
    data class Click(val index: Int? = null, val text: String? = null) : UiAction

    /** Raw tap at screen coordinates, for canvases and custom-drawn views with no node. */
    @Serializable
    @SerialName("tap")
    data class Tap(val x: Int, val y: Int) : UiAction

    /**
     * Presses and holds, which is how a long list of real apps expose their second layer
     * (context menus, multi-select, drag handles, message actions). Addressed the same three
     * ways as a click: by [text] label, by [index], or by raw coordinates when both are known.
     *
     * [durationMs] is clamped by the executor: below ~500 ms Android reports a plain click, and
     * an unreasonably long hold looks like a stuck finger to some views.
     */
    @Serializable
    @SerialName("long_press")
    data class LongPress(
        val index: Int? = null,
        val text: String? = null,
        val x: Int? = null,
        val y: Int? = null,
        val durationMs: Int = 800,
    ) : UiAction

    /** Swipe/scroll gesture from (x1,y1) to (x2,y2) over [durationMs]. */
    @Serializable
    @SerialName("swipe")
    data class Swipe(
        val x1: Int,
        val y1: Int,
        val x2: Int,
        val y2: Int,
        val durationMs: Int = 300,
    ) : UiAction

    /**
     * Types [value] into a text field. When [index] is null the currently focused input is used,
     * which is the common case after a click has already focused the field.
     */
    @Serializable
    @SerialName("text")
    data class Text(val value: String, val index: Int? = null) : UiAction

    /**
     * Presses a hardware/navigation key by name (`BACK`, `HOME`, `ENTER`, `KEYCODE_BACK`, …).
     * Kept as a name rather than an int so the model does not have to memorise key codes.
     */
    @Serializable
    @SerialName("key")
    data class Key(val name: String) : UiAction

    /**
     * Scrolls a scrollable container. [forward] means "towards the end of the content", i.e.
     * swipe up for a vertical list — the same direction a user's finger travels when they read on.
     */
    @Serializable
    @SerialName("scroll")
    data class Scroll(val index: Int, val forward: Boolean = true) : UiAction
}

/**
 * Outcome of executing one [UiAction].
 *
 * [sliceAfter] carries the freshly pruned screen so the next step does not need a separate dump:
 * a tap almost always changes the screen, and asking the model to make a second call just to see
 * the result doubles the round trips (and the latency) of every interaction.
 *
 * [backend] names the path that actually ran it — `accessibility`, `accessibility_gesture`, or
 * the shell backends (`root`/`shizuku`/`app`) — because the user-facing status and the agent's
 * own fallback decisions depend on knowing whether the accessibility service was really used.
 */
@Serializable
data class UiActionResult(
    val success: Boolean,
    val backend: String,
    val message: String,
    val sliceAfter: UiSlice? = null,
) {
    companion object {
        /** Accessibility service performed the action itself (ACTION_CLICK / ACTION_SET_TEXT). */
        const val BACKEND_ACCESSIBILITY = "accessibility"

        /** Accessibility service was up but the node was not clickable, so a gesture was dispatched. */
        const val BACKEND_ACCESSIBILITY_GESTURE = "accessibility_gesture"

        /** No accessibility service; the action went through `uiautomator` + `input`. */
        const val BACKEND_SHELL = "shell"
    }
}

/**
 * Failure detail that still has to be actionable for the model.
 *
 * A bare "failed" makes the agent retry the same thing. Every message produced by the executor
 * therefore says what was attempted, why it did not work, and what to do instead — e.g.
 * "no node matches 'Save'; screen has 34 nodes, try one of: Save draft, Cancel".
 */
internal fun failure(message: String, backend: String): UiActionResult =
    UiActionResult(success = false, backend = backend, message = message, sliceAfter = null)
