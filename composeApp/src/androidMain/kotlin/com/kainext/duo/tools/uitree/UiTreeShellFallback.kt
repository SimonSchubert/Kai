package com.kainext.duo.tools.uitree

import android.util.Log
import com.kainext.duo.tools.PrivilegedResult
import com.kainext.duo.tools.PrivilegedShell
import kotlinx.coroutines.CancellationException

private const val TAG = "UiTreeShellFallback"

/**
 * The no-accessibility path: reads the screen with `uiautomator dump` and acts with `input`.
 *
 * ## Why this exists at all
 *
 * The accessibility service is the better backend — it sees the live tree and injects gestures
 * through the same pipeline as TalkBack — but it is also the one backend the user has to switch
 * on in system settings, and it is unavailable on some ROMs and inside work profiles. Without a
 * fallback, the whole dynamic-UI feature would be dead exactly when the user needs it most (a
 * device they cannot configure). `uiautomator dump` needs only root/Shizuku/app-shell, which the
 * app already has for its other device tools.
 *
 * ## Why the output is converted into the same [UiNode] model
 *
 * The two backends produce different shapes (an `AccessibilityNodeInfo` graph vs an XML file), but
 * the *consumer* is the same model. Normalising both into [UiSlice] means a prompt, a stored
 * action, or a test cannot tell which backend produced it, and the executor's index/label
 * resolution works unchanged. It also keeps the token cost identical, which matters because the
 * fallback is slower and would otherwise be tempted to dump the raw XML "since we have it".
 *
 * ## The honest limitation
 *
 * A dump is a snapshot of the moment `uiautomator` ran; it is always at least a few hundred
 * milliseconds old, and it fails outright on screens that block accessibility (some banking apps,
 * DRM video). The `[truncated]` flag and the explicit error messages here say so rather than
 * letting the model believe it is looking at live UI.
 */
