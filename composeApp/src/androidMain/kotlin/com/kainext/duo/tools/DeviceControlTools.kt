package com.kainext.duo.tools

import com.kainext.duo.KaiAccessibilityService
import com.kainext.duo.tools.uitree.UiAction
import com.kainext.duo.tools.uitree.UiActionExecutor
import com.kainext.duo.tools.uitree.UiActionResult
import com.kainext.duo.tools.uitree.UiTreePruner
import com.kainext.duo.tools.uitree.UiTreeShellFallback

import android.util.Base64
import com.kainext.duo.network.tools.ParameterSchema
import com.kainext.duo.network.tools.Tool
import com.kainext.duo.network.tools.ToolInfo
import com.kainext.duo.network.tools.ToolSchema
import kotlin.time.Duration.Companion.seconds

private const val MAX_FILE_BYTES = 200_000
private const val MAX_UI_NODES = 80

/** Shared helper: wraps a privileged result as the JSON-able map every tool returns. */
private fun PrivilegedResult.asToolMap(): Map<String, Any> = mapOf(
    "success" to success,
    "backend" to backend,
    "exit_code" to exitCode,
    "stdout" to stdout,
    "stderr" to stderr,
)

/**
 * Runs arbitrary shell commands with root or Shizuku privileges. This is the low-level
 * primitive behind the file and UI tools; it is also exposed on its own so the agent can
 * use device utilities (`am`, `pm`, `settings`, `dumpsys`, …) the other tools don't wrap.
 */
class AndroidShellTool(private val shell: PrivilegedShell) : Tool {
    override val schema = ToolSchema(
        name = ID,
        description = "Execute a shell command on the Android device with root or Shizuku (adb shell) privileges. " +
            "Use standard Android shell tools: am, pm, settings, dumpsys, input, screencap, cmd, getprop, etc. " +
            "The working directory is the shell default. Output is capped by the tool executor.",
        parameters = mapOf(
            "command" to ParameterSchema("string", "The shell command to run, e.g. 'getprop ro.product.model'.", required = true),
            "timeout_seconds" to ParameterSchema("number", "Timeout in seconds (1-120, default 30).", required = false),
        ),
    )

    override val timeout = 130.seconds

    override suspend fun execute(args: Map<String, Any>): Any {
        val command = args["command"]?.toString()?.takeIf { it.isNotBlank() }
            ?: return mapOf("success" to false, "error" to "Missing command")
        val timeoutSeconds = (args["timeout_seconds"] as? Number)?.toLong()?.coerceIn(1L, 120L) ?: 30L
        return shell.execute(command, timeoutSeconds * 1000L).asToolMap()
    }

    companion object {
        const val ID = "android_shell"

        val toolInfo = ToolInfo(
            id = ID,
            name = "Android Shell",
            description = "Run shell commands with root/Shizuku privileges",
            isEnabled = false,
            userToggleable = false,
        )
    }
}

/**
 * Reads and writes files under shared storage (`/sdcard`) by going through the privileged
 * shell, so it works even when the app itself does not hold MANAGE_EXTERNAL_STORAGE.
 */
class AndroidFileTool(private val shell: PrivilegedShell) : Tool {

    override val schema = ToolSchema(
        name = ID,
        description = "Access files on shared storage (/sdcard) using root/Shizuku privileges. " +
            "Actions: list (default), read, write, append, delete, move, mkdir, stat. " +
            "Paths must be under /sdcard, /storage or /mnt/media_rw.",
        parameters = mapOf(
            "action" to ParameterSchema("string", "One of: list, read, write, append, delete, move, mkdir, stat.", required = true),
            "path" to ParameterSchema("string", "Absolute path, e.g. /sdcard/Download/file.txt.", required = true),
            "content" to ParameterSchema("string", "Text content for write/append.", required = false),
            "destination" to ParameterSchema("string", "Destination path for move.", required = false),
            "recursive" to ParameterSchema("boolean", "For delete: remove directories recursively (default true).", required = false),
        ),
    )

    override val timeout = 90.seconds

