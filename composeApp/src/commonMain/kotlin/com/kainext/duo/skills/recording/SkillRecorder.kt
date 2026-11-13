@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.kainext.duo.skills.recording

import kotlinx.coroutines.sync.Mutex
import kotlin.time.Clock

/**
 * Records a user demonstration and turns it into a reusable [RecordedSkill].
 *
 * **Why the platform pushes events instead of the recorder observing them:** `commonMain` cannot
 * reference `MotionEvent` or `AccessibilityNodeInfo`, so this class never touches the platform event
 * stream. The Android side owns a thin adapter — an accessibility service or a root/Shizuku input
 * hook — that calls [start], then [recordEvent] once per observed interaction (with a
 * [ScreenElement] snapshot of the tree at that instant), then [stop]. Everything here is pure Kotlin,
 * which is what makes the abstraction logic testable without a device.
 *
 * **What the recorder adds on top of the raw stream** — the three things that decide whether a
 * recording replays or merely replays *once*:
 * 1. **Automatic waits.** A gap longer than [WAIT_THRESHOLD_MS] between events becomes an explicit
 *    [RecordedAction.Wait]. Replay at recording speed taps into screens that have not finished
 *    loading; the wait is the difference between a working skill and an intermittently broken one.
 * 2. **Debounce.** A trembling finger emits several taps on the same pixel within a few
 *    milliseconds. Recording them would double-submit every form, so identical events closer than
 *    [DEBOUNCE_WINDOW_MS] collapse into one.
 * 3. **Abstraction.** Every event goes through [ActionAbstractor] with the snapshot the platform
 *    supplied, so what is stored is text/id/relative-point — not pixels.
 *
 * **Concurrency.** The public API is deliberately non-suspend, because the platform calls it from an
 * accessibility callback thread and a UI thread that are not coroutines. State is still guarded by a
 * [Mutex] (the only lock primitive available in `commonMain`), acquired by [locked] with a short
 * spin. That is safe *because* every critical section is a handful of list operations: none of them
 * suspends, blocks, allocates unboundedly, or re-enters the lock, so a waiter can never spin for
 * more than microseconds and the lock can never deadlock. Do not put a blocking call inside a
 * critical section — that would turn the spin into a real hang.
 *
 * **Bounded by design.** At most [MAX_ACTIONS] actions are kept. A runaway recording (a stuck
 * finger, an endless scroll) would otherwise produce a skill too large to inject into a prompt or to
 * replay sensibly. On overflow the recorder stops accepting events and sets [isTruncated], but keeps
 * the session open so [stop] still returns the actions captured so far — discarding a user's whole
 * demonstration because they tapped a few times too many would be a worse outcome than a partial
 * skill that reports itself as partial.
 *
 * @param clock injectable time source, used only for the finished skill's `createdAtMs`. Event
 *   timing always comes from the events themselves, so a skewed clock cannot distort the waits and
 *   tests can pin the creation timestamp.
 */
