package com.kainext.duo.tools.uitree

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.kainext.duo.KaiAccessibilityService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "UiTreePruner"

/**
 * A pruned screen plus the live nodes it was built from.
 *
 * The [live] list is index-aligned with [slice]'s nodes: `live[k]` is the `AccessibilityNodeInfo`
 * behind `slice.nodes[k]`. Keeping both matters because the pruned model deliberately drops
 * everything the model does not need (window ids, parent links, raw flags) while the executor
 * still needs the real node to call `ACTION_CLICK` on it. Re-walking the tree to find a node
 * again would be both slower and racy — the second walk could see a different screen.
 *
 * Nodes are handed out without `recycle()`: `AccessibilityNodeInfo.recycle()` has been deprecated
 * since API 33 and is a no-op there, and calling it on older releases from a coroutine that does
 * not own the node's lifetime risks a use-after-recycle crash for a saving measured in kilobytes.
 */
class UiTreeSnapshot(
    val slice: UiSlice,
    val live: List<AccessibilityNodeInfo>,
) {
    /** Live node for slice index [index], or null when the slice no longer contains it. */
    fun liveNode(index: Int): AccessibilityNodeInfo? {
        val position = slice.nodes.indexOfFirst { it.i == index }
        return if (position >= 0) live.getOrNull(position) else null
    }
}

/**
 * Turns the live accessibility tree into the compact [UiSlice] a model can read.
 *
 * ## Why prune at all
 *
 * A real phone screen exposes 300-800 nodes: every layout wrapper, every icon, every spacer.
 * Almost all of them are invisible to the user and irrelevant to the task. Shipping the raw tree
 * costs tens of thousands of tokens *per turn* and, worse, buries the four controls that matter
 * in hundreds that do not — models pick the wrong node far more often from a noisy tree than
 * from a short one. Pruning is therefore not only a cost optimisation, it is the accuracy fix.
 *
 * ## The trade-off being made
 *
 * The filter keeps a node when the *user* could see or touch it: it has text, a content
 * description, is clickable, is scrollable, or accepts text. Everything else is dropped — but
 * crucially, dropping a node does **not** drop its subtree. Wrapper `FrameLayout`s and
 * `ConstraintLayout`s are exactly the nodes that get filtered, and their children are usually the
 * real controls, so the walk always descends regardless of whether the parent was kept.
 *
 * The caps ([maxNodes], [maxDepth]) are the second half of the trade-off. Breadth-first order
 * means the cap keeps the *shallowest* nodes, which are the ones nearest the screen root and the
 * ones a user would name first ("the toolbar", "the list", "the OK button"). Cutting a 400-node
 * screen to 120 nodes at BFS order therefore removes the tail of a long list rather than the
 * header of the screen. [UiSlice.truncated] records that the cut happened so the model does not
 * mistake a partial view for a complete one.
 *
 * ## Threading
 *
 * `AccessibilityNodeInfo` access and `getBoundsInScreen` must run on the thread that owns the
 * accessibility connection — in practice the main thread. Reading them from a background
 * coroutine is not merely slow, it can return stale or empty bounds because the node's view is
 * being laid out on the main thread at that moment. Every public entry point therefore switches
 * to `Dispatchers.Main.immediate` before touching the tree, and only the resulting plain data
 * ([UiSlice]) crosses back to the caller's dispatcher.
 */