    override suspend fun execute(args: Map<String, Any>): Any {
        val action = args["action"]?.toString()?.lowercase() ?: "list"
        val rawPath = args["path"]?.toString()?.takeIf { it.isNotBlank() }
            ?: return mapOf("success" to false, "error" to "Missing path")
        val path = normalizeSharedPath(rawPath)
            ?: return mapOf("success" to false, "error" to "Path must be under /sdcard, /storage or /mnt/media_rw: $rawPath")
        val quoted = shell.shellQuote(path)

        return when (action) {
            "list" -> shell.execute("ls -la $quoted 2>&1").asToolMap()

            "stat" -> shell.execute("stat -c '%n %s bytes %a %y' $quoted 2>&1 || ls -ld $quoted 2>&1").asToolMap()

            "read" -> {
                val result = shell.execute("head -c $MAX_FILE_BYTES $quoted 2>&1")
                if (result.success && result.stdout.length >= MAX_FILE_BYTES) {
                    result.asToolMap() + mapOf("truncated" to true)
                } else {
                    result.asToolMap()
                }
            }

            "write", "append" -> {
                val content = args["content"]?.toString()
                    ?: return mapOf("success" to false, "error" to "Missing content")
                val encoded = Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                val redirect = if (action == "append") ">>" else ">"
                shell.execute("printf '%s' '$encoded' | base64 -d $redirect $quoted 2>&1 && echo OK").asToolMap()
            }

            "delete" -> {
                val recursive = (args["recursive"] as? Boolean) ?: true
                val flag = if (recursive) "-rf" else "-f"
                shell.execute("rm $flag $quoted 2>&1 && echo OK").asToolMap()
            }

            "move" -> {
                val destination = args["destination"]?.toString()?.takeIf { it.isNotBlank() }
                    ?: return mapOf("success" to false, "error" to "Missing destination")
                val dest = normalizeSharedPath(destination)
                    ?: return mapOf("success" to false, "error" to "Destination must be under /sdcard: $destination")
                shell.execute("mv $quoted ${shell.shellQuote(dest)} 2>&1 && echo OK").asToolMap()
            }

            "mkdir" -> shell.execute("mkdir -p $quoted 2>&1 && echo OK").asToolMap()

            else -> mapOf("success" to false, "error" to "Unknown action: $action")
        }
    }

    /** Accepts /sdcard and /storage/emulated/0 spellings; rejects anything outside shared storage. */
    private fun normalizeSharedPath(path: String): String? {
        val trimmed = path.trim()
        if (!trimmed.startsWith("/")) return null
        val allowed = listOf("/sdcard", "/storage", "/mnt/media_rw")
        if (allowed.none { trimmed == it || trimmed.startsWith("$it/") }) return null
        if (trimmed.contains("/../")) return null
        return trimmed
    }

    companion object {
        const val ID = "android_files"

        val toolInfo = ToolInfo(
            id = ID,
            name = "Android Files",
            description = "Read/write files under /sdcard with privileged access",
            isEnabled = false,
            userToggleable = false,
        )
    }
}

/**
 * Drives the device UI: taps, long presses, swipes, text entry, key events, app launch and
 * foreground handover, plus a compact accessibility-tree dump the model can reason about.
 *
 * ## Design note
 *
 * The action set and the "every action returns the screen it produced" contract follow the
 * GUI-agent design of [Eta](https://github.com/Mangi-11/Eta) (GPL-3.0, Copyright © 2026 蛮吉
 * Mangi-11). This file is an independent implementation for Kai's accessibility/root/Shizuku
 * stack — no Eta source is copied — see `docs/CREDITS-eta.md`.
 *
 * Every mutating action goes through [UiActionExecutor], which re-reads the tree before acting
 * (so a stale index cannot hit whatever moved into that spot) and waits for the screen to settle
 * afterwards (so the returned nodes are the *new* screen, not the old one). That is why the tool
 * reports `nodes` after an action: the model does not need a second `dump` to see the result,
 * which halves the round trips of every interaction.
 */
