package com.kainext.duo.skills.recording

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One replayable step of a recorded procedure, in the *semantic* form the AI reasons about.
 *
 * **Why semantics instead of coordinates:** a pixel tap recorded on a 1080x2400 phone is worthless
 * on a 1440x3120 one, and worthless again after the app moves a button by 8dp. Every variant below
 * therefore targets something the accessibility layer can re-resolve at replay time (visible text,
 * a content description, a resource id) and only falls back to geometry when nothing better exists.
 *
 * **Why the tap variants keep a pixel fallback:** the semantic handle may not resolve on the next
 * run — a label changed language, a list scrolled, a WebView re-rendered. Carrying `fallbackX` /
 * `fallbackY` from the recording lets a replayer degrade to "tap roughly there" instead of failing
 * the whole procedure. The fallback is advisory only; it is never the primary strategy.
 *
 * **Why relative coordinates (0.0..1.0) and not pixels for the geometric fallback:** a ratio of the
 * screen survives a resolution or density change, which is the common case when the same procedure
 * is replayed on another device. It is strictly better than absolute pixels and costs nothing.
 */
@Serializable
sealed interface RecordedAction {

    /**
     * Tap the element showing [text]. The strongest handle available: it is what the user reads on
     * screen, so it also survives app updates that reshuffle ids. [fallbackX] / [fallbackY] are the
     * absolute pixel position from the recording, used only if [text] cannot be found.
     */
    @Serializable
    @SerialName("tap_text")
    data class TapText(
        val text: String,
        val fallbackX: Int,
        val fallbackY: Int,
    ) : RecordedAction

    /**
     * Tap the element whose accessibility content description is [description]. Used for icon-only
     * buttons (close, back, overflow), which have no visible text but are still resolvable.
     */
    @Serializable
    @SerialName("tap_desc")
    data class TapDescription(
        val description: String,
        val fallbackX: Int,
        val fallbackY: Int,
    ) : RecordedAction

    /**
     * Tap the element with resource id [resourceId]. The most *stable* handle across app versions
     * and locales (ids are not translated), but the least portable across apps — and Compose screens
     * frequently expose none, which is why it ranks below text and description.
     */
    @Serializable
    @SerialName("tap_id")
    data class TapId(
        val resourceId: String,
        val fallbackX: Int,
        val fallbackY: Int,
    ) : RecordedAction

    /**
     * Last-resort tap at a screen *ratio*: (0,0) is the top-left corner, (1,1) the bottom-right.
     * Chosen when no element was a plausible target — either nothing contained the point, or the
     * only match was a near-full-screen container (see [ActionAbstractor]).
     */
    @Serializable
    @SerialName("tap_point")
    data class TapPoint(
        val relativeX: Float,
        val relativeY: Float,
    ) : RecordedAction

    /**
     * Drag between two screen ratios over [durationMs]. Ratios (not pixels) so the gesture covers
     * the same fraction of the viewport on any display; the duration is preserved because it
     * changes the receiving app's interpretation (scroll vs. fling vs. dismiss).
     */
    @Serializable
    @SerialName("swipe")
    data class Swipe(
        val fromRelativeX: Float,
        val fromRelativeY: Float,
        val toRelativeX: Float,
        val toRelativeY: Float,
        val durationMs: Int,
    ) : RecordedAction

    /**
     * Type [value] into the focused field. Stored as the final committed value rather than a
     * keystroke stream: replaying a value is IME-independent, and it lets the AI substitute
     * parameters later ("sign in as <user>") by rewriting one string.
     */
    @Serializable
    @SerialName("input_text")
    data class InputText(
        val value: String,
    ) : RecordedAction

    /** Press a normalised key name (`"back"`, `"enter"`, `"home"`). */
    @Serializable
    @SerialName("key")
    data class Key(
        val name: String,
    ) : RecordedAction

    /**
     * Pause for [ms] before the next step. The recorder inserts these automatically from inter-event
     * gaps, because replaying at recording speed lands taps on screens that have not finished
     * loading — the single most common cause of a "recorded but broken" skill.
     */
    @Serializable
    @SerialName("wait")
    data class Wait(
        val ms: Long,
    ) : RecordedAction

    /**
     * Bring [packageName] to the foreground.
     *
     * Not in the minimum action set, but included because [RecordedEvent.AppSwitch] would otherwise
     * have no representation and multi-app procedures would silently lose their app boundaries.
     * A replayer that cannot launch apps can skip this variant safely — the steps around it remain
     * valid in whatever app is currently foreground.
     */
    @Serializable
    @SerialName("launch_app")
    data class LaunchApp(
        val packageName: String,
    ) : RecordedAction
}

/**
 * Human- and model-readable one-line description of this step, e.g. `tap "签到"` or `wait 500ms`.
 *
 * Shared by the SKILL.md body and the prompt section so the natural-language view of a skill and
 * the JSON view can never drift apart. Absolute pixel fallbacks are deliberately omitted here: they
 * are noise for the model and are preserved verbatim in the JSON block instead.
 */
fun RecordedAction.describeStep(): String = when (this) {
    is RecordedAction.TapText -> "tap \"$text\""
    is RecordedAction.TapDescription -> "tap element described as \"$description\""
    is RecordedAction.TapId -> "tap element with id \"$resourceId\""
    is RecordedAction.TapPoint -> "tap at ${percent(relativeX)}%, ${percent(relativeY)}% of the screen"
    is RecordedAction.Swipe ->
        "swipe from ${percent(fromRelativeX)}%, ${percent(fromRelativeY)}% " +
            "to ${percent(toRelativeX)}%, ${percent(toRelativeY)}% over ${durationMs}ms"
    is RecordedAction.InputText -> "type \"$value\""
    is RecordedAction.Key -> "press $name"
    is RecordedAction.Wait -> "wait ${ms}ms"
    is RecordedAction.LaunchApp -> "open app $packageName"
}

/** Formats a 0.0..1.0 ratio as a whole percentage, clamped so a bad value cannot print nonsense. */
private fun percent(ratio: Float): Int = (ratio.coerceIn(0f, 1f) * 100f).toInt()
