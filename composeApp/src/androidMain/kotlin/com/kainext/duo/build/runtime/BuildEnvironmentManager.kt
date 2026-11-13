package com.kainext.duo.build.runtime

import android.content.Context
import android.os.StatFs
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.kainext.duo.TerminalLine
import com.kainext.duo.build.BuildAgents
import com.kainext.duo.build.KaiBuildSessionInfo
import com.kainext.duo.build.KaiBuildStage
import com.kainext.duo.build.KaiBuildState
import com.kainext.duo.linux.DistroSpec
import com.kainext.duo.linux.InstallMarker
import com.kainext.duo.linux.InstallStep
import com.kainext.duo.linux.LinuxDistro
import com.kainext.duo.linux.LinuxInstaller
import com.kainext.duo.linux.LinuxPaths
import com.kainext.duo.linux.ProotLauncher
import com.kainext.duo.sandbox.PersistentSandboxShell
import com.kainext.duo.sandbox.ProotExecutor
import com.kainext.duo.sandbox.SessionShell
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

/** A vendor installer runs `apt` or downloads a binary; the slow ones need room. */
private const val AGENT_INSTALL_TIMEOUT_SECONDS = 1800L

/** An agent CLI is interactive and stays up for as long as the user keeps it. */
private const val AGENT_TIMEOUT_SECONDS = 24L * 60 * 60

/** A user command gets half an hour — long enough for a build, short of a hang forever. */
private const val COMMAND_TIMEOUT_SECONDS = 30L * 60

private const val MAX_PROJECT_NAME = 64

/** How long a project's shells survive after the user steps out of it. */
private val IDLE_WINDOW = 30.minutes

private const val TAG = "KaiBuild"

/**
 * Owns Kai Build's Debian install, the project folders under it, and the shell
 * sessions running inside them.
 *
 * This is the Android half of [com.kainext.duo.KaiBuildController]: the
 * controller is the platform-neutral contract, this is the implementation the
 * app actually runs. It shares [LinuxPaths] with the chat sandbox — a Debian
 * installed from either surface is the same rootfs — but no state: resetting
 * one never touches the other's sessions.
 */
