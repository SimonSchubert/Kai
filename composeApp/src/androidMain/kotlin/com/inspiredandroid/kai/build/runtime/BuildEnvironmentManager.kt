package com.inspiredandroid.kai.build.runtime

import android.content.Context
import android.os.StatFs
import android.util.Log
import com.inspiredandroid.kai.build.BuildAgent
import com.inspiredandroid.kai.build.BuildAgents
import com.inspiredandroid.kai.build.BuildEnvironmentState
import com.inspiredandroid.kai.build.BuildStep
import com.inspiredandroid.kai.build.BuildSystemInfo
import com.inspiredandroid.kai.build.BuildTerminalSession
import com.inspiredandroid.kai.build.KaiBuildState
import com.inspiredandroid.kai.build.terminal.DEFAULT_COLUMNS
import com.inspiredandroid.kai.build.terminal.DEFAULT_ROWS
import com.inspiredandroid.kai.build.terminal.MAX_COLUMNS
import com.inspiredandroid.kai.build.terminal.MAX_ROWS
import com.inspiredandroid.kai.build.terminal.MIN_COLUMNS
import com.inspiredandroid.kai.build.terminal.MIN_ROWS
import com.inspiredandroid.kai.build.terminal.TerminalScreen
import com.inspiredandroid.kai.build.terminal.TerminalSnapshot
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "KaiBuild"

/** Installed with Debian itself so every project starts with the essentials. */
// tar: OpenCode's vendor installer extracts a .tar.gz; coreutils ships sha256sum for Claude.
private const val BASE_PACKAGES =
    "bash ca-certificates curl wget git nano less unzip python3 tar coreutils"

/** Device-code pattern shown on Grok's login TUI (e.g. PNX4-ZGCX). */
private val DEVICE_CODE_REGEX = Regex("""\b([A-Z0-9]{4}-[A-Z0-9]{4})\b""")

private val INVALID_NAME_CHARS = Regex("[^A-Za-z0-9._-]+")

/** Floor on the gap between two terminal repaints — roughly one display frame. */
private const val REPAINT_INTERVAL_MS = 16L

/** Captured login / OSC 8 links leave the bar after this (matches the UI hide). */
private const val HYPERLINK_TTL_MS = 2 * 60 * 1000L

/**
 * One live PTY session: its own VT screen, geometry, and capture files. Several
 * can run at once, which is what the terminal's session tabs switch between.
 */
private class BuildSession(
    val id: String,
    val project: String,
    val agentId: String?,
    val number: Int,
    columns: Int,
    rows: Int,
) {
    private val screenLock = Any()
    private val screen = TerminalScreen(columns, rows)

    val columns = AtomicInteger(columns)
    val rows = AtomicInteger(rows)

    /** Host-side file names inside the bind-mounted tmp dir — one set per session. */
    val winsizeFile: String get() = "kai-winsize-$id"
    val openUrlFile: String get() = "kai-open-url-$id"
    val pidFile: String get() = "kai-pid-$id"

    @Volatile
    var handle: BuildProotHandle? = null

    @Volatile
    var job: Job? = null

    @Volatile
    var busy: Boolean = true

    @Volatile
    var lastDeviceCode: String? = null

    /** Cancels/reschedules when a new URL is captured so the bar stays for a full TTL. */
    var hyperlinkClearJob: Job? = null

    /** Last published screen — recomputed only for the session that produced output. */
    @Volatile
    var snapshot: TerminalSnapshot = TerminalSnapshot.blank()
        private set

    fun <T> withScreen(block: (TerminalScreen) -> T): T = synchronized(screenLock) { block(screen) }

    /**
     * Caches a fresh snapshot and hands back the visible text (for device-code
     * scans). URLs are scraped from a space-trimmed join of rows so a line-wrapped
     * OAuth URL reassembles into one link instead of one stump per row.
     * Snapshotting allocates a cell object per grid position, so the second
     * snapshot only happens when a new link actually changed the screen.
     */
    fun refreshSnapshot(): String = synchronized(screenLock) {
        val snap = screen.snapshot()
        val text = visibleText(snap)
        val urlText = visibleTextForUrlScan(snap)
        snapshot = if (screen.noteTextUrls(urlText)) screen.snapshot() else snap
        text
    }

    fun resize(cols: Int, rws: Int) {
        columns.set(cols)
        rows.set(rws)
        synchronized(screenLock) { screen.resize(cols, rws) }
    }

    fun stop() {
        handle?.cancel()
        handle = null
        job?.cancel()
        job = null
        hyperlinkClearJob?.cancel()
        hyperlinkClearJob = null
        busy = false
    }

    fun toUiSession() = BuildTerminalSession(
        id = id,
        project = project,
        agentId = agentId,
        number = number,
        terminal = snapshot,
        busy = busy,
    )
}

