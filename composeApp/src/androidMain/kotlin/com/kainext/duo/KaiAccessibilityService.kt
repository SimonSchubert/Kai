package com.kainext.duo

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "KaiA11y"

/**
 * The accessibility service behind the "accessibility" privilege mode.
 *
 * Why this exists next to root and Shizuku: both of those can inject input via `input tap`,
 * but that path is blind — it has no idea what is on screen, so the agent has to screenshot
 * and guess coordinates. An accessibility service can read the live node tree (with exact
 * bounds and labels) and dispatch gestures through the same pipeline the system uses for
 * TalkBack, which makes taps land on the intended control even when the layout shifted.
 *
 * It also keeps working when the device is locked down or `input` is restricted, and it needs
 * no root — so it is the fallback for users who will not or cannot root.
 *
 * The service holds no state of its own beyond the node-tree snapshot it exposes statically;
 * the tool layer drives it.
 */
class KaiAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _connected.value = true
        Log.i(TAG, "accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // The tool layer pulls the tree on demand; events are not needed for that, and
        // reacting to every window change would burn battery for no benefit.
    }

    override fun onInterrupt() {
        // Nothing to interrupt: gestures are one-shot and awaited by the caller.
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        _connected.value = false
        Log.i(TAG, "accessibility service unbound")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        _connected.value = false
        super.onDestroy()
    }

    /** The currently attached service, or null when the user has not enabled it. */
    companion object {
        @Volatile
        private var instance: KaiAccessibilityService? = null

        private val _connected = MutableStateFlow(false)
        val connected: StateFlow<Boolean> = _connected.asStateFlow()

        fun current(): KaiAccessibilityService? = instance
    }

    // ---- actions used by AndroidUiTool ----

    /** Root node of the active window, or null when no window is available. */
    fun activeRoot(): AccessibilityNodeInfo? = try {
        rootInActiveWindow
    } catch (e: Throwable) {
        Log.w(TAG, "rootInActiveWindow failed", e)
        null
    }

    /**
     * Taps the centre of [node] via ACTION_CLICK. Returns false when the node is not
     * clickable, so the caller can fall back to a coordinate gesture on its bounds.
     */
    fun clickNode(node: AccessibilityNodeInfo): Boolean = try {
        if (!node.isClickable) {
            false
        } else {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
    } catch (e: Throwable) {
        Log.w(TAG, "clickNode failed", e)
        false
    }

    /** Types [text] into [node] (or the focused field when null). */
    fun setText(node: AccessibilityNodeInfo?, text: String): Boolean = try {
        val target = node ?: findFocusedEditable()
        if (target == null) {
            false
        } else {
            val args = android.os.Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }
    } catch (e: Throwable) {
        Log.w(TAG, "setText failed", e)
        false
    }

    private fun findFocusedEditable(): AccessibilityNodeInfo? = try {
        rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
    } catch (_: Throwable) {
        null
    }

    /**
     * Dispatches a tap gesture at absolute screen coordinates. Needed for views that expose
     * bounds but are not marked clickable (custom-drawn lists, canvas apps, games).
     */
    fun gestureTap(x: Float, y: Float, durationMs: Long = 50): Boolean = dispatchPath { path ->
        path.moveTo(x, y)
        GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(1))
    }

    /** Swipes from (x1,y1) to (x2,y2) over [durationMs]. */
    fun gestureSwipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 300): Boolean =
        dispatchPath { path ->
            path.moveTo(x1, y1)
            path.lineTo(x2, y2)
            GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(1))
        }

    private fun dispatchPath(build: (Path) -> GestureDescription.StrokeDescription): Boolean = try {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            false
        } else {
            val path = Path()
            val stroke = build(path)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            dispatchGesture(gesture, null, null)
        }
    } catch (e: Throwable) {
        Log.w(TAG, "dispatchGesture failed", e)
        false
    }

    /** Presses a global navigation key: back, home or recents. */
    fun performGlobal(action: GlobalAction): Boolean = try {
        val id = when (action) {
            GlobalAction.BACK -> GLOBAL_ACTION_BACK
            GlobalAction.HOME -> GLOBAL_ACTION_HOME
            GlobalAction.RECENTS -> GLOBAL_ACTION_RECENTS
            GlobalAction.NOTIFICATIONS -> GLOBAL_ACTION_NOTIFICATIONS
        }
        performGlobalAction(id)
    } catch (e: Throwable) {
        Log.w(TAG, "performGlobalAction failed", e)
        false
    }

    enum class GlobalAction { BACK, HOME, RECENTS, NOTIFICATIONS }
}