class AndroidUiTool(private val shell: PrivilegedShell) : Tool {

    private val uiPruner = UiTreePruner()
    private val uiFallback = UiTreeShellFallback(shell)
    private val uiActions = UiActionExecutor(pruner = uiPruner, shell = shell, fallback = uiFallback)

    override val schema = ToolSchema(
        name = ID,
        description = "Control the phone user interface using accessibility, root or Shizuku. " +
            "Actions: dump (accessibility tree), tap_node (tap by label — preferred), long_press, tap, swipe, " +
            "scroll, text, key, launch, foreground, wait_for_window, current_app, screenshot, wait, back, home, recents. " +
            "Every action that changes the screen returns the resulting nodes, so you usually do not need a separate dump. " +
            "Prefer tap_node / long_press with a visible label over raw coordinates: labels are re-resolved against a " +
            "fresh tree, coordinates are not. Use dump first when you do not know the label.",
        parameters = mapOf(
            "action" to ParameterSchema(
                "string",
                "One of: dump, tap, tap_node, long_press, swipe, scroll, text, key, launch, foreground, " +
                    "wait_for_window, current_app, screenshot, wait, back, home, recents.",
                required = true,
            ),
            "target" to ParameterSchema(
                "string",
                "For tap_node / long_press: visible text, content description, or resource id of the control. " +
                    "Prefers an exact match, then a case-insensitive contains match.",
                required = false,
            ),
            "index" to ParameterSchema(
                "number",
                "Node index from a dump, as an alternative to target. Indices are re-anchored on the label they " +
                    "pointed at, so they survive a re-render but must come from a recent dump.",
                required = false,
            ),
            "x" to ParameterSchema("number", "X coordinate for tap / swipe start / long_press.", required = false),
            "y" to ParameterSchema("number", "Y coordinate for tap / swipe start / long_press.", required = false),
            "x2" to ParameterSchema("number", "Swipe end X.", required = false),
            "y2" to ParameterSchema("number", "Swipe end Y.", required = false),
            "duration_ms" to ParameterSchema(
                "number",
                "Gesture duration in ms: swipe default 300, long_press default 800 (clamped to 500-10000).",
                required = false,
            ),
            "text" to ParameterSchema("string", "Text to type for the text action.", required = false),
            "key" to ParameterSchema("string", "Key event name or code: HOME, BACK, ENTER, POWER, VOLUME_UP, or a number.", required = false),
            "forward" to ParameterSchema("boolean", "Scroll direction for the scroll action (default true = towards the end).", required = false),
            "package" to ParameterSchema("string", "Package name for launch / foreground / wait_for_window, e.g. com.android.settings.", required = false),
            "activity" to ParameterSchema("string", "Optional activity for launch, e.g. .Settings.", required = false),
            "seconds" to ParameterSchema("number", "Seconds to wait for the wait action.", required = false),
            "timeout_ms" to ParameterSchema("number", "How long wait_for_window waits for the window (default 5000).", required = false),
            "include_nodes" to ParameterSchema(
                "boolean",
                "Attach the resulting nodes to the reply (default true). Set false for actions whose result you " +
                    "already know, to save tokens.",
                required = false,
            ),
            "base64" to ParameterSchema(
                "boolean",
                "For screenshot: also return the PNG bytes base64-encoded (default false; large).",
                required = false,
            ),
        ),
    )

    override val timeout = 120.seconds