private fun visibleText(snap: TerminalSnapshot): String =
    buildString(snap.columns * snap.rows + snap.rows) {
        for (row in 0 until snap.rows) {
            for (col in 0 until snap.columns) {
                append(snap.cellAt(col, row).char)
            }
            append('\n')
        }
    }

/**
 * Like [visibleText] but trims trailing spaces on each row and joins without
 * newlines. Terminal wrap pads the rest of the line with spaces; keeping those
 * (or the newline) splits one long OAuth URL into many partial matches.
 */
private fun visibleTextForUrlScan(snap: TerminalSnapshot): String =
    buildString(snap.columns * snap.rows) {
        for (row in 0 until snap.rows) {
            val start = length
            for (col in 0 until snap.columns) {
                append(snap.cellAt(col, row).char)
            }
            // Trim trailing spaces from this row only.
            var end = length
            while (end > start && this[end - 1] == ' ') end--
            if (end < length) deleteRange(end, length)
        }
    }

/**
 * Owns the Debian rootfs, agent installs, and the project terminal sessions for
 * Kai Build. Process-scoped: held by the Koin-managed AndroidKaiBuildController.
 */
class BuildEnvironmentManager(context: Context) {

    private val paths = BuildPaths(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var installJob: Job? = null

    private val sessionsLock = Any()
    private val sessions = LinkedHashMap<String, BuildSession>()
    private var activeSessionId: String? = null
    private val sessionCounter = AtomicInteger()

    /** Geometry of the last measured viewport — new sessions start there, not at 80×24. */
    private val lastColumns = AtomicInteger(DEFAULT_COLUMNS)
    private val lastRows = AtomicInteger(DEFAULT_ROWS)

    private val _state = MutableStateFlow(KaiBuildState())
    val state: StateFlow<KaiBuildState> = _state.asStateFlow()

    init {
        paths.ensureLayout()
        scope.launch { sync() }
    }

    fun install(agentIds: Set<String>) {
        if (installJob?.isActive == true) return
        installJob = scope.launch {
            try {
                installInternal(agentIds) { isActive }
            } catch (e: CancellationException) {
                cleanUpPartialInstall(error = null)
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Install failed", e)
                cleanUpPartialInstall(error = e.message ?: "Install failed")
            }
        }
    }

    fun cancel() {
        installJob?.cancel()
        installJob = null
    }

    fun uninstall() {
        if (installJob?.isActive == true) return
        installJob = scope.launch {
            closeAllSessions()
            paths.readyMarker.delete()
            paths.rootfsDir.deleteRecursively()
            paths.tmpDir.deleteRecursively()
            paths.archiveFile.delete()
            paths.ensureLayout()
            _state.value = KaiBuildState(projects = scanProjects())
        }
    }

    fun refresh() {
        scope.launch { sync() }
    }

    fun createProject(name: String): String? {
        val folder = name.trim().replace(INVALID_NAME_CHARS, "-").trim('-', '.').take(64)
        if (folder.isEmpty()) return null
        File(paths.projectsDir, folder).mkdirs()
        scope.launch { _state.update { it.copy(projects = scanProjects()) } }
        return folder
    }

    // --- sessions --------------------------------------------------------

    /**
     * Opens another PTY session in [project] and makes it active. A non-null
     * [agentId] runs that agent first and drops back to a shell when it exits.
     */
    fun startSession(project: String, agentId: String?) {
        if (!_state.value.isReady) return
        val id = "s${sessionCounter.incrementAndGet()}"
        val session = synchronized(sessionsLock) {
            val number = (sessions.values.filter { it.project == project }.maxOfOrNull { it.number } ?: 0) + 1
            BuildSession(
                id = id,
                project = project,
                agentId = agentId?.takeIf { BuildAgents.get(it) != null },
                number = number,
                columns = lastColumns.get(),
                rows = lastRows.get(),
            ).also {
                sessions[id] = it
                activeSessionId = id
            }
        }
        publishSessions()
        session.job = scope.launch { runShell(session) }
    }

    fun selectSession(id: String) {
        synchronized(sessionsLock) {
            if (!sessions.containsKey(id)) return
            activeSessionId = id
        }
        publishSessions()
    }

    fun closeSession(id: String) {
        val session = synchronized(sessionsLock) {
            val removed = sessions.remove(id) ?: return
            if (activeSessionId == id) {
                activeSessionId = sessions.values.lastOrNull { it.project == removed.project }?.id
                    ?: sessions.values.lastOrNull()?.id
            }
            removed
        }
        endSession(session)
        publishSessions()
    }

    fun closeProjectSessions(project: String) {
        val closing = synchronized(sessionsLock) {
            val matches = sessions.values.filter { it.project == project }
            matches.forEach { sessions.remove(it.id) }
            if (sessions[activeSessionId] == null) activeSessionId = sessions.values.lastOrNull()?.id
            matches
        }
        closing.forEach(::endSession)
        publishSessions()
    }

    /**
     * Writes text to the active session's shell/agent (UTF-8). Callers that want
     * a submitted line should include the trailing carriage return themselves.
     */
    fun writeToTerminal(text: String) {
        activeSession()?.handle?.writeText(text)
    }

    /**
     * Resize the active session's VT buffer and notify its live PTY
     * (desktop-style). Safe to call from the UI on every layout pass — no-ops
     * when geometry is unchanged.
     */
    fun resizeTerminal(columns: Int, rows: Int) {
        val c = columns.coerceIn(MIN_COLUMNS, MAX_COLUMNS)
        val r = rows.coerceIn(MIN_ROWS, MAX_ROWS)
        lastColumns.set(c)
        lastRows.set(r)
        val session = activeSession() ?: return
        // Cheap enough to answer on the caller's thread, and the common case: the
        // UI calls this on every layout pass, including each keyboard-animation frame.
        if (c == session.columns.get() && r == session.rows.get()) return
        scope.launch {
            // Read the geometry back rather than using the captured one: two resizes
            // in flight at once would otherwise race, and the stale one could land
            // last. Re-reading makes every pending pass apply the newest viewport.
            val currentC = lastColumns.get()
            val currentR = lastRows.get()
            session.resize(currentC, currentR)
            publishScreen(session)
            // Bridge process polls this file and applies TIOCSWINSZ + SIGWINCH.
            runCatching {
                paths.tmpDir.mkdirs()
                File(paths.tmpDir, session.winsizeFile).writeText("$currentR $currentC\n")
            }
        }
    }

    fun clearTerminal() {
        val session = activeSession() ?: return
        scope.launch {
            session.withScreen { it.clear() }
            publishScreen(session)
        }
    }

    private fun activeSession(): BuildSession? = synchronized(sessionsLock) { sessions[activeSessionId] }

    private fun closeAllSessions() {
        val closing = synchronized(sessionsLock) {
            val all = sessions.values.toList()
            sessions.clear()
            activeSessionId = null
            all
        }
        closing.forEach(::endSession)
        publishSessions()
    }

    /** Kills the process and drops the per-session capture files. */
    private fun endSession(session: BuildSession) {
        session.stop()
        runCatching {
            File(paths.tmpDir, session.winsizeFile).delete()
            File(paths.tmpDir, session.openUrlFile).delete()
            File(paths.tmpDir, session.pidFile).delete()
        }
    }

    private fun publishSessions() {
        val list: List<BuildTerminalSession>
        val active: String?
        synchronized(sessionsLock) {
            list = sessions.values.map { it.toUiSession() }
            active = activeSessionId
        }
        _state.update { it.copy(sessions = list.toImmutableList(), activeSessionId = active) }
    }

    // --- install ---------------------------------------------------------

    private fun installInternal(agentIds: Set<String>, isActive: () -> Boolean) {
        paths.ensureLayout()
        check(File(paths.prootPath).exists()) { "proot not found at ${paths.prootPath}" }
        copyLibtalloc()

        if (!paths.readyMarker.exists()) {
            installDebian(isActive)
            paths.readyMarker.writeText("debian")
        }

        val failed = mutableListOf<String>()
        for (agent in agentIds.mapNotNull { BuildAgents.get(it) }) {
            if (!isActive()) throw CancellationException()
            if (!installAgent(agent)) failed += agent.title
        }
        sync(error = if (failed.isEmpty()) null else "Could not install ${failed.joinToString()}")
    }

    private fun installDebian(isActive: () -> Boolean) {
        setStep(BuildStep.Download, progress = 0f)
        DebianRootfsInstaller.download(paths.archiveFile, isActive) { setStep(BuildStep.Download, progress = it) }

        if (!isActive()) throw CancellationException()
        setStep(BuildStep.Extract)
        try {
            DebianRootfsInstaller.extract(paths.archiveFile, paths.rootfsDir)
        } finally {
            paths.archiveFile.delete()
        }

        if (!isActive()) throw CancellationException()
        setStep(BuildStep.Configure)
        listOf(
            "var/lib/apt/lists/partial",
            "var/cache/apt/archives/partial",
            "var/lib/dpkg/updates",
            "var/lib/dpkg/info",
            "var/lib/dpkg/alternatives",
            "var/log",
            "run/lock",
            "tmp",
        ).forEach { File(paths.rootfsDir, it).mkdirs() }
        File(paths.rootfsDir, "etc/dpkg/dpkg.cfg.d").mkdirs()
        File(paths.rootfsDir, "etc/dpkg/dpkg.cfg.d/force-unsafe-io").writeText("force-unsafe-io\n")

        setStep(BuildStep.BasePackages)
        val executor = executor()
        executor.require("apt-get update -y", timeoutSeconds = 300)
        executor.require(
            "apt-get install -y --no-install-recommends $BASE_PACKAGES",
            timeoutSeconds = 900,
        )
        executor.require("mkdir -p /root/.local/bin /root/projects", timeoutSeconds = 30)
        ensureAgentPathProfile()
    }

    private fun installAgent(agent: BuildAgent): Boolean {
        setStep(BuildStep.Agent, agentId = agent.id)
        ensureAgentPathProfile()
        val executor = executor()
        executor.execute(
            "mkdir -p /root/.local/bin /root/.grok/bin /root/.grok/downloads " +
                "/root/.opencode/bin /root/.claude/downloads /usr/local/bin",
            timeoutSeconds = 30,
        )
        val result = executor.execute(agent.installCommand, timeoutSeconds = 900)
        // Vendor scripts may leave the binary only in their private dir and only
        // update shell rc files — which Kai Build never sources. Link into PATH
        // and re-probe so a successful download still counts as installed.
        val installed = executor.ensureAgentBinary(agent.binary)
        if (!installed) {
            Log.w(TAG, "${agent.id} install failed (exit detail): ${result.failureDetail()}")
            val probe = executor.execute(
                "command -v ${agent.binary} 2>/dev/null; " +
                    "ls -la /root/.local/bin /root/.grok/bin /root/.opencode/bin /usr/local/bin 2>/dev/null; " +
                    "ls -la /root/.local/share/claude/versions 2>/dev/null | tail -5; " +
                    "ls -la /root/.grok/downloads 2>/dev/null | tail -5; " +
                    "ls -la /root/.opencode/bin 2>/dev/null",
                timeoutSeconds = 30,
            )
            Log.w(TAG, "${agent.id} post-install probe: ${probe.stdout} ${probe.stderr}")
        }
        return installed
    }

    private fun cleanUpPartialInstall(error: String?) {
        paths.archiveFile.delete()
        if (!paths.readyMarker.exists()) paths.rootfsDir.deleteRecursively()
        sync(error)
    }

    // --- terminal --------------------------------------------------------

    /** Snapshot the screen, scan it for a device code, and push it to the UI. */
    private fun publishScreen(session: BuildSession) {
        val linksBefore = session.snapshot.hyperlinks
        val text = session.refreshSnapshot()
        noteDeviceCodeFromText(session, text)
        val linksAfter = session.snapshot.hyperlinks
        // New URLs (OSC 8 / stream / device code) restart the auto-hide TTL.
        if (linksAfter.isNotEmpty() && linksAfter != linksBefore) {
            scheduleHyperlinkClear(session)
        }
        publishSessions()
    }

    private fun runShell(session: BuildSession) {
        session.refreshSnapshot()
        publishSessions()
        var urlPollJob: Job? = null
        // Repaint is coalesced to at most one per frame. A repaint snapshots the
        // whole grid and re-lays out the text, and the PTY hands us a chunk every
        // few hundred microseconds under heavy output — repainting per chunk is
        // what made the app unusable while a command was running. CONFLATED means
        // a burst collapses into a single repaint without ever dropping the last
        // state: whatever arrived during the wait is still pending afterwards.
        val repaint = Channel<Unit>(Channel.CONFLATED)
        val repaintJob = scope.launch(Dispatchers.Default) {
            while (true) {
                if (repaint.receiveCatching().isClosed) break
                publishScreen(session)
                delay(REPAINT_INTERVAL_MS)
            }
            // The channel closes once the shell is done writing, so this last pass
            // renders the tail — including the exit line — after every writer.
            publishScreen(session)
        }
        try {
            ensureBrowserCaptureHelpers()
            ensureAgentPathProfile()
            // Interactive login shell rooted at the project folder.
            // stty reinforces rows/cols for apps that only consult the tty line discipline.
            val cols = session.columns.get()
            val rows = session.rows.get()
            // Seed winsize file so the bridge and any early readers see current geometry.
            runCatching {
                paths.tmpDir.mkdirs()
                File(paths.tmpDir, session.winsizeFile).writeText("$rows $cols\n")
                File(paths.tmpDir, session.openUrlFile).writeText("")
            }
            // An agent session runs the CLI first and leaves a usable shell behind.
            // Resolve an absolute path under the non-login probe PATH: login shells
            // (`bash -lc`) reset PATH via /etc/profile, and vendor installers often
            // skip writing rc PATH lines when our proot env already included their
            // bin dir — leaving only agents that force-wrote .bashrc (e.g. Grok).
            val agentBinary = BuildAgents.get(session.agentId)?.binary
            val agentCmd = agentBinary?.let { resolveAgentCommand(it) }
            val launch = if (agentCmd != null) "$agentCmd; exec bash -l" else "exec bash -l"
            val handle = executor(columns = cols, rows = rows, session = session).executeStreaming(
                command = "stty rows $rows cols $cols 2>/dev/null; $launch",
                workingDir = "/root/projects/${session.project}",
                // Runs on the PTY reader thread: keep it to parsing, and let the
                // repaint pump do the snapshotting and publishing on its own clock.
                onOutput = { buf, n ->
                    // Always scan raw stream for URLs (OSC 8/52 may be incomplete across chunks).
                    val text = buf.decodeToString(0, n, throwOnInvalidSequence = false)
                    session.withScreen { screen ->
                        screen.noteTextUrls(text)
                        screen.writeText(text)
                    }
                    repaint.trySend(Unit)
                },
            )
            session.handle = handle
            // Poll Grok's URL capture file (and our BROWSER wrapper) while the shell runs.
            urlPollJob = scope.launch { pollOpenUrlFile(session) }
            val exit = handle.awaitExit()
            if (exit > 0) {
                session.withScreen { it.writeText("\r\n[shell exited $exit]\r\n") }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Shell failed", e)
            session.withScreen { it.writeText("\r\n${e.message ?: "Shell failed"}\r\n") }
        } finally {
            urlPollJob?.cancel()
            // One last read in case the URL landed as the process exited.
            ingestOpenUrlFile(session)
            session.handle = null
            session.busy = false
            // Closing lets the pump run its final pass, which renders whatever the
            // blocks above wrote. Nothing writes to the screen after this point.
            repaint.close()
            if (!repaintJob.isActive) publishScreen(session)
        }
    }

    /**
     * Login shells (`bash -l` / `bash -lc`) reset PATH from `/etc/profile`, which
     * drops the proot-injected vendor dirs. Vendor installers often skip writing
     * PATH into shell rc when their bin dir is already on PATH during install —
     * so Claude (`~/.local/bin`) and OpenCode (`~/.opencode/bin`) vanish while
     * Grok (always rewrites `.bashrc`) still works.
     *
     * A tiny `/etc/profile.d` snippet puts every agent bin dir back for login
     * shells without depending on vendor rc edits. Written on the host so it
     * does not need a proot round-trip.
     */
    private fun ensureAgentPathProfile() {
        val dir = File(paths.rootfsDir, "etc/profile.d")
        dir.mkdirs()
        File(dir, "kai-build-path.sh").writeText(
            """
            |# Managed by Kai Build — keep coding-agent CLIs on PATH for login shells.
            |export PATH="/root/.local/bin:/root/.grok/bin:/root/.opencode/bin${'$'}{PATH:+:${'$'}PATH}"
            |
            """.trimMargin(),
        )
    }

    /**
     * Absolute path (quoted) for [binary], or the bare name if resolution fails.
     * Ensures the binary is linked into a known dir before probing.
     */
    private fun resolveAgentCommand(binary: String): String {
        // Safe: [binary] is a fixed catalog name (claude/grok/opencode).
        val probe = executor()
        probe.ensureAgentBinary(binary)
        val path = probe.execute(
            "command -v $binary 2>/dev/null",
            timeoutSeconds = 20,
        ).stdout.trim().lineSequence().firstOrNull().orEmpty()
        return if (path.startsWith("/")) "'$path'" else binary
    }

    /**
     * Installs `/usr/local/bin/kai-browser` into the rootfs so `$BROWSER` captures
     * URLs Grok tries to open when no real display/browser exists under proot.
     * The target file is per session, passed in as `KAI_OPEN_URL_FILE`.
     */
    private fun ensureBrowserCaptureHelpers() {
        val script = File(paths.rootfsDir, "usr/local/bin/kai-browser")
        script.parentFile?.mkdirs()
        script.writeText(
            """
            |#!/bin/sh
            |# Kai Build: capture browser-open URLs for the in-app link bar.
            |url="${'$'}1"
            |target="${'$'}{KAI_OPEN_URL_FILE:-/tmp/kai-open-url}"
            |if [ -n "${'$'}url" ]; then
            |  printf '%s\n' "${'$'}url" >> "${'$'}target"
            |fi
            |exit 0
            """.trimMargin(),
        )
        script.setExecutable(true, false)
    }

    private suspend fun pollOpenUrlFile(session: BuildSession) {
        while (true) {
            ingestOpenUrlFile(session)
            kotlinx.coroutines.delay(250)
        }
    }

    private fun ingestOpenUrlFile(session: BuildSession) {
        val file = File(paths.tmpDir, session.openUrlFile)
        if (!file.exists() || file.length() == 0L) return
        val lines = runCatching { file.readLines() }.getOrDefault(emptyList())
        if (lines.isEmpty()) return
        // Consume the file so the same URLs are not re-applied after a TTL clear.
        runCatching { file.writeText("") }
        var added = false
        session.withScreen { screen ->
            for (line in lines) {
                val url = line.trim()
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    if (screen.noteHyperlink(url)) {
                        added = true
                        Log.i(TAG, "captured open-url: $url")
                    }
                }
            }
        }
        if (added) {
            session.refreshSnapshot()
            publishSessions()
            scheduleHyperlinkClear(session)
        }
    }

    /**
     * When Grok shows a device code but never emits the auth URL on the PTY
     * (confirmed via term-debug.log: zero https/OSC8), surface the known sign-in
     * page plus the code so the user can complete login on the phone browser.
     */
    private fun noteDeviceCodeFromText(session: BuildSession, text: String) {
        // Only treat as auth when the login copy is on screen. This runs on every
        // repaint, so the keyword gate comes first: `contains(ignoreCase)` scans in
        // place, where the regex and a lowercased copy of the whole screen allocate.
        if (!text.contains("approve", ignoreCase = true) &&
            !text.contains("signing in", ignoreCase = true) &&
            !text.contains("browser", ignoreCase = true)
        ) {
            return
        }
        val code = DEVICE_CODE_REGEX.find(text)?.groupValues?.getOrNull(1) ?: return
        if (code == session.lastDeviceCode) return
        session.lastDeviceCode = code
        session.withScreen { screen ->
            screen.noteHyperlink("https://accounts.x.ai/sign-in")
            // Bookmark-style deep link so the code is visible next to the URL in the bar.
            screen.noteHyperlink("https://accounts.x.ai/sign-in#code=$code")
        }
        session.refreshSnapshot()
        // TTL is scheduled by publishScreen when it sees the new links.
        Log.i(TAG, "device code detected: $code — surface https://accounts.x.ai/sign-in")
    }

    /**
     * Drop the link bar a couple of minutes after the last new URL. Login flows
     * only need the links while the user completes them; leaving them forever
     * looks like they still need to sign in.
     */
    private fun scheduleHyperlinkClear(session: BuildSession) {
        session.hyperlinkClearJob?.cancel()
        session.hyperlinkClearJob = scope.launch {
            delay(HYPERLINK_TTL_MS)
            session.withScreen { it.clearHyperlinks() }
            session.refreshSnapshot()
            publishSessions()
        }
    }

    // --- state -----------------------------------------------------------

    private fun sync(error: String? = null) {
        val ready = paths.readyMarker.exists() && File(paths.prootPath).canExecute()
        val agents = if (ready) detectAgents() else persistentSetOf()
        val projects = scanProjects()
        val info = if (ready) readSystemInfo() else null
        _state.update {
            it.copy(
                environment = if (ready) BuildEnvironmentState.Ready else BuildEnvironmentState.NotInstalled,
                installedAgents = agents,
                projects = projects,
                systemInfo = info,
                lastError = error,
            )
        }
    }

    /** Read straight off the rootfs — no proot round-trip, so it is cheap enough for every refresh. */
    private fun readSystemInfo(): BuildSystemInfo {
        val pretty = runCatching {
            File(paths.rootfsDir, "etc/os-release").readLines()
                .firstOrNull { it.startsWith("PRETTY_NAME=") }
                ?.substringAfter('=')
                ?.trim('"')
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
        val version = runCatching {
            File(paths.rootfsDir, "etc/debian_version").readText().trim().takeIf { it.isNotBlank() }
        }.getOrNull()
        val packages = runCatching {
            File(paths.rootfsDir, "var/lib/dpkg/status").useLines { lines ->
                lines.count { it.startsWith("Package: ") }
            }
        }.getOrDefault(0)
        val free = runCatching { StatFs(paths.buildDir.absolutePath).availableBytes }.getOrDefault(0L)
        return BuildSystemInfo(
            distribution = pretty ?: version?.let { "Debian $it" } ?: "Debian",
            architecture = DebianRootfsInstaller.linuxArch(),
            packageCount = packages,
            systemBytes = directorySize(paths.rootfsDir) + directorySize(paths.tmpDir),
            projectsBytes = directorySize(paths.projectsDir),
            freeBytes = free,
        )
    }

    private fun setStep(step: BuildStep, progress: Float? = null, agentId: String? = null) {
        _state.update {
            it.copy(environment = BuildEnvironmentState.Installing(step, progress, agentId), lastError = null)
        }
    }

    private fun detectAgents(): ImmutableSet<String> {
        ensureAgentPathProfile()
        val executor = executor()
        return BuildAgents.all
            .filter { executor.ensureAgentBinary(it.binary) }
            .map { it.id }
            .toImmutableSet()
    }

    private fun scanProjects() = paths.projectsDir.listFiles()
        .orEmpty()
        .filter { it.isDirectory }
        .map { it.name }
        .sorted()
        .toImmutableList()

    private fun executor(
        columns: Int = lastColumns.get(),
        rows: Int = lastRows.get(),
        session: BuildSession? = null,
    ): BuildProotExecutor {
        copyLibtalloc()
        paths.rootfsProjectsMount.mkdirs()
        File(paths.rootfsDir, "root/.local/bin").mkdirs()
        return BuildProotExecutor(
            prootPath = paths.prootPath,
            libDir = paths.buildDir.absolutePath,
            rootfsPath = paths.rootfsDir.absolutePath,
            projectsPath = paths.projectsDir.absolutePath,
            tmpPath = paths.tmpDir.absolutePath,
            columns = columns,
            rows = rows,
            winsizePath = "/tmp/${session?.winsizeFile ?: "kai-winsize"}",
            openUrlPath = "/tmp/${session?.openUrlFile ?: "kai-open-url"}",
            pidFileName = session?.pidFile ?: "kai-pid",
        )
    }

    private fun copyLibtalloc() {
        val target = paths.tallocTarget
        if (target.exists()) return
        val source = File(paths.nativeLibDir, "libtalloc.so")
        if (source.exists()) source.copyTo(target, overwrite = true)
    }
}

private fun BuildProotExecutor.hasBinary(binary: String): Boolean =
    execute("command -v $binary >/dev/null 2>&1", timeoutSeconds = 20).success

/**
 * True when [binary] is reachable. Always tries to place a stable symlink in
 * `/root/.local/bin` (even when already on the probe PATH) so login shells that
 * only keep a subset of vendor dirs still find every agent by name.
 *
 * Known layouts:
 * - Claude: `~/.local/bin/claude` → `~/.local/share/claude/versions/<ver>`
 * - Grok: `~/.grok/bin/grok`
 * - OpenCode: `~/.opencode/bin/opencode`
 */
private fun BuildProotExecutor.ensureAgentBinary(binary: String): Boolean {
    // Safe: [binary] is a fixed catalog name (claude/grok/opencode), not user input.
    execute(
        """
        set -e
        bin='$binary'
        dest="/root/.local/bin/${'$'}bin"
        mkdir -p /root/.local/bin
        # Prefer a real file under .local/bin; otherwise link from vendor dirs.
        if [ -x "${'$'}dest" ] && [ ! -d "${'$'}dest" ]; then
            exit 0
        fi
        for candidate in \
            "/root/.grok/bin/${'$'}bin" \
            "/root/.opencode/bin/${'$'}bin" \
            "/usr/local/bin/${'$'}bin"
        do
            if [ -x "${'$'}candidate" ] && [ ! -d "${'$'}candidate" ]; then
                ln -sfn "${'$'}candidate" "${'$'}dest"
                exit 0
            fi
        done
        # Claude install can leave only the versioned binary under share/.
        if [ "${'$'}bin" = "claude" ]; then
            ver_dir="/root/.local/share/claude/versions"
            if [ -d "${'$'}ver_dir" ]; then
                latest="${'$'}(ls -1 "${'$'}ver_dir" 2>/dev/null | sort -V | tail -1)"
                if [ -n "${'$'}latest" ] && [ -x "${'$'}ver_dir/${'$'}latest" ]; then
                    ln -sfn "${'$'}ver_dir/${'$'}latest" "${'$'}dest"
                    exit 0
                fi
            fi
        fi
        exit 0
        """.trimIndent(),
        timeoutSeconds = 30,
    )
    return hasBinary(binary)
}

private fun BuildProotExecutor.require(command: String, timeoutSeconds: Long) {
    val result = execute(command, timeoutSeconds)
    check(result.success) { "`$command` failed: ${result.failureDetail()}" }
}

/**
 * Total size of the regular files under [dir], without following symlinks — an
 * LXC rootfs contains link loops that would trap a naive recursive walk.
 */
private fun directorySize(dir: File): Long {
    if (!dir.exists()) return 0L
    var total = 0L
    runCatching {
        Files.walkFileTree(
            dir.toPath(),
            object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (attrs.isRegularFile) total += attrs.size()
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult =
                    FileVisitResult.CONTINUE
            },
        )
    }
    return total
}