class UiTreePruner(
    /** Hard cap on emitted nodes; the default is tuned so a typical screen costs ~1.5k tokens. */
    val maxNodes: Int = DEFAULT_MAX_NODES,
    /** Depth cap, a stack-overflow and pathological-nesting guard rather than a normal limit. */
    val maxDepth: Int = DEFAULT_MAX_DEPTH,
) {

    /**
     * Reads and prunes the active window.
     *
     * Returns null when the accessibility service is not running or has no active window, which
     * is the signal for callers to fall back to the `uiautomator` shell path. A non-null result
     * can still contain zero nodes (a blank screen, a game surface), so callers must check
     * [UiSlice.isEmpty] separately from null.
     */
    suspend fun snapshot(): UiTreeSnapshot? = withContext(Dispatchers.Main.immediate) {
        try {
            val service = KaiAccessibilityService.current() ?: return@withContext null
            val root = service.activeRoot() ?: return@withContext null
            prune(root)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.w(TAG, "snapshot failed", e)
            null
        }
    }

    /** Convenience wrapper for callers that only need the pruned data, not the live nodes. */
    suspend fun slice(): UiSlice? = snapshot()?.slice

    /**
     * Prunes an already-obtained [root]. Public so tests and callers that hold their own root
     * (a `findAccessibilityNodeInfosByText` result, a specific window) can reuse the same rules
     * instead of growing a second, subtly different filter.
     */
    suspend fun sliceOf(root: AccessibilityNodeInfo): UiSlice = withContext(Dispatchers.Main.immediate) {
        prune(root).slice
    }

    private fun prune(root: AccessibilityNodeInfo): UiTreeSnapshot {
        val drafts = ArrayList<Draft>(maxNodes.coerceAtMost(256))
        val live = ArrayList<AccessibilityNodeInfo>(maxNodes.coerceAtMost(256))
        // Frames carry the index of the nearest *kept* ancestor, not of the literal parent: when a
        // wrapper is filtered out its children must still be able to de-duplicate against it.
        val queue = ArrayDeque<Frame>()
        queue.addLast(Frame(root, depth = 0, parent = NO_PARENT))
        var visited = 0
        // The cap counts *usable* nodes, not candidates: a screen full of Button-wraps-TextView
        // pairs would otherwise lose half its budget to nodes de-duplication is about to remove,
        // and the model would see 60 controls on a screen that has 120.
        var keptCount = 0
        var truncated = false

        while (queue.isNotEmpty()) {
            // The visit cap is a second safety net: a corrupted or hostile tree that keeps
            // producing nodes must not spin forever. Eight candidates per kept node is generous
            // for real screens and cheap to abandon on a bad one.
            if (visited >= maxNodes * VISIT_FACTOR) {
                truncated = true
                break
            }
            val frame = queue.removeFirst()
            visited++
            val node = frame.node

            val draft = draftOf(node)
            if (draft != null && keptCount >= maxNodes) {
                // BFS guarantees everything already kept is at or above this depth, so cutting
                // here drops the deepest, least important tail of the screen rather than the
                // header the user is looking at.
                truncated = true
                break
            }
            // Index this node will have once kept, or the ancestor its children should point at
            // when it is filtered out.
            val selfIndex = if (draft == null) {
                frame.parent
            } else {
                drafts.add(draft)
                live.add(node)
                keptCount++
                if (deduplicate(drafts, frame.parent, draft)) keptCount--
                drafts.size - 1
            }

            // Descend even when this node is filtered out: wrappers are the most common filtered
            // node and they usually hold the only actionable children on the screen. Enqueueing
            // happens after the node is indexed so children can reference their kept ancestor.
            if (frame.depth < maxDepth) {
                for (index in 0 until node.childCount) {
                    val child = try {
                        node.getChild(index)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Throwable) {
                        null
                    }
                    if (child != null) {
                        queue.addLast(Frame(child, frame.depth + 1, selfIndex))
                    }
                }
            } else if (node.childCount > 0) {
                // Hitting the depth cap with children still below means the view is incomplete,
                // even if every node we did emit survived the filter.
                truncated = true
            }
        }

        // Re-number after de-duplication so indices stay dense: the model uses `i` as a handle
        // for click/text/scroll, and gaps in the sequence make it invent indices that never
        // existed. `live` is compacted with the same predicate to stay index-aligned.
        val nodes = ArrayList<UiNode>(drafts.size)
        val compactedLive = ArrayList<AccessibilityNodeInfo>(live.size)
        for (position in drafts.indices) {
            val draft = drafts[position]
            if (draft.dropped) continue
            nodes.add(draft.toUiNode(nodes.size))
            compactedLive.add(live[position])
        }
        return UiTreeSnapshot(
            slice = UiSlice(nodes = nodes, truncated = truncated, totalVisited = visited),
            live = compactedLive,
        )
    }

    /**
     * Parent/child de-duplication: an Android `Button` is very often a clickable wrapper around a
     * `TextView` that carries the same text, so the model would see "OK" twice and have to guess
     * which one is real. The child wins — it is the more specific node and the one drawn last —
     * but it inherits the parent's interaction flags first, because the wrapper is frequently the
     * only clickable half of the pair and deleting it outright would remove the only target.
     */
    private fun deduplicate(drafts: List<Draft>, parentIndex: Int, child: Draft): Boolean {
        if (parentIndex == NO_PARENT) return false
        val parent = drafts[parentIndex]
        if (parent.dropped || !isDuplicateLabel(parent, child)) return false
        parent.dropped = true
        child.clickable = child.clickable || parent.clickable
        child.scrollable = child.scrollable || parent.scrollable
        child.editable = child.editable || parent.editable
        return true
    }

    /**
     * Applies the keep/filter rules to one node, or returns null when the user could neither see
     * nor touch it. Bounds are read here because a zero-sized node is invisible in practice even
     * when the platform still reports it as "visible to user" (it happens with collapsed rows and
     * zero-height containers during a layout pass).
     */
    private fun draftOf(node: AccessibilityNodeInfo): Draft? {
        val text = node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val desc = node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val clickable = node.isClickable
        val scrollable = node.isScrollable
        val editable = node.isEditable
        if (text == null && desc == null && !clickable && !scrollable && !editable) return null

        val visible = try {
            node.isVisibleToUser
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            true
        }
        if (!visible) return null

        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.width() <= 0 || rect.height() <= 0) return null

        return Draft(
            text = text?.let(::clip),
            desc = desc?.let(::clip),
            id = node.viewIdResourceName?.takeIf { it.isNotBlank() },
            cls = node.className?.toString()?.substringAfterLast('.')?.takeIf { it.isNotBlank() },
            bounds = "[${rect.left},${rect.top}][${rect.right},${rect.bottom}]",
            cx = rect.centerX(),
            cy = rect.centerY(),
            clickable = clickable,
            scrollable = scrollable,
            editable = editable,
            checked = when {
                node.isCheckable || node.isChecked -> node.isChecked
                else -> null
            },
            enabled = node.isEnabled,
        )
    }

    /**
     * Two nodes are duplicates when both carry the same non-blank text (or, failing that, the
     * same description) and neither adds a label the other lacks. Nodes with no label at all are
     * never duplicates of each other: two unlabelled clickable wrappers in a list are different
     * rows, and collapsing them would delete real targets.
     */
    private fun isDuplicateLabel(parent: Draft, child: Draft): Boolean {
        val parentLabel = parent.text ?: parent.desc
        val childLabel = child.text ?: child.desc
        if (parentLabel.isNullOrBlank() || childLabel.isNullOrBlank()) return false
        return parentLabel == childLabel
    }

    /**
     * Long labels are clipped because a single "Terms and conditions" paragraph can cost more
     * tokens than the rest of the screen combined. The prefix is enough for the model to identify
     * and click the control; `click(text)` re-matches against the *fresh* tree, so a clipped
     * label still resolves as a contains-match and nothing becomes unclickable.
     */
    private fun clip(value: String): String = if (value.length <= MAX_LABEL_CHARS) value else value.take(MAX_LABEL_CHARS) + "…"

    /** A node waiting to be visited, with the index of its nearest kept ancestor. */
    private class Frame(val node: AccessibilityNodeInfo, val depth: Int, val parent: Int)

    /**
     * Mutable working copy of a node. De-duplication has to retract an already-emitted parent and
     * hand its flags to the child, which immutable [UiNode] values cannot express, so the walk
     * builds drafts and converts once at the end.
     */
    private class Draft(
        val text: String?,
        val desc: String?,
        val id: String?,
        val cls: String?,
        val bounds: String,
        val cx: Int,
        val cy: Int,
        var clickable: Boolean,
        var scrollable: Boolean,
        var editable: Boolean,
        val checked: Boolean?,
        val enabled: Boolean,
    ) {
        var dropped: Boolean = false

        fun toUiNode(index: Int): UiNode = UiNode(
            i = index,
            text = text,
            desc = desc,
            id = id,
            cls = cls,
            bounds = bounds,
            cx = cx,
            cy = cy,
            clickable = clickable,
            scrollable = scrollable,
            editable = editable,
            checked = checked,
            enabled = enabled,
        )
    }

    companion object {
        /**
         * 120 nodes is the point where a typical screen (chat, settings, list) is fully covered
         * while staying under roughly 1.5k tokens of compact JSON. Raising it buys the tail of
         * long lists at a cost the model rarely uses; lowering it starts cutting real controls.
         */
        const val DEFAULT_MAX_NODES: Int = 120

        /** Deep enough for every real layout, shallow enough that a cyclic/broken tree terminates. */
        const val DEFAULT_MAX_DEPTH: Int = 40

        private const val NO_PARENT = -1
        private const val VISIT_FACTOR = 8
        private const val MAX_LABEL_CHARS = 160
    }
}