    override suspend fun execute(args: Map<String, Any>): Any {
        val action = args["action"]?.toString()?.lowercase() ?: return missing("action")
        val includeNodes = (args["include_nodes"] as? Boolean) ?: true
        val index = (args["index"] as? Number)?.toInt()
        val target = args["target"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val x = (args["x"] as? Number)?.toInt()
        val y = (args["y"] as? Number)?.toInt()
        val duration = (args["duration_ms"] as? Number)?.toInt()

        return when (action) {
            "dump" -> dumpUi()

            "tap" -> {
                if (x == null || y == null) return missing("x/y for tap")
                run(UiAction.Tap(x, y), includeNodes)
            }

            "tap_node" -> {
                if (index == null && target == null) return missing("target or index for tap_node")
                run(UiAction.Click(index = index, text = target), includeNodes)
            }

            "long_press" -> {
                if (x != null && y != null) {
                    run(UiAction.LongPress(x = x, y = y, durationMs = duration ?: DEFAULT_LONG_PRESS_MS), includeNodes)
                } else if (index != null || target != null) {
                    run(
                        UiAction.LongPress(
                            index = index,
                            text = target,
                            durationMs = duration ?: DEFAULT_LONG_PRESS_MS,
                        ),
                        includeNodes,
                    )
                } else {
                    missing("target, index or x/y for long_press")
                }
            }

            "swipe" -> {
                val x2 = (args["x2"] as? Number)?.toInt()
                val y2 = (args["y2"] as? Number)?.toInt()
                if (x == null || y == null || x2 == null || y2 == null) return missing("x/y/x2/y2 for swipe")
                run(UiAction.Swipe(x, y, x2, y2, (duration ?: 300).coerceIn(50, 5_000)), includeNodes)
            }

            "scroll" -> {
                if (index == null) return missing("index for scroll (dump first to find the list)")
                val forward = (args["forward"] as? Boolean) ?: true
                run(UiAction.Scroll(index, forward), includeNodes)
            }

            "text" -> {
                val text = args["text"]?.toString()
                if (text.isNullOrEmpty()) return missing("text")
                run(UiAction.Text(text, index), includeNodes)
            }

            "key" -> {
                val key = args["key"]?.toString()?.takeIf { it.isNotBlank() } ?: return missing("key")
                run(UiAction.Key(key), includeNodes)
            }

            "back" -> run(UiAction.Key("BACK"), includeNodes)
            "home" -> run(UiAction.Key("HOME"), includeNodes)
            "recents" -> run(UiAction.Key("RECENTS"), includeNodes)

            "launch" -> {
                val pkg = args["package"]?.toString()?.takeIf { it.isNotBlank() } ?: return missing("package")
                launch(pkg, args["activity"]?.toString()?.takeIf { it.isNotBlank() }, includeNodes)
            }

            "foreground" -> {
                val pkg = args["package"]?.toString()?.takeIf { it.isNotBlank() } ?: return missing("package")
                foreground(pkg, includeNodes)
            }

            "wait_for_window" -> {
                val pkg = args["package"]?.toString()?.takeIf { it.isNotBlank() } ?: return missing("package")
                val timeoutMs = ((args["timeout_ms"] as? Number)?.toLong() ?: DEFAULT_WINDOW_TIMEOUT_MS)
                    .coerceIn(500L, 30_000L)
                val observed = awaitForegroundPackage(pkg, timeoutMs)
                buildMap<String, Any> {
                    put("observed", observed != null)
                    put("package", pkg)
                    if (observed != null) {
                        put("foreground", observed)
                        snapshotPrompt()?.let { put("nodes", it) }
                    } else {
                        put(
                            "note",
                            "no window for $pkg was observed within ${timeoutMs}ms. It may still be starting " +
                                "(slow cold start, splash screen) or it may have refused to launch. Call " +
                                "wait_for_window again, or screenshot to see what is actually on screen.",
                        )
                    }
                }
            }

            "current_app" -> {
                val a11yPkg = KaiAccessibilityService.current()?.activeRoot()?.packageName?.toString()
                if (a11yPkg != null) {
                    mapOf("success" to true, "backend" to "accessibility", "package" to a11yPkg)
                } else {
                    shell.execute("dumpsys window 2>/dev/null | grep -E 'mCurrentFocus|mFocusedApp' | head -4").asToolMap()
                }
            }

            "screenshot" -> screenshot((args["base64"] as? Boolean) ?: false)

            "wait" -> {
                val seconds = (args["seconds"] as? Number)?.toInt()?.coerceIn(1, 10_000) ?: 1
                kotlinx.coroutines.delay(seconds * 1_000L)
                snapshotPrompt()?.let { nodes ->
                    mapOf("success" to true, "waited_seconds" to seconds, "nodes" to nodes)
                } ?: mapOf("success" to true, "waited_seconds" to seconds)
            }

            else -> mapOf("success" to false, "error" to "Unknown action: $action")
        }
    }

    /** Runs one action and turns the executor's result into a tool reply. */
    private suspend fun run(action: UiAction, includeNodes: Boolean): Map<String, Any> =
        uiActions.execute(action).toToolMap(includeNodes)

    /** The current screen as prompt text, or null when neither backend can produce a tree. */
    private suspend fun snapshotPrompt(): String? {
        val slice = uiActions.snapshot()
        return if (slice.isEmpty) null else slice.toPromptText()
    }

    /**
     * Starts [pkg] and reports whether a window was actually observed.
     *
     * The shell's `am start` returns 0 as soon as the intent is *delivered*, which is not the same
     * thing as the app being in front: a disabled app, a crash during `onCreate`, or a package
     * that only has a non-launcher entry point all report success and then show the previous
     * screen. Reporting "launched" in that situation is how an agent goes on to tap coordinates
     * belonging to the old app, so the reply separates "the command was accepted" from "the
     * window was seen".
     */
    private suspend fun launch(pkg: String, activity: String?, includeNodes: Boolean): Map<String, Any> {
        val command = if (activity != null) {
            "am start -n ${shell.shellQuote("$pkg/$activity")} 2>&1"
        } else {
            "monkey -p ${shell.shellQuote(pkg)} -c android.intent.category.LAUNCHER 1 2>&1"
        }
        val started = shell.execute(command)
        val observed = awaitForegroundPackage(pkg, LAUNCH_VERIFY_MS)
        return buildMap<String, Any> {
            put("success", started.success && observed != null)
            put("backend", started.backend)
            put("stdout", started.stdout)
            put("stderr", started.stderr)
            put("package", pkg)
            put("window_observed", observed != null)
            if (observed != null) {
                put("foreground", observed)
                if (includeNodes) snapshotPrompt()?.let { put("nodes", it) }
            } else {
                put(
                    "note",
                    "the start command was accepted but no window for $pkg appeared within " +
                        "${LAUNCH_VERIFY_MS}ms. Possible causes: the app crashed, it is disabled or its " +
                        "launcher activity is missing. Screenshot to see what is on screen before acting.",
                )
            }
        }
    }

    /**
     * Brings [pkg] to the physical screen and waits for it to be there.
     *
     * This is Kai's equivalent of handing a screen back to the user: after a task the app may sit
     * behind a system dialog or another app, and "the work is done" is not the same as "the user
     * can see it". The action does not guess — it brings the task forward and then verifies focus.
     */
    private suspend fun foreground(pkg: String, includeNodes: Boolean): Map<String, Any> {
        // `am start` with the launcher intent is the documented way to move an existing task to the
        // front: it reuses the running activity (singleTop/singleTask) instead of starting a second
        // copy, and it works without knowing the concrete activity class.
        val started = shell.execute(
            "am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER " +
                "-p ${shell.shellQuote(pkg)} 2>&1",
        )
        val observed = awaitForegroundPackage(pkg, LAUNCH_VERIFY_MS)
        return buildMap<String, Any> {
            put("success", observed != null)
            put("backend", started.backend)
            put("package", pkg)
            // `foreground` is what was actually observed on screen. When nothing could be
            // confirmed the key is absent rather than empty, so "unknown" cannot be misread as
            // "not running".
            observed?.let { put("foreground", it) }
            put("handed_to_user", observed != null)
            if (observed == null) {
                put(
                    "note",
                    "could not confirm that $pkg is on screen. Output: " +
                        started.stdout.trim().ifEmpty { started.stderr.trim() },
                )
            } else if (includeNodes) {
                snapshotPrompt()?.let { put("nodes", it) }
            }
        }
    }

    /**
     * Polls until [pkg] is the focused package, returning the observed foreground package or null
     * on timeout. Accessibility answers this in-process; without the service it takes one
     * `dumpsys window` per poll, which is why the interval is coarse.
     */
    private suspend fun awaitForegroundPackage(pkg: String, timeoutMs: Long): String? {
        val started = System.currentTimeMillis()
        while (true) {
            val current = foregroundPackage()
            if (current != null && current == pkg) return current
            if (System.currentTimeMillis() - started >= timeoutMs) return null
            kotlinx.coroutines.delay(if (current == null) FOREGROUND_POLL_SHELL_MS else FOREGROUND_POLL_MS)
        }
    }

    /** The package that currently owns input focus, from accessibility or `dumpsys window`. */
    private suspend fun foregroundPackage(): String? {
        KaiAccessibilityService.current()?.activeRoot()?.packageName?.toString()?.let { return it }
        val result = shell.execute(
            "dumpsys window 2>/dev/null | grep -E 'mCurrentFocus|mFocusedApp' | head -2",
        )
        if (!result.success) return null
        // Lines look like: mCurrentFocus=Window{abc u0 com.android.settings/com.android.settings.HomeActivity}
        val match = Regex("u0 ([A-Za-z0-9_.]+)/").find(result.stdout) ?: return null
        return match.groupValues[1]
    }

    /**
     * Captures the screen at its native resolution and reports where it went.
     *
     * The file is written as PNG with no scaling and no re-encode: the design reference for this
     * behaviour (Eta) is explicit that a screenshot is evidence, and a downscaled or JPEG-compressed
     * copy silently destroys the small text the agent may need to read. The bytes are only returned
     * inline when the caller asks for them, because a full-screen PNG is far too large to ship in
     * every tool reply.
     */
    private suspend fun screenshot(includeBase64: Boolean): Map<String, Any> {
        val path = "/sdcard/kai_screenshot_${System.currentTimeMillis()}.png"
        val result = shell.execute("screencap -p ${shell.shellQuote(path)} 2>&1 && echo OK")
        if (!result.success) return result.asToolMap() + mapOf("error" to "screencap failed")
        val file = java.io.File(path)
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { android.graphics.BitmapFactory.decodeFile(path, bounds) }
        return buildMap<String, Any> {
            put("success", true)
            put("backend", result.backend)
            put("path", path)
            put("bytes", file.length())
            if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                put("width", bounds.outWidth)
                put("height", bounds.outHeight)
            }
            put("note", "full-resolution PNG, not scaled or re-encoded")
            if (includeBase64 && file.isFile && file.length() <= MAX_INLINE_SCREENSHOT_BYTES) {
                runCatching { android.util.Base64.encodeToString(file.readBytes(), android.util.Base64.NO_WRAP) }
                    .onSuccess { put("base64", it) }
            } else if (includeBase64) {
                put("base64_skipped", "file larger than $MAX_INLINE_SCREENSHOT_BYTES bytes")
            }
        }
    }