class BuildEnvironmentManager(
    private val paths: LinuxPaths,
    initialState: KaiBuildState = KaiBuildState(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<KaiBuildState> = _state

    /** Set by the controller so the chat sandbox can react to install/uninstall. */
    @Volatile
    var onEnvironmentRemoved: (() -> Unit)? = null

    @Volatile
    var onEnvironmentChanged: (() -> Unit)? = null

    /** What is on disk. Null before an install. */
    @Volatile
    private var marker: InstallMarker? = paths.readMarker()

    private var currentJob: Job? = null

    /** Live shells by session id, plus the metadata the UI lists them by. */
    private val shells = mutableMapOf<String, SessionShell>()
    private val infos = mutableMapOf<String, KaiBuildSessionInfo>()

    /** Sessions with a command in flight: typed lines go to the process, not a new command. */
    private val busy = mutableMapOf<String, Job>()

    /** Per-project idle timers, started on [leaveProject]. */
    private val idleClosers = mutableMapOf<String, Job>()

    private val emptyTranscript: SnapshotStateList<TerminalLine> = mutableStateListOf()

    /** Kept for source compatibility with the controller's secondary constructor. */
    constructor(context: Context, paths: LinuxPaths) : this(paths)

    // ---------------------------------------------------------------- install

    fun install(agentIds: Set<String>) {
        if (currentJob?.isActive == true) return
        currentJob = scope.launch {
            _state.update { it.copy(isInstalling = true, lastError = null, stage = KaiBuildStage.Download(0)) }
            try {
                val installer = LinuxInstaller(paths)
                val installed = installer.install(LinuxDistro.DEBIAN) { step ->
                    _state.update { it.copy(stage = step.toStage()) }
                }
                marker = installed
                _state.update { it.copy(isInstalled = true) }
                onEnvironmentChanged?.invoke()

                if (agentIds.isNotEmpty()) installAgents(agentIds)

                _state.update { it.copy(isInstalling = false, stage = null, lastError = null) }
            } catch (e: CancellationException) {
                _state.update { it.copy(isInstalling = false, stage = null) }
            } catch (e: Exception) {
                Log.e(TAG, "Install failed", e)
                _state.update {
                    it.copy(isInstalling = false, stage = null, lastError = e.message ?: "Install failed")
                }
            } finally {
                marker = paths.readMarker()
                refreshNow()
            }
        }
    }

    /**
     * Runs the vendor installers one after another. A failure on one agent is
     * reported but does not sink the others: the system itself is already usable.
     */
    private suspend fun installAgents(agentIds: Set<String>) {
        val executor = createProotExecutor()
        // Shared with the chat sandbox's package installs — they run `apt` too,
        // and two of them at once corrupts the dpkg lock.
        LinuxInstaller.packageLock.withLock {
            for (agentId in agentIds) {
                val agent = BuildAgents.byId(agentId) ?: continue
                _state.update { it.copy(stage = KaiBuildStage.Agent(agent.id, agent.label)) }
                val result = executor.execute(agent.installCommand, timeoutSeconds = AGENT_INSTALL_TIMEOUT_SECONDS)
                if (result["success"] as? Boolean != true) {
                    val detail = failureDetail(result)
                    Log.e(TAG, "Failed to install ${agent.id}: $detail")
                    _state.update { it.copy(lastError = "${agent.label}: ${detail.take(300)}") }
                }
            }
        }
    }

    fun cancel() {
        currentJob?.cancel()
        currentJob = null
        _state.update { it.copy(isInstalling = false, stage = null) }
    }

    fun uninstall() {
        scope.launch {
            closeAllSessions()
            // Project folders live outside the rootfs on purpose, so they survive
            // this — the same folders reappear when Linux is installed again.
            paths.deleteInstall()
            marker = null
            _state.update {
                KaiBuildState(
                    projects = listProjects(),
                    installedAgents = emptySet(),
                )
            }
            onEnvironmentChanged?.invoke()
        }
    }

    /**
     * Called before the chat sandbox wipes a rootfs it shares with us: the live
     * shells hold file descriptors into the tree that is about to disappear.
     */
    fun onEnvironmentWillBeRemoved() {
        closeAllSessions()
        marker = null
        _state.update { it.copy(isInstalled = false, installedAgents = emptySet(), packagesInstalled = 0) }
    }

    private fun InstallStep.toStage(): KaiBuildStage = when (this) {
        is InstallStep.Download -> KaiBuildStage.Download((fraction * 100).toInt().coerceIn(0, 100))
        is InstallStep.Extract -> KaiBuildStage.Extract
        is InstallStep.Configure -> KaiBuildStage.Configure
        is InstallStep.Packages -> KaiBuildStage.BasePackages
    }

    // ------------------------------------------------------------------ state

    fun refresh() {
        scope.launch { refreshNow() }
    }

    /** Scans disk on the caller's thread — always an IO coroutine here. */
    private suspend fun refreshNow() {
        val current = paths.readMarker()
        marker = current
        val proot = File(paths.prootPath)
        val ready = current != null && proot.exists() && proot.canExecute()
        val projects = listProjects()
        val agents = if (ready) detectAgents() else emptySet()
        val packages = if (ready) countPackages() else 0
        val diskSystem = directorySizeMB(paths.rootfsDir)
        val diskProjects = directorySizeMB(paths.projectsDir)
        val free = freeSpaceMB()
        _state.update {
            it.copy(
                isInstalled = ready,
                projects = projects,
                installedAgents = agents,
                packagesInstalled = packages,
                diskSystemMB = diskSystem,
                diskProjectsMB = diskProjects,
                diskFreeMB = free,
            )
        }
    }

    /** Asks the guest which agent binaries exist. One round trip for all of them. */
    private fun detectAgents(): Set<String> {
        val result = createProotExecutor().execute(BuildAgents.detectCommand, timeoutSeconds = 30)
        val out = result["stdout"] as? String ?: return emptySet()
        val names = out.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        return BuildAgents.all.filter { it.binary in names }.map { it.id }.toSet()
    }

    private fun countPackages(): Int {
        val result = createProotExecutor()
            .execute("dpkg-query -f '.\\n' -W 2>/dev/null | wc -l", timeoutSeconds = 60)
        return ((result["stdout"] as? String) ?: "").trim().toIntOrNull() ?: 0
    }

    private fun freeSpaceMB(): Long = try {
        val stat = StatFs(paths.rootfsDir.takeIf { it.isDirectory }?.path ?: paths.root.path)
        stat.availableBytes / (1024L * 1024L)
    } catch (e: Exception) {
        Log.w(TAG, "Free space unavailable: ${e.message}")
        0L
    }

    /** Walks a tree without following links, skipping sockets and broken entries. */
    private fun directorySizeMB(dir: File): Long {
        if (!dir.isDirectory) return 0
        var total = 0L
        val stack = ArrayDeque<File>()
        stack.addLast(dir)
        while (stack.isNotEmpty()) {
            val children = try {
                stack.removeLast().listFiles()
            } catch (_: Throwable) {
                null
            } ?: continue
            for (child in children) {
                try {
                    when {
                        child.isDirectory -> stack.addLast(child)
                        child.isFile -> total += child.length()
                    }
                } catch (_: Throwable) {
                    // A transient entry: skip it and keep going.
                }
            }
        }
        return total / (1024L * 1024L)
    }

    // --------------------------------------------------------------- projects

    fun createProject(name: String): String? {
        val clean = sanitizeProjectName(name) ?: return null
        val dir = File(paths.projectsDir, clean)
        if (dir.exists()) return null
        if (!dir.mkdirs()) return null
        _state.update { it.copy(projects = listProjects()) }
        return clean
    }

    fun deleteProject(name: String) {
        val dir = File(paths.projectsDir, name)
        if (!isProjectDir(dir)) return
        closeSessionsOf(name)
        dir.deleteRecursively()
        _state.update { it.copy(projects = listProjects(), currentProject = it.currentProject?.takeIf { p -> p != name }) }
    }

    fun renameProject(name: String, newName: String): String? {
        val source = File(paths.projectsDir, name)
        if (!isProjectDir(source)) return null
        val clean = sanitizeProjectName(newName) ?: return null
        val target = File(paths.projectsDir, clean)
        if (target.exists()) return null
        if (!source.renameTo(target)) return null
        // The sessions were started in the folder that just moved; their working
        // directory no longer exists, so they are closed with it.
        closeSessionsOf(name)
        _state.update {
            it.copy(
                projects = listProjects(),
                currentProject = it.currentProject?.takeIf { p -> p != name },
            )
        }
        return clean
    }

    private fun listProjects(): List<String> {
        val dir = paths.projectsDir
        return if (!dir.isDirectory) {
            emptyList()
        } else {
            (dir.listFiles() ?: emptyArray())
                .filter { it.isDirectory && !it.name.startsWith(".") }
                .map { it.name }
                .sortedBy { it.lowercase() }
        }
    }

    /** Guards against a name escaping `projects/` — the sanitizer already does, this is the belt. */
    private fun isProjectDir(dir: File): Boolean {
        val root = paths.projectsDir.absoluteFile
        return try {
            dir.absoluteFile.canonicalPath.startsWith(root.canonicalPath + File.separator) && dir.isDirectory
        } catch (e: Exception) {
            false
        }
    }

    // --------------------------------------------------------------- sessions

    fun startSession(project: String, agentId: String?) {
        val projectDir = File(paths.projectsDir, project)
        if (!projectDir.isDirectory && !projectDir.mkdirs()) return
        cancelIdleClose(project)

        val id = "kb-" + UUID.randomUUID().toString().replace("-", "").take(10)
        val shell = SessionShell(id, PersistentSandboxShell(createProotExecutor(), paths.tmpDir.absolutePath))
        val agent = BuildAgents.byId(agentId)
        val info = KaiBuildSessionInfo(id = id, project = project, agentLabel = agent?.label)
        synchronized(shells) {
            shells[id] = shell
            infos[id] = info
        }
        _state.update {
            it.copy(
                sessions = it.sessions + info,
                currentProject = project,
                currentSession = id,
                isSessionActive = true,
            )
        }

        val guestDir = "/root/projects/$project"
        if (agent != null) {
            // An interactive CLI holds the shell for as long as it runs: typed
            // lines are forwarded to its stdin (see [writeToTerminal]), and the
            // shell is free for commands again once it exits.
            launchLongCommand(id, shell, "cd $guestDir && exec ${agent.binary}")
        } else {
            // Warm the shell so the first command does not pay proot's startup,
            // and leave the working directory at the project.
            runCommand(id, shell, "cd $guestDir && pwd", quiet = true)
        }
    }

    fun selectSession(id: String) {
        val info = synchronized(shells) { infos[id] } ?: return
        cancelIdleClose(info.project)
        _state.update { it.copy(currentProject = info.project, currentSession = id, isSessionActive = true) }
    }

    fun closeSession(id: String) {
        val removed = synchronized(shells) {
            val shell = shells.remove(id)
            infos.remove(id)
            shell
        } ?: return
        busy.remove(id)?.cancel()
        removed.reset()
        _state.update { state ->
            val remaining = state.sessions.filterNot { it.id == id }
            val next = remaining.lastOrNull { it.project == state.currentProject }
            state.copy(
                sessions = remaining,
                currentSession = if (state.currentSession == id) next?.id else state.currentSession,
                isSessionActive = if (state.currentSession == id) next != null else state.isSessionActive,
            )
        }
    }

    fun resumeProject(project: String): Boolean {
        val candidate = _state.value.sessions.lastOrNull { it.project == project } ?: return false
        cancelIdleClose(project)
        _state.update { it.copy(currentProject = project, currentSession = candidate.id, isSessionActive = true) }
        return true
    }

    /**
     * Steps out without killing anything: a build keeps running while the user is
     * elsewhere. If the project is not reopened within [IDLE_WINDOW] its shells are
     * closed, so a forgotten session does not hold a proot alive forever.
     */
    fun leaveProject(project: String) {
        _state.update { if (it.currentProject == project) it.copy(currentProject = null, isSessionActive = false) else it }
        if (!_state.value.sessions.any { it.project == project }) return
        synchronized(idleClosers) {
            idleClosers.remove(project)?.cancel()
            idleClosers[project] = scope.launch {
                delay(IDLE_WINDOW)
                closeSessionsOf(project)
            }
        }
    }

    private fun cancelIdleClose(project: String) {
        synchronized(idleClosers) { idleClosers.remove(project)?.cancel() }
    }

    private fun closeSessionsOf(project: String) {
        val ids = _state.value.sessions.filter { it.project == project }.map { it.id }
        ids.forEach { closeSession(it) }
    }

    private fun closeAllSessions() {
        val ids = synchronized(shells) { shells.keys.toList() }
        ids.forEach { closeSession(it) }
        synchronized(idleClosers) {
            idleClosers.values.forEach { it.cancel() }
            idleClosers.clear()
        }
    }

    fun transcriptFor(sessionId: String): SnapshotStateList<TerminalLine> =
        synchronized(shells) { shells[sessionId] }?.transcript ?: emptyTranscript

    /**
     * A submitted line runs as a command — the shell has no PTY, so there is no
     * character-at-a-time mode to feed. While a process is already attached to
     * the shell (an agent CLI, a build), the line is written to its stdin instead;
     * that is how the user talks to a running agent.
     */
    fun writeToTerminal(text: String) {
        val id = _state.value.currentSession ?: return
        val shell = synchronized(shells) { shells[id] } ?: return
        val line = text.trimEnd('\r', '\n')
        if (line.isBlank()) return
        if (busy[id]?.isActive == true) {
            shell.writeInput(line)
            return
        }
        runCommand(id, shell, line)
    }

    /**
     * The shell has no PTY winsize, so geometry is recorded but not applied: the
     * guest side renders at its default width and the terminal view wraps output
     * itself. Kept so the contract has one place to grow when a PTY is added.
     */
    fun resizeTerminal(columns: Int, rows: Int) = Unit

    private fun runCommand(
        sessionId: String,
        shell: SessionShell,
        command: String,
        quiet: Boolean = false,
    ) {
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val result = shell.run(command, COMMAND_TIMEOUT_SECONDS)
                if (result["timed_out"] as? Boolean == true) {
                    Log.w(TAG, "Command timed out: $command")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Command failed: $command", e)
            } finally {
                busy.remove(sessionId)
            }
        }
        synchronized(busy) { busy[sessionId] = job }
        job.start()
        if (quiet) Unit
    }

    private fun launchLongCommand(sessionId: String, shell: SessionShell, command: String) {
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                shell.run(command, AGENT_TIMEOUT_SECONDS)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Session command failed: $command", e)
            } finally {
                busy.remove(sessionId)
            }
        }
        synchronized(busy) { busy[sessionId] = job }
        job.start()
    }

    // ------------------------------------------------------------------ proot

    /** proot for the installed rootfs, with the binds Kai Build needs. */
    private fun createProotExecutor(): ProotExecutor = ProotExecutor(createLauncher())

    private fun createLauncher(): ProotLauncher {
        val current = marker ?: legacyMarker()
        val spec = DistroSpec.of(current.distro)
        paths.copyLibtalloc()
        paths.ensureLayout()
        // `/root/projects` is where the sessions work, and it lives outside the
        // rootfs so a reinstall keeps the user's code.
        paths.ensureMountPoints()
        val binds = buildList {
            if (!current.homeOnRootfs) {
                add(paths.homeDir(current).absolutePath to "/root")
            }
            add(paths.projectsDir.absolutePath to "/root/projects")
        }
        return ProotLauncher(
            prootPath = paths.prootPath,
            libDir = paths.libDir,
            rootfsPath = paths.rootfsDir.absolutePath,
            tmpPath = paths.tmpDir.absolutePath,
            binds = binds,
            extraArgs = spec.prootArgs,
            env = spec.env,
        )
    }

    /**
     * Before an install the marker is absent and proot is not there yet; a legacy
     * marker keeps the bind layout of an existing rootfs rather than inventing one.
     */
    private fun legacyMarker() = InstallMarker(LinuxDistro.DEBIAN, homeOnRootfs = true)

    private fun failureDetail(result: Map<String, Any>): String =
        listOf("stderr", "error", "stdout")
            .firstNotNullOfOrNull { (result[it] as? String)?.takeIf(String::isNotBlank) }
            .orEmpty()
}

/**
 * Folder names double as shell-safe ids: lowercase, no spaces, nothing that
 * needs quoting. Returns null when nothing usable is left.
 */
internal fun sanitizeProjectName(raw: String): String? {
    val clean = raw.trim().lowercase()
        .replace(Regex("[^a-z0-9._-]+"), "-")
        .trim('-', '.', '_')
        .take(MAX_PROJECT_NAME)
        .trim('-', '.', '_')
    if (clean.isEmpty() || clean == "." || clean == "..") return null
    return clean
}
