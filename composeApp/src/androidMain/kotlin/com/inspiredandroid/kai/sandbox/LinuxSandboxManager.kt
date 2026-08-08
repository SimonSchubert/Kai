package com.inspiredandroid.kai.sandbox

import android.content.Context
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.inspiredandroid.kai.SandboxSessions
import com.inspiredandroid.kai.TerminalLine
import com.inspiredandroid.kai.data.AppSettings
import com.inspiredandroid.kai.data.ConversationStorage
import com.inspiredandroid.kai.linux.DistroSpec
import com.inspiredandroid.kai.linux.GuestFileMap
import com.inspiredandroid.kai.linux.InstallMarker
import com.inspiredandroid.kai.linux.InstallStep
import com.inspiredandroid.kai.linux.LinuxDistro
import com.inspiredandroid.kai.linux.LinuxInstaller
import com.inspiredandroid.kai.linux.LinuxPaths
import com.inspiredandroid.kai.linux.ProotLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

private val TRANSCRIPT_SAVE_DEBOUNCE = 500.milliseconds

private const val PACKAGE_TIMEOUT_SECONDS = 900L

class LinuxSandboxManager(
    private val context: Context,
    private val conversationStorage: ConversationStorage,
    private val appSettings: AppSettings,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentJob: Job? = null
    private val _state = MutableStateFlow<SandboxState>(SandboxState.NotInstalled)
    val state: StateFlow<SandboxState> = _state

    val paths = LinuxPaths.forSandbox(context)
    private val installer = LinuxInstaller(paths)

    /**
     * What is on disk. Null until something is installed, at which point every
     * distro-dependent decision — package commands, proot flags, where `/root`
     * lives — follows it rather than the user's current setting.
     */
    @Volatile
    private var marker: InstallMarker? = null

    /** The distro an install would become, or the one already installed. */
    val distro: LinuxDistro get() = marker?.distro ?: appSettings.getSandboxDistro()

    val rootfsPath: String get() = paths.rootfsDir.absolutePath

    /**
     * Host directory behind `/root`. New installs keep it on the rootfs so
     * binaries under `~/.local/bin` are executable; installs made before the two
     * Linux stacks merged keep their external-storage bind.
     */
    val homePath: String get() = paths.homeDir(marker ?: legacyMarker()).absolutePath

    /**
     * False only for installs made before the two Linux stacks merged. Those
     * still bind `/root` from external storage, so it is not part of the rootfs
     * listing and the file browser has to add it.
     */
    val homeOnRootfs: Boolean get() = (marker ?: legacyMarker()).homeOnRootfs

    val tmpPath: String get() = paths.tmpDir.absolutePath

    val prootPath: String get() = paths.prootPath

    /** Called on reset so Kai Build can drop sessions holding a deleted rootfs. */
    @Volatile
    var onBeforeReset: (() -> Unit)? = null

    private fun legacyMarker() = InstallMarker(LinuxDistro.LEGACY, homeOnRootfs = false)

    init {
        checkExistingInstallation()
    }

    /**
     * Re-reads what is on disk. Kai Build calls this after it installs or removes
     * a shared Debian — that is the same rootfs this manager reports on, and
     * nothing else would tell it the answer changed.
     */
    fun refreshInstallState() {
        if (currentJob?.isActive == true) return
        checkExistingInstallation()
    }

    private fun checkExistingInstallation() {
        marker = paths.readMarker()
        val proot = File(prootPath)
        _state.value = if (marker != null && proot.exists() && proot.canExecute()) {
            SandboxState.Ready
        } else {
            SandboxState.NotInstalled
        }
    }

    fun setup() {
        if (currentJob?.isActive == true) return
        val target = appSettings.getSandboxDistro()
        currentJob = scope.launch {
            try {
                val installed = installer.install(target) { step -> _state.value = step.toSandboxState() }
                marker = installed
                _state.value = SandboxState.Ready
            } catch (e: kotlinx.coroutines.CancellationException) {
                checkExistingInstallation()
            } catch (e: Exception) {
                android.util.Log.e("LinuxSandbox", "Setup failed", e)
                marker = paths.readMarker()
                _state.value = SandboxState.Error(e.message ?: "Setup failed")
            }
        }
    }

    private fun InstallStep.toSandboxState(): SandboxState = when (this) {
        is InstallStep.Download -> SandboxState.Downloading(fraction)

        is InstallStep.Extract -> SandboxState.Extracting

        is InstallStep.Configure -> SandboxState.Installing("Configuring...")

        is InstallStep.Packages -> SandboxState.Installing(
            if (packages.size == 1) "Installing ${packages.first()}..." else "Installing base packages...",
        )
    }

    fun cancel() {
        currentJob?.cancel()
        currentJob = null
        checkExistingInstallation()
    }

    /** proot for the installed rootfs, with the binds and flags its distro needs. */
    fun createProotExecutor(): ProotExecutor = ProotExecutor(createLauncher())

    private fun createLauncher(): ProotLauncher {
        val current = marker ?: legacyMarker()
        val spec = DistroSpec.of(current.distro)
        paths.copyLibtalloc()
        paths.ensureLayout()
        val binds = buildList {
            // Nothing to bind when /root is already part of the rootfs; only the
            // pre-unification layout keeps it on external storage.
            if (!current.homeOnRootfs) {
                add(paths.homeDir(current).absolutePath to "/root")
            }
            // Only a shared Debian has projects to expose; an Alpine sandbox has
            // no Kai Build behind it and the mount point would be a stray folder.
            if (current.distro == LinuxDistro.DEBIAN) {
                paths.ensureMountPoints()
                add(paths.projectsDir.absolutePath to "/root/projects")
            }
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

    /** Guest-path translation matching [createLauncher]'s binds. */
    fun fileMap(): GuestFileMap {
        val current = marker ?: legacyMarker()
        return GuestFileMap(
            rootfsDir = paths.rootfsDir,
            homeDir = paths.homeDir(current),
            projectsDir = if (current.distro == LinuxDistro.DEBIAN) paths.projectsDir else null,
            tmpDir = paths.tmpDir,
        )
    }

    // One bash session per logical caller (chat conversation, terminal scratch,
    // package-manager UI, etc.). Lazily created on first access; tracked here so
    // the sandbox-level `reset()` and per-conversation deletion can tear them
    // down. Live during the app process only — not persisted.
    private val shells = mutableMapOf<String, SessionShell>()
    private val _sessions = MutableStateFlow<List<String>>(emptyList())
    val sessions: StateFlow<List<String>> = _sessions

    // Debounce per-session transcript writes. A burst of commands (e.g. a
    // 1000-iteration loop) would otherwise re-serialize the entire conversations
    // JSON and rewrite SharedPreferences once per command.
    private val pendingSaves = mutableMapOf<String, Job>()

    fun shellFor(sessionId: String): SessionShell = synchronized(shells) {
        shells[sessionId]?.let { return it }
        val inner = PersistentSandboxShell(createProotExecutor(), tmpPath)
        val persistable = SandboxSessions.isPersistable(sessionId)
        val initialLines = if (persistable) {
            conversationStorage.conversations.value
                .firstOrNull { it.id == sessionId }?.shellTranscript.orEmpty()
        } else {
            emptyList()
        }
        val onChange: ((List<TerminalLine>) -> Unit)? = if (persistable) {
            { lines -> scheduleTranscriptSave(sessionId, lines) }
        } else {
            null
        }
        val wrapper = SessionShell(sessionId, inner, initialLines, onChange)
        shells[sessionId] = wrapper
        _sessions.value = shells.keys.toList()
        wrapper
    }

    private fun scheduleTranscriptSave(sessionId: String, lines: List<TerminalLine>) {
        synchronized(pendingSaves) {
            pendingSaves[sessionId]?.cancel()
            pendingSaves[sessionId] = scope.launch {
                try {
                    delay(TRANSCRIPT_SAVE_DEBOUNCE)
                    conversationStorage.updateShellTranscript(sessionId, lines)
                } finally {
                    synchronized(pendingSaves) { pendingSaves.remove(sessionId) }
                }
            }
        }
    }

    fun transcriptFor(sessionId: String): SnapshotStateList<TerminalLine> = shellFor(sessionId).transcript

    /**
     * Toggle prune-pause on an existing session shell. Does NOT create a shell
     * — if there's no shell for [sessionId] yet there's no transcript to gate.
     */
    fun setSessionInteractive(sessionId: String, interacting: Boolean) {
        val shell = synchronized(shells) { shells[sessionId] } ?: return
        shell.setPrunePaused(interacting)
    }

    fun clearTranscript(sessionId: String) {
        synchronized(shells) { shells[sessionId] }?.transcript?.clear()
    }

    fun closeShell(sessionId: String) {
        val removed = synchronized(shells) {
            val s = shells.remove(sessionId)
            _sessions.value = shells.keys.toList()
            s
        }
        removed?.reset()
    }

    private fun closeAllShells() {
        val all = synchronized(shells) {
            val snapshot = shells.values.toList()
            shells.clear()
            _sessions.value = emptyList()
            snapshot
        }
        all.forEach { it.reset() }
    }

    fun installPackages() {
        if (currentJob?.isActive == true) return
        currentJob = scope.launch {
            try {
                installOptionalPackages()
            } catch (_: kotlinx.coroutines.CancellationException) {
                _state.value = SandboxState.Ready
            } catch (e: Exception) {
                android.util.Log.e("LinuxSandbox", "Package install exception", e)
                _state.value = SandboxState.Error("Install failed: ${e.message}")
            }
        }
    }

    /**
     * Only runs when the user taps "Install Packages" in Settings — the base
     * packages are already guaranteed present by [setup].
     */
    private suspend fun installOptionalPackages() {
        val current = marker ?: return
        val manager = current.distro.packageManager
        val executor = createProotExecutor()
        // A shared rootfs means Kai Build could be installing an agent right now.
        LinuxInstaller.packageLock.withLock {
            for (pkg in current.distro.optionalPackages) {
                currentCoroutineContext().ensureActive()
                _state.value = SandboxState.Installing("Installing $pkg...")
                val result = executor.execute(
                    manager.installCommand(pkg),
                    timeoutSeconds = PACKAGE_TIMEOUT_SECONDS,
                )
                if (result["success"] as? Boolean != true) {
                    val detail = listOf("stderr", "error", "stdout")
                        .firstNotNullOfOrNull { (result[it] as? String)?.takeIf(String::isNotBlank) }
                        .orEmpty()
                    android.util.Log.e("LinuxSandbox", "Failed to install $pkg: $detail")
                    _state.value = SandboxState.Error("Failed to install $pkg: ${detail.take(200)}")
                    return
                }
            }
        }
        // Seed ~/.ssh/config with keepalive + accept-new defaults so any manual
        // `ssh user@host` from now on works without a prompt this shell cannot
        // answer. Idempotent; failures here are non-fatal.
        runCatching { SshConfigManager(File(homePath)).ensureDefaults() }
            .onFailure { android.util.Log.w("LinuxSandbox", "ssh defaults seed failed: ${it.message}") }
        _state.value = SandboxState.Ready
    }

    fun reset() {
        scope.launch {
            onBeforeReset?.invoke()
            closeAllShells()
            // Wipes the rootfs, the marker and the libtalloc copy. Project folders
            // live in external files and survive, as they do for a Kai Build uninstall.
            paths.root.deleteRecursively()
            marker = null
            _state.value = SandboxState.NotInstalled
        }
    }

    fun getDiskUsageMB(): Long {
        if (!paths.root.isDirectory) return 0
        // Manual stack walk instead of walkTopDown(): the latter throws an
        // AssertionError if a child entry transitions from directory→non-directory
        // between the iterator's isDirectory check and DirectoryState construction.
        // The rootfs can contain unix sockets / FIFOs / broken symlinks (e.g. from
        // user-run programs like node), and concurrent install activity also races
        // the walk. We skip bad entries and keep going.
        var total = 0L
        val stack = ArrayDeque<File>()
        stack.addLast(paths.root)
        while (stack.isNotEmpty()) {
            val dir = stack.removeLast()
            val children = try {
                dir.listFiles()
            } catch (_: Throwable) {
                null
            } ?: continue
            for (child in children) {
                try {
                    when {
                        child.isDirectory -> stack.addLast(child)
                        child.isFile -> total += child.length()
                        // skip sockets, FIFOs, broken symlinks
                    }
                } catch (_: Throwable) {
                    // skip transient/inaccessible entry, keep iterating
                }
            }
        }
        return total / (1024 * 1024)
    }

    /**
     * Both markers must be present: installs predating the SSH bundle report
     * not-installed and re-prompt, picking up the new packages on the next run
     * (both package managers skip already-installed ones).
     */
    fun arePackagesInstalled(): Boolean {
        if (_state.value !is SandboxState.Ready) return false
        return File(rootfsPath, "usr/bin/python3").exists() &&
            File(rootfsPath, "usr/bin/ssh").exists()
    }
}