    /** Dumps the accessibility tree and condenses it to the nodes an agent can act on. */
    private suspend fun dumpUi(): Map<String, Any> {
        // The pruned slice is the preferred output: a raw accessibility tree of a real app is
        // routinely 800-3000 nodes, most of them layout wrappers with no text and no action. That
        // costs tens of thousands of tokens and buries the handful of controls the model can
        // actually use. UiTreePruner keeps only actionable or labelled nodes and reports indices
        // the action executor can resolve against a fresh snapshot.
        uiPruner.slice()?.let { slice ->
            return mapOf(
                "success" to true,
                "backend" to "accessibility",
                "node_count" to slice.nodes.size,
                "truncated" to slice.truncated,
                "nodes" to slice.nodes.map { it.toToolMap() },
            )
        }
        // Shell fallback: same pruning, applied to the uiautomator XML.
        uiFallback.snapshot().takeIf { it.nodes.isNotEmpty() }?.let { slice ->
            return mapOf(
                "success" to true,
                "backend" to "shell",
                "node_count" to slice.nodes.size,
                "truncated" to slice.truncated,
                "nodes" to slice.nodes.map { it.toToolMap() },
            )
        }
        val dumpPath = "/sdcard/kai_ui_dump.xml"
        val result = shell.execute(
            "uiautomator dump --compressed ${shell.shellQuote(dumpPath)} >/dev/null 2>&1; cat ${shell.shellQuote(dumpPath)} 2>/dev/null",
        )
        if (!result.success || result.stdout.isBlank()) {
            return result.asToolMap() + mapOf("error" to (result.stderr.ifBlank { "uiautomator dump produced no output" }))
        }
        val nodes = parseUiNodes(result.stdout)
        return mapOf(
            "success" to true,
            "backend" to result.backend,
            "node_count" to nodes.size,
            "nodes" to nodes.take(MAX_UI_NODES),
        )
    }