class SkillRecorder(
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {

    /** Immutable facts fixed at [start]; replaced wholesale on restart so no stale field survives. */
    private data class Session(
        val packageName: String?,
        val appLabel: String?,
        val screenWidth: Int,
        val screenHeight: Int,
    )

    private val mutex = Mutex()

    private var session: Session? = null
    private var actions: MutableList<RecordedAction> = mutableListOf()

    /** Last accepted event's timestamp; drives wait insertion and the debounce window. */
    private var lastAcceptedAtMs: Long? = null

    /** Debounce signature of the last accepted event, or null when nothing was accepted yet. */
    private var lastSignature: String? = null

    /** Foreground package as currently believed, so a no-op app switch is not recorded twice. */
    private var foregroundPackage: String? = null

    private var truncated = false

    /** True while a session is open and [stop] would return a skill. */
    fun isRecording(): Boolean = locked { session != null }

    /**
     * Number of [RecordedAction]s captured so far, including auto-inserted waits.
     *
     * Not the number of raw events: a swipe is one action, and a long pause contributes a wait the
     * user never performed. This is the number the UI should show, because it tracks skill size.
     */
    val recordedCount: Int
        get() = locked { actions.size }

    /**
     * True when [MAX_ACTIONS] was reached and later events were dropped.
     *
     * Surfaced so the UI can tell the user the procedure was cut short instead of silently shipping
     * a skill that stops halfway.
     */
    val isTruncated: Boolean
        get() = locked { truncated }

    /**
     * Begins a new recording, discarding anything previously captured.
     *
     * [screenWidth] / [screenHeight] are the display size in pixels. They are required rather than
     * inferred because [ActionAbstractor] needs them both to compute relative coordinates and to
     * apply its "this element is effectively the whole screen" test; without them every geometric
     * fallback would be expressed against an unknown axis.
     *
     * [packageName] / [appLabel] describe the foreground app at the moment recording started. They
     * are stored in [SkillContext] because a procedure is meaningless without knowing which app it
     * applies to.
     *
     * Calling [start] while already recording restarts from scratch. There is no implicit save: a
     * half-finished demonstration is rarely worth keeping, and a silent save would surprise the user.
     */
    fun start(packageName: String?, appLabel: String?, screenWidth: Int, screenHeight: Int) {
        locked {
            session = Session(
                packageName = packageName?.trim()?.takeIf { it.isNotEmpty() },
                appLabel = appLabel?.trim()?.takeIf { it.isNotEmpty() },
                screenWidth = screenWidth.coerceAtLeast(0),
                screenHeight = screenHeight.coerceAtLeast(0),
            )
            actions = mutableListOf()
            lastAcceptedAtMs = null
            lastSignature = null
            foregroundPackage = packageName?.trim()?.takeIf { it.isNotEmpty() }
            truncated = false
        }
    }

    /**
     * Records one observed interaction together with the screen snapshot taken at that instant.
     *
     * The platform should call this for *every* interaction seen while recording. Passing an empty
     * snapshot is not an error — the abstractor degrades to relative coordinates — but it costs the
     * skill its semantic handles, so adapters should read the accessibility tree at event time
     * rather than reusing a stale one.
     *
     * Silently ignored when not recording, so a late callback from a previous session cannot leak
     * into the next one.
     */
    fun recordEvent(event: RecordedEvent, elements: List<ScreenElement>) {
        locked {
            val active = session ?: return@locked
            if (truncated) return@locked

            val signature = signatureOf(event)
            val previousAt = lastAcceptedAtMs
            if (previousAt != null && signature == lastSignature && event.timestampMs - previousAt <= DEBOUNCE_WINDOW_MS) {
                return@locked
            }

            // The pause belongs *before* the action it precedes, so it is emitted first. It is capped
            // at MAX_WAIT_MS: a recording left running while the user answered a call should not
            // encode a five-minute stall into the skill.
            if (previousAt != null) {
                val gap = event.timestampMs - previousAt
                if (gap > WAIT_THRESHOLD_MS && !append(RecordedAction.Wait(gap.coerceAtMost(MAX_WAIT_MS)))) {
                    return@locked
                }
            }

            val abstracted = abstract(event, elements, active)
            if (abstracted != null && !append(abstracted)) return@locked

            lastAcceptedAtMs = event.timestampMs
            lastSignature = signature
        }
    }

    /**
     * Ends the recording and returns the skill, or null when nothing usable was captured.
     *
     * A zero-action recording is a mistap on the record button, not a skill; returning null lets the
     * caller say "nothing was recorded" instead of saving an empty procedure the AI would then
     * faithfully "follow" and do nothing. The session is always cleared, so a failed [stop] cannot
     * be retried into a duplicate skill.
     *
     * [name] and [description] override the generated ones. Both are optional so the platform can
     * prompt the user for a name, or call `stop()` headlessly and still get a sensible skill; the
     * generated values are intentionally descriptive rather than pretty — renaming is the UI's job.
     */
    fun stop(name: String? = null, description: String? = null): RecordedSkill? = locked {
        val active = session
        val captured = actions.toList()
        clear()
        if (active == null || captured.isEmpty()) return@locked null

        RecordedSkill(
            name = name?.trim()?.takeIf { it.isNotEmpty() } ?: defaultName(active, captured),
            description = description?.trim()?.takeIf { it.isNotEmpty() } ?: defaultDescription(active, captured),
            actions = captured,
            context = SkillContext(
                packageName = active.packageName,
                appLabel = active.appLabel,
                keywords = RecordedSkill.extractKeywords(captured, active.packageName, active.appLabel),
                screenWidth = active.screenWidth,
                screenHeight = active.screenHeight,
            ),
            createdAtMs = clock(),
            recordedFrom = active.packageName,
        )
    }

    /**
     * Abandons the recording and discards everything captured.
     *
     * Distinct from [stop]: the user cancelled, so no skill is produced and the discarded actions
     * never reach storage. Idempotent — cancelling when idle is a no-op, which lets the UI call it
     * defensively on dismissal.
     */
    fun cancel() {
        locked { clear() }
    }

    /** Appends [action], or reports that the cap was reached. Must be called under the lock. */
    private fun append(action: RecordedAction): Boolean {
        if (actions.size >= MAX_ACTIONS) {
            truncated = true
            return false
        }
        actions.add(action)
        return true
    }

    /** Converts one raw event into its semantic action, or null when it carries no replayable step. */
    private fun abstract(
        event: RecordedEvent,
        elements: List<ScreenElement>,
        active: Session,
    ): RecordedAction? = when (event) {
        is RecordedEvent.Tap -> ActionAbstractor.abstractTap(
            tap = event,
            elements = elements,
            screenWidth = active.screenWidth,
            screenHeight = active.screenHeight,
        )

        is RecordedEvent.Swipe -> ActionAbstractor.abstractSwipe(
            swipe = event,
            screenWidth = active.screenWidth,
            screenHeight = active.screenHeight,
        )

        // A blank value means the field was cleared, not that the user typed nothing: replaying
        // "type ''" is a no-op, so it is dropped rather than stored as a meaningless step.
        is RecordedEvent.TextInput -> event.value.takeIf { it.isNotBlank() }?.let { RecordedAction.InputText(it) }

        is RecordedEvent.KeyPress -> event.name.trim().takeIf { it.isNotEmpty() }?.let { RecordedAction.Key(it) }

        is RecordedEvent.AppSwitch -> {
            val target = event.packageName.trim().takeIf { it.isNotEmpty() }
            if (target == null || target == foregroundPackage) {
                // Blank package, or already in front: the switch is a no-op and would only add noise.
                null
            } else {
                foregroundPackage = target
                RecordedAction.LaunchApp(target)
            }
        }
    }

    /**
     * Identity of an event for debounce purposes: only the fields that distinguish two *physical*
     * gestures. Timestamps are excluded by construction, since they are the thing being compared.
     */
    private fun signatureOf(event: RecordedEvent): String = when (event) {
        is RecordedEvent.Tap -> "tap:${event.x}:${event.y}"
        is RecordedEvent.Swipe -> "swipe:${event.x1}:${event.y1}:${event.x2}:${event.y2}"
        is RecordedEvent.TextInput -> "text:${event.value}"
        is RecordedEvent.KeyPress -> "key:${event.name}"
        is RecordedEvent.AppSwitch -> "app:${event.packageName}"
    }

    /**
     * Fallback display name, e.g. `WeChat 签到`. Built from the app label plus the first thing the
     * user tapped, because that pair is what the user themselves would call the procedure. Falls
     * back progressively to the package's short name and finally a generic label, so [stop] can
     * never produce an empty name.
     */
    private fun defaultName(active: Session, captured: List<RecordedAction>): String {
        val app = active.appLabel
            ?: active.packageName?.substringAfterLast('.')?.takeIf { it.isNotEmpty() }
        val trigger = firstTappedText(captured)
        return when {
            app != null && trigger != null -> "$app $trigger"
            app != null -> "$app procedure"
            trigger != null -> trigger
            else -> "Recorded skill"
        }
    }

    /**
     * Fallback one-line description. States the app and the step count, which is what the model
     * needs to judge applicability without reading the whole step list, and names the first tapped
     * label as a hint at the intent.
     */
    private fun defaultDescription(active: Session, captured: List<RecordedAction>): String {
        val where = active.appLabel
            ?: active.packageName
            ?: "the device"
        val steps = captured.count { it !is RecordedAction.Wait }
        val trigger = firstTappedText(captured)
        return if (trigger != null) {
            "Recorded $steps-step procedure in $where, starting with tapping \"$trigger\"."
        } else {
            "Recorded $steps-step procedure in $where."
        }
    }

    /** First non-blank label the user tapped — the best short hint at what the procedure is for. */
    private fun firstTappedText(captured: List<RecordedAction>): String? = captured
        .filterIsInstance<RecordedAction.TapText>()
        .firstOrNull { it.text.isNotBlank() }
        ?.text

    /** Clears all session state. Must be called under the lock. */
    private fun clear() {
        session = null
        actions = mutableListOf()
        lastAcceptedAtMs = null
        lastSignature = null
        foregroundPackage = null
        truncated = false
    }

    /**
     * Runs [block] holding [mutex], spinning while another thread holds it.
     *
     * The spin is sound only because every caller's critical section is a few list operations with
     * no suspension point, no blocking call, and no nested acquisition — so the holder always
     * releases within microseconds. Keep it that way: adding a blocking call inside a critical
     * section turns this into a genuine hang rather than a theoretical one.
     */
    private fun <T> locked(block: () -> T): T {
        while (!mutex.tryLock()) {
            // Busy-wait: contended for microseconds at most (see KDoc above).
        }
        try {
            return block()
        } finally {
            mutex.unlock()
        }
    }

    companion object {
        /**
         * Hard cap on captured actions. Bounds both the serialized size and the prompt cost of the
         * resulting skill, and stops a runaway recording from producing something unreplayable.
         */
        const val MAX_ACTIONS = 200

        /**
         * Gaps longer than this become an explicit wait. 400 ms is roughly the point at which a
         * human pause means "waiting for something to load" rather than "moving my finger".
         */
        const val WAIT_THRESHOLD_MS = 400L

        /**
         * Ceiling on a single recorded wait. Beyond ~30 s the pause is almost certainly the user
         * being interrupted, and encoding it would make replay appear hung.
         */
        const val MAX_WAIT_MS = 30_000L

        /**
         * Window inside which an identical event is treated as the same physical gesture. 100 ms is
         * short enough that a deliberate double-tap (typically ≥150 ms apart) still survives.
         */
        const val DEBOUNCE_WINDOW_MS = 100L
    }
}
