package com.kainext.duo.skills.recording

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One raw interaction observed while the user demonstrates a task on their phone.
 *
 * **Why this model is platform-neutral:** `commonMain` cannot reference `android.view.MotionEvent`
 * or `AccessibilityNodeInfo`, so the recorder must never touch the platform event stream directly.
 * The Android side owns that translation: an accessibility service (or a root/Shizuku input hook)
 * converts what it observes into these five primitives and hands them to [SkillRecorder] together
 * with a [ScreenElement] snapshot of the tree at that instant. Everything downstream — coordinate
 * abstraction, storage, prompt rendering — is pure Kotlin and therefore testable off-device.
 *
 * **Why raw coordinates rather than semantic ids at this layer:** this is the *input* layer. The
 * same tap is abstracted later into a text/id/relative-point [RecordedAction]; keeping the raw pixel
 * event around until abstraction time lets [ActionAbstractor] apply heuristics (smallest containing
 * element, area sanity check) in one place instead of duplicating them in every platform adapter.
 *
 * [timestampMs] is wall-clock epoch milliseconds (`Clock.System.now().toEpochMilliseconds()`), not
 * uptime, so a recording stays meaningful across process restarts and can be merged or compared.
 * The recorder only consumes *differences* between timestamps, so any source that advances
 * monotonically works.
 */
@Serializable
sealed interface RecordedEvent {
    val timestampMs: Long

    /**
     * A single finger tap at ([x], [y]) in screen pixels. Platforms should report the *up* position,
     * because that is the point the user believed they touched (the down position drifts during a
     * sloppy tap, and it is the up point that the target app acts on).
     */
    @Serializable
    @SerialName("tap")
    data class Tap(
        override val timestampMs: Long,
        val x: Int,
        val y: Int,
    ) : RecordedEvent

    /**
     * A drag from ([x1], [y1]) to ([x2], [y2]) that took [durationMs]. Duration is kept because a
     * 2-second drag and a 100 ms flick are different gestures to the receiving app (scroll vs.
     * swipe-to-dismiss vs. fling distance), so replay has to reproduce the same one.
     */
    @Serializable
    @SerialName("swipe")
    data class Swipe(
        override val timestampMs: Long,
        val x1: Int,
        val y1: Int,
        val x2: Int,
        val y2: Int,
        val durationMs: Int,
    ) : RecordedEvent

    /**
     * Text committed to the focused field. Platforms report the *resulting field value*, not the
     * individual keystrokes: replaying a value is robust, whereas replaying key events depends on
     * the IME showing the same keyboard layout as during recording.
     */
    @Serializable
    @SerialName("text")
    data class TextInput(
        override val timestampMs: Long,
        val value: String,
    ) : RecordedEvent

    /**
     * A hardware or navigation key press, with [name] already normalised by the platform
     * (e.g. `KEYCODE_BACK` -> `"back"`, `KEYCODE_ENTER` -> `"enter"`) so `commonMain` never has to
     * know Android key codes.
     */
    @Serializable
    @SerialName("key")
    data class KeyPress(
        override val timestampMs: Long,
        val name: String,
    ) : RecordedEvent

    /**
     * Foreground app changed to [packageName]. Recorded because a procedure spanning two apps
     * ("open the banking app, then the authenticator") is otherwise indistinguishable from one that
     * silently continued in the wrong app — and because replay needs the switch as an action.
     */
    @Serializable
    @SerialName("app")
    data class AppSwitch(
        override val timestampMs: Long,
        val packageName: String,
    ) : RecordedEvent
}

/**
 * Short, stable label for an event kind, for logging and UI ("recording: tap").
 *
 * Deliberately *not* derived from `@SerialName` at runtime: `commonMain` has no reflection, and a
 * plain `when` costs nothing and cannot fail. Not used for debounce — that compares full event
 * signatures, since two taps of the same kind are not the same tap.
 */
fun RecordedEvent.kindLabel(): String = when (this) {
    is RecordedEvent.Tap -> "tap"
    is RecordedEvent.Swipe -> "swipe"
    is RecordedEvent.TextInput -> "text"
    is RecordedEvent.KeyPress -> "key"
    is RecordedEvent.AppSwitch -> "app"
}