    private fun parseUiNodes(xml: String): List<Map<String, Any>> {
        val tagRegex = Regex("<node\\b([^>]*)/?>")
        val attrRegex = Regex("([\\w:-]+)=\"([^\"]*)\"")
        val nodes = mutableListOf<Map<String, Any>>()
        for (tag in tagRegex.findAll(xml)) {
            val attrs = attrRegex.findAll(tag.groupValues[1])
                .associate { it.groupValues[1] to it.groupValues[2] }
            val text = attrs["text"].orEmpty()
            val desc = attrs["content-desc"].orEmpty()
            val clickable = attrs["clickable"] == "true"
            val scrollable = attrs["scrollable"] == "true"
            val editable = attrs["class"]?.contains("EditText") == true
            if (text.isBlank() && desc.isBlank() && !clickable && !scrollable && !editable) continue
            val bounds = attrs["bounds"].orEmpty()
            val center = parseBoundsCenter(bounds)
            nodes += buildMap<String, Any> {
                if (text.isNotBlank()) put("text", text)
                if (desc.isNotBlank()) put("description", desc)
                attrs["class"]?.let { put("class", it.substringAfterLast('.')) }
                if (clickable) put("clickable", true)
                if (scrollable) put("scrollable", true)
                if (editable) put("editable", true)
                attrs["resource-id"]?.takeIf { it.isNotBlank() }?.let { put("resource_id", it) }
                bounds.takeIf { it.isNotBlank() }?.let { put("bounds", bounds) }
                center?.let { (cx, cy) ->
                    put("center_x", cx)
                    put("center_y", cy)
                }
            }
        }
        return nodes
    }