class UiTreeShellFallback(
    private val shell: PrivilegedShell,
    private val maxNodes: Int = UiTreePruner.DEFAULT_MAX_NODES,
    private val maxDepth: Int = UiTreePruner.DEFAULT_MAX_DEPTH,
) {

    /** File the dump is written to before being read back, in a path both root and shell can write. */
    private val dumpPath: String = "/sdcard/kai_ui_tree_dump.xml"

    /**
     * Dumps and prunes the current screen.
     *
     * Returns an empty slice (never null) when the dump fails: callers combine this with the
     * accessibility result and pick whichever has content, and a null here would force every
     * caller to handle a second absence case for no benefit. The failure reason is logged; the
     * user-facing error comes from the action that needed the tree.
     */
    suspend fun snapshot(): UiSlice {
        val xml = dumpXml() ?: return UiSlice.empty()
        return try {
            pruneXml(xml)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.w(TAG, "pruneXml failed", e)
            UiSlice.empty()
        }
    }

    /**
     * Runs `uiautomator dump` and returns the XML, or null when the backend is unavailable.
     *
     * `--compressed` is used because the uncompressed dump includes every layout attribute and is
     * several times larger for the same nodes; we only read a handful of attributes, so the
     * compressed form loses nothing. The `cat` is chained with `;` on purpose: `uiautomator dump`
     * often exits non-zero after a *successful* dump (it reports the file path on stdout and a
     * warning on stderr), so its exit code cannot be trusted — only the file content can.
     */
    private suspend fun dumpXml(): String? {
        val result = shell.execute(
            "uiautomator dump --compressed ${shell.shellQuote(dumpPath)} >/dev/null 2>&1; " +
                "cat ${shell.shellQuote(dumpPath)} 2>/dev/null",
            DUMP_TIMEOUT_MS,
        )
        if (result.stdout.isBlank() || !result.stdout.contains("<hierarchy")) {
            Log.w(TAG, "uiautomator dump produced no hierarchy: ${result.stderr.take(200)}")
            return null
        }
        return result.stdout
    }

    /**
     * Parses the `uiautomator` XML into the compact model, applying the same keep/filter rules and
     * caps as the accessibility pruner so both backends cost the same and truncate the same way.
     *
     * The XML is walked in document order, which for a `uiautomator` dump is already a
     * depth-first, top-to-bottom traversal. That is close enough to the accessibility pruner's
     * breadth-first order that indices are stable across a backend switch for simple screens, and
     * it avoids building an intermediate tree just to re-order it.
     */
    fun pruneXml(xml: String): UiSlice {
        val nodes = ArrayList<UiNode>(maxNodes.coerceAtMost(256))
        // Depth and label of the most recently kept node. De-duplication is only ever applied to a
        // direct parent/child pair, so this pair is enough state: in document order the node right
        // before a child is either its parent or a previous sibling's subtree, and the depth check
        // tells the two apart.
        var lastKeptLabel: String? = null
        var lastKeptDepth = -1
        var visited = 0
        var truncated = false
        var depth = 0

        for (tag in NODE_TAG.findAll(xml)) {
            val raw = tag.value
            if (raw.startsWith("</")) {
                depth = (depth - 1).coerceAtLeast(0)
                continue
            }
            val nodeDepth = depth
            if (!raw.endsWith("/>")) depth++
            visited++

            if (visited >= maxNodes * VISIT_FACTOR) {
                truncated = true
                break
            }
            if (nodeDepth > maxDepth) {
                // Deeper subtrees are skipped, so the screen is not complete even though the node
                // cap was not reached — the caller must be told.
                truncated = true
                continue
            }

            val attrs = attributes(raw)
            val text = attrs["text"]?.trim()?.takeIf { it.isNotEmpty() }
            val desc = attrs["content-desc"]?.trim()?.takeIf { it.isNotEmpty() }
            val clickable = attrs["clickable"] == "true"
            val scrollable = attrs["scrollable"] == "true"
            val editable = attrs["class"]?.contains("EditText") == true
            if (text == null && desc == null && !clickable && !scrollable && !editable) continue
            if (attrs["visible-to-user"] == "false") continue

            val rect = UiRect.parse(attrs["bounds"].orEmpty()) ?: continue
            if (!rect.hasArea) continue

            val label = text ?: desc
            // A wrapper and the child carrying the identical label are one control; the child is
            // the more specific node and the one actually drawn, so it replaces the parent. Siblings
            // are never merged: two rows labelled "OK" are two real targets.
            var inherited: UiNode? = null
            if (label != null && label == lastKeptLabel && nodeDepth == lastKeptDepth + 1) {
                inherited = nodes.removeLastOrNull()
            }
            lastKeptLabel = label
            lastKeptDepth = nodeDepth

            if (nodes.size >= maxNodes) {
                truncated = true
                break
            }
            nodes.add(
                UiNode(
                    i = nodes.size,
                    text = text?.let(::clip),
                    desc = desc?.let(::clip),
                    id = attrs["resource-id"]?.takeIf { it.isNotBlank() },
                    cls = attrs["class"]?.substringAfterLast('.')?.takeIf { it.isNotBlank() },
                    bounds = "[${rect.left},${rect.top}][${rect.right},${rect.bottom}]",
                    cx = rect.centerX,
                    cy = rect.centerY,
                    // A dropped wrapper is often the only clickable half of the pair, so the child
                    // inherits its interaction flags instead of losing the only way to tap it.
                    clickable = clickable || inherited?.clickable == true,
                    scrollable = scrollable || inherited?.scrollable == true,
                    editable = editable || inherited?.editable == true,
                    checked = when (attrs["checked"]) {
                        "true" -> true
                        "false" -> false
                        else -> null
                    },
                    enabled = attrs["enabled"] != "false",
                ),
            )
        }
        return UiSlice(nodes = nodes, truncated = truncated, totalVisited = visited)
    }

    // ---- actions ----

    /**
     * Clicks by index or label through the shell: `uiautomator` gives the coordinates, `input tap`
     * performs the tap. The tree is dumped again right before acting, for the same reason the
     * accessibility path re-reads it — a coordinate from an older dump is a guess.
     */
    suspend fun click(index: Int?, needle: String?, lastSlice: UiSlice?): UiActionResult {
        if (index == null && needle == null) {
            return failure("click needs either an index or a text label", UiActionResult.BACKEND_SHELL)
        }
        val slice = snapshot()
        if (slice.isEmpty) return dumpFailed()
        val target = resolve(slice, index, needle, lastSlice)
            ?: return failure(
                "no node matches ${needle?.let { "'$it'" } ?: "#$index"} in the uiautomator dump " +
                    "(${slice.nodes.size} nodes); run a dump to see what is on screen",
                UiActionResult.BACKEND_SHELL,
            )
        val result = shell.execute("input tap ${target.cx} ${target.cy} 2>&1", ACTION_TIMEOUT_MS)
        return if (result.success) {
            UiActionResult(
                success = true,
                backend = result.backend,
                message = "tapped #${target.i} at [${target.cx},${target.cy}]",
                sliceAfter = null,
            )
        } else {
            failure("input tap failed: ${result.toToolOutput()}", result.backend)
        }
    }

    /** Taps raw coordinates through `input tap`. */
    suspend fun tap(x: Int, y: Int): UiActionResult {
        val result = shell.execute("input tap $x $y 2>&1", ACTION_TIMEOUT_MS)
        return if (result.success) {
            UiActionResult(true, result.backend, "tapped [$x,$y]", null)
        } else {
            failure("input tap failed: ${result.toToolOutput()}", result.backend)
        }
    }

    /** Swipes through `input swipe`. */
    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int): UiActionResult {
        val result = shell.execute("input swipe $x1 $y1 $x2 $y2 $durationMs 2>&1", ACTION_TIMEOUT_MS)
        return if (result.success) {
            UiActionResult(true, result.backend, "swiped [$x1,$y1] -> [$x2,$y2] over ${durationMs}ms", null)
        } else {
            failure("input swipe failed: ${result.toToolOutput()}", result.backend)
        }
    }

    /**
     * Long-presses raw coordinates through `input swipe`.
     *
     * `input` has no dedicated long-press verb; a swipe whose start and end are the same point
     * degenerates into a press of exactly [durationMs], which is what `input`'s own help documents
     * for this purpose. Using it keeps the shell path to one process spawn.
     */
    suspend fun longPress(x: Int, y: Int, durationMs: Int): UiActionResult {
        val result = shell.execute("input swipe $x $y $x $y $durationMs 2>&1", ACTION_TIMEOUT_MS)
        return if (result.success) {
            UiActionResult(true, result.backend, "long-pressed [$x,$y] for ${durationMs}ms", null)
        } else {
            failure("input swipe (long press) failed: ${result.toToolOutput()}", result.backend)
        }
    }

    /** Long-presses the node at [index] / [needle], re-dumping before it acts. */
    suspend fun longPressAtNode(
        index: Int?,
        needle: String?,
        lastSlice: UiSlice?,
        durationMs: Int,
    ): UiActionResult {
        if (index == null && needle == null) {
            return failure("long_press needs either an index or a text label", UiActionResult.BACKEND_SHELL)
        }
        val slice = snapshot()
        if (slice.isEmpty) return dumpFailed()
        val target = resolve(slice, index, needle, lastSlice)
            ?: return failure(
                "no node matches ${needle?.let { "'$it'" } ?: "#$index"} in the uiautomator dump " +
                    "(${slice.nodes.size} nodes); run a dump to see what is on screen",
                UiActionResult.BACKEND_SHELL,
            )
        val result = shell.execute("input swipe ${target.cx} ${target.cy} ${target.cx} ${target.cy} $durationMs 2>&1", ACTION_TIMEOUT_MS)
        return if (result.success) {
            UiActionResult(
                success = true,
                backend = result.backend,
                message = "long-pressed #${target.i} at [${target.cx},${target.cy}] for ${durationMs}ms",
                sliceAfter = null,
            )
        } else {
            failure("input swipe (long press) failed: ${result.toToolOutput()}", result.backend)
        }
    }

    /**
     * Types through `input text`.
     *
     * `input text` splits on spaces, so every space becomes `%s` — the shell's own escape, not a
     * placeholder we can avoid. The value is single-quoted afterwards so that shell metacharacters
     * in the text ($, `, ;) are not interpreted, which would otherwise let a typed message run a
     * command.
     */
    suspend fun type(value: String, index: Int?, lastSlice: UiSlice?): UiActionResult {
        if (value.isEmpty()) return failure("text action needs a value", UiActionResult.BACKEND_SHELL)
        if (index != null) {
            val slice = snapshot()
            if (slice.isEmpty) return dumpFailed()
            val target = resolve(slice, index, null, lastSlice)
                ?: return failure("no node #$index in the uiautomator dump", UiActionResult.BACKEND_SHELL)
            // Focus the field first: `input text` goes to whatever holds focus, and without this
            // the text lands on the previously focused widget (or nowhere).
            shell.execute("input tap ${target.cx} ${target.cy} 2>&1", ACTION_TIMEOUT_MS)
        }
        val escaped = shell.shellQuote(value.replace(" ", "%s"))
        val result = shell.execute("input text $escaped 2>&1", ACTION_TIMEOUT_MS)
        return if (result.success) {
            UiActionResult(true, result.backend, "typed ${value.length} chars", null)
        } else {
            failure("input text failed: ${result.toToolOutput()}", result.backend)
        }
    }

    /** Presses a key through `input keyevent`. */
    suspend fun key(name: String): UiActionResult {
        val keyCode = KEY_ALIASES[name] ?: name
        if (!KEY_NAME_PATTERN.matches(keyCode)) {
            return failure(
                "unknown key '$name'; use BACK, HOME, RECENTS, ENTER, TAB, or a KEYCODE_* name",
                UiActionResult.BACKEND_SHELL,
            )
        }
        val result = shell.execute("input keyevent ${shell.shellQuote(keyCode)} 2>&1", ACTION_TIMEOUT_MS)
        return if (result.success) {
            UiActionResult(true, result.backend, "pressed $keyCode", null)
        } else {
            failure("input keyevent failed: ${result.toToolOutput()}", result.backend)
        }
    }

    /**
     * Scrolls with a swipe derived from the container's own bounds. `uiautomator` cannot perform
     * scroll actions itself, so the fallback is always a gesture — with the same 60% travel rule
     * the accessibility path uses, for the same reason (edges belong to the system).
     */
    suspend fun scroll(index: Int, forward: Boolean, lastSlice: UiSlice?): UiActionResult {
        val slice = snapshot()
        if (slice.isEmpty) return dumpFailed()
        val target = resolve(slice, index, null, lastSlice)
            ?: return failure("no node #$index in the uiautomator dump", UiActionResult.BACKEND_SHELL)
        val rect = target.rect()
        if (rect == null || !rect.hasArea) {
            return failure("#${target.i} has no bounds to scroll", UiActionResult.BACKEND_SHELL)
        }
        val span = (rect.height * SCROLL_TRAVEL_RATIO).toInt().coerceAtLeast(MIN_SCROLL_SPAN)
        val startY = if (forward) rect.top + (rect.height - span) / 2 else rect.bottom - (rect.height - span) / 2
        val endY = if (forward) startY + span else startY - span
        return swipe(rect.centerX, startY, rect.centerX, endY, DEFAULT_SCROLL_MS)
    }

    // ---- helpers ----

    /**
     * Resolves an index or a label against the dumped slice. A label always wins, and an index is
     * re-anchored on the label it used to point at, mirroring [UiActionExecutor] so that a
     * backend switch does not change which control an action hits.
     */
    private fun resolve(slice: UiSlice, index: Int?, needle: String?, lastSlice: UiSlice?): UiNode? {
        if (needle != null) return slice.matchByText(needle)
        if (index == null) return null
        val rememberedLabel = lastSlice?.nodeAt(index)?.labels()?.firstOrNull()
        if (rememberedLabel != null) {
            slice.matchByText(rememberedLabel)?.let { return it }
        }
        return slice.nodeAt(index)
    }

    private fun dumpFailed(): UiActionResult = failure(
        "uiautomator dump produced nothing. Either no shell backend is available " +
            "(enable root/Shizuku) or the foreground app blocks accessibility inspection.",
        UiActionResult.BACKEND_SHELL,
    )

    /** Parses `key="value"` pairs out of one `<node ...>` tag. */
    private fun attributes(tag: String): Map<String, String> {
        val out = HashMap<String, String>(16)
        for (match in ATTRIBUTE_PATTERN.findAll(tag)) {
            out[match.groupValues[1]] = unescape(match.groupValues[2])
        }
        return out
    }

    /**
     * `uiautomator` writes text as XML-escaped, so `&amp;`, `&lt;` and `&quot;` appear verbatim in
     * labels the user sees as `&`, `<` and `"`. Un-escaping keeps label matching working when the
     * model quotes what it saw on screen.
     */
    private fun unescape(value: String): String {
        if (!value.contains('&')) return value
        return value
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
    }

    private fun clip(value: String): String =
        if (value.length <= MAX_LABEL_CHARS) value else value.take(MAX_LABEL_CHARS) + "…"

    /** Result of a dump, exposed for callers that want the raw text (debugging, recordings). */
    suspend fun rawDump(): PrivilegedResult = shell.execute(
        "uiautomator dump --compressed ${shell.shellQuote(dumpPath)} 2>&1; cat ${shell.shellQuote(dumpPath)} 2>/dev/null",
        DUMP_TIMEOUT_MS,
    )

    companion object {
        private const val DUMP_TIMEOUT_MS = 15_000L
        private const val ACTION_TIMEOUT_MS = 10_000L
        private const val VISIT_FACTOR = 8
        private const val MAX_LABEL_CHARS = 160
        private const val SCROLL_TRAVEL_RATIO = 0.6
        private const val MIN_SCROLL_SPAN = 80
        private const val DEFAULT_SCROLL_MS = 300

        /** `<node ...>`, `</node>` and self-closing `<node .../>` in document order. */
        private val NODE_TAG = Regex("</?node\\b[^>]*>")

        private val ATTRIBUTE_PATTERN = Regex("([\\w:-]+)=\"([^\"]*)\"")

        /** Guards against a key name being used to smuggle extra shell arguments. */
        private val KEY_NAME_PATTERN = Regex("[A-Za-z0-9_]+")

        /** Friendly names mapped to the `input keyevent` spellings. */
        private val KEY_ALIASES: Map<String, String> = mapOf(
            "BACK" to "KEYCODE_BACK",
            "HOME" to "KEYCODE_HOME",
            "RECENTS" to "KEYCODE_APP_SWITCH",
            "ENTER" to "KEYCODE_ENTER",
            "TAB" to "KEYCODE_TAB",
            "POWER" to "KEYCODE_POWER",
            "MENU" to "KEYCODE_MENU",
            "SEARCH" to "KEYCODE_SEARCH",
            "DELETE" to "KEYCODE_DEL",
        )
    }
}