    /** "[x1,y1][x2,y2]" -> center point. */
    private fun parseBoundsCenter(bounds: String): Pair<Int, Int>? {
        val match = Regex("\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]").find(bounds) ?: return null
        val (x1, y1, x2, y2) = match.destructured.toList().map { it.toInt() }
        return (x1 + x2) / 2 to (y1 + y2) / 2
    }

    private fun missing(name: String): Map<String, Any> = mapOf("success" to false, "error" to "Missing $name")

    companion object {
        const val ID = "android_ui"

        val toolInfo = ToolInfo(
            id = ID,
            name = "Android UI",
            description = "Control the phone UI (tap/long-press/swipe/type/keys/dump) via privileges",
            isEnabled = false,
            userToggleable = false,
        )

        private const val DEFAULT_LONG_PRESS_MS = 800
        private const val LAUNCH_VERIFY_MS = 4_000L
        private const val DEFAULT_WINDOW_TIMEOUT_MS = 5_000L
        private const val FOREGROUND_POLL_MS = 200L
        private const val FOREGROUND_POLL_SHELL_MS = 600L
        private const val MAX_INLINE_SCREENSHOT_BYTES = 6_000_000L
    }
}

/** Maps an executor result to a tool reply, optionally with the resulting screen. */
private fun UiActionResult.toToolMap(includeNodes: Boolean): Map<String, Any> = buildMap {
    put("success", success)
    put("backend", backend)
    put("message", message)
    val slice = sliceAfter
    if (slice != null) {
        put("node_count", slice.nodes.size)
        if (slice.truncated) put("truncated", true)
        if (includeNodes && !slice.isEmpty) put("nodes", slice.toPromptText())
    }
}
