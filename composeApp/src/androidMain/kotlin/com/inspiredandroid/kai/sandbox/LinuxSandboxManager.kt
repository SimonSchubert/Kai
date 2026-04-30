package com.inspiredandroid.kai.sandbox

import android.content.Context
import android.os.Build
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.inspiredandroid.kai.TerminalLine
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

class LinuxSandboxManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentJob: Job? = null
    private val _state = MutableStateFlow<SandboxState>(SandboxState.NotInstalled)
    val state: StateFlow<SandboxState> = _state

    private val sandboxDir: File
        get() = File(context.filesDir, "linux-sandbox")

    val rootfsPath: String get() = File(sandboxDir, "rootfs").absolutePath

    // Sandbox /root is bind-mounted from externally-visible app storage so files
    // produced by the agent can be opened via FileProvider Intents. Computed
    // lazily on first access; mkdirs and the one-time legacy-home migration run
    // once per process, then the cached path is reused for every shell call.
    val homePath: String by lazy {
        val external = context.getExternalFilesDir(null)
        val target = if (external != null) {
            File(external, "sandbox-home")
        } else {
            File(sandboxDir, "home")
        }
        target.mkdirs()
        val legacy = File(sandboxDir, "home")
        val newHomeIsEmpty = target.listFiles().isNullOrEmpty()
        if (legacy.isDirectory && legacy.absolutePath != target.absolutePath && newHomeIsEmpty) {
            try {
                legacy.listFiles()?.forEach { entry ->
                    val dest = File(target, entry.name)
                    if (!dest.exists()) entry.copyRecursively(dest, overwrite = false)
                }
            } catch (e: Exception) {
                android.util.Log.w("LinuxSandbox", "Legacy home migration failed: ${e.message}")
            }
        }
        target.absolutePath
    }

    val tmpPath: String get() = File(sandboxDir, "tmp").absolutePath

    // Run proot directly from nativeLibraryDir where Android grants execute permission
    val prootPath: String get() = File(context.applicationInfo.nativeLibraryDir, "libproot.so").absolutePath
    val nativeLibDir: String get() = context.applicationInfo.nativeLibraryDir

    private val downloader = RootfsDownloader(HttpClient(Android))

    init {
        checkExistingInstallation()
    }

    private fun checkExistingInstallation() {
        val rootfs = File(sandboxDir, "rootfs")
        val proot = File(prootPath)
        if (rootfs.isDirectory && proot.exists() && proot.canExecute()) {
            _state.value = SandboxState.Ready
        }
    }

    private fun getLinuxArch(): String {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        return when {
            abi.startsWith("arm64") -> "aarch64"
            abi.startsWith("armeabi") -> "armhf"
            abi.startsWith("x86_64") -> "x86_64"
            abi.startsWith("x86") -> "x86"
            else -> "aarch64"
        }
    }

    fun setup() {
        if (currentJob?.isActive == true) return
        currentJob = scope.launch {
            try {
                setupInternal()
            } catch (e: kotlinx.coroutines.CancellationException) {
                checkExistingInstallation()
            } catch (e: Exception) {
                _state.value = SandboxState.Error(e.message ?: "Setup failed")
            }
        }
    }

    fun cancel() {
        currentJob?.cancel()
        currentJob = null
        // Clean up partial downloads
        File(sandboxDir, "rootfs.tar.gz").delete()
        // Determine correct state based on what exists
        val rootfs = File(sandboxDir, "rootfs")
        if (rootfs.isDirectory && File(prootPath).exists()) {
            _state.value = SandboxState.Ready
        } else {
            _state.value = SandboxState.NotInstalled
        }
    }

    private suspend fun setupInternal() {
        val arch = getLinuxArch()

        // Verify proot is available in nativeLibraryDir
        val proot = File(prootPath)
        if (!proot.exists()) {
            throw IllegalStateException(
                "Proot binary not found at $prootPath. " +
                    "nativeLibraryDir contents: ${File(nativeLibDir).listFiles()?.map { it.name } ?: "empty"}",
            )
        }

        // Create directories. `homePath` getter creates the externally-visible
        // sandbox-home dir on access, so we only need to ensure sandboxDir + tmp.
        sandboxDir.mkdirs()
        File(sandboxDir, "tmp").mkdirs()

        // Copy libtalloc with correct soname (Android strips .so.2 suffix in jniLibs)
        copyLibtalloc()

        // Download rootfs
        val rootfsDir = File(sandboxDir, "rootfs")
        if (!rootfsDir.isDirectory) {
            val tarGzFile = File(sandboxDir, "rootfs.tar.gz")
            try {
                _state.value = SandboxState.Downloading(0f)
                downloader.download(arch, tarGzFile) { progress ->
                    _state.value = SandboxState.Downloading(progress)
                }

                _state.value = SandboxState.Extracting
                downloader.extractTarGz(tarGzFile, rootfsDir)
            } finally {
                tarGzFile.delete()
            }
        }

        // Post-setup
        _state.value = SandboxState.Installing("Configuring...")
        downloader.makeWritable(rootfsDir)
        downloader.writeResolvConf(rootfsDir)

        val executor = createProotExecutor()
        var updated = false
        for (mirror in downloader.mirrors) {
            downloader.writeRepositories(rootfsDir, mirror)
            val result = executor.execute("apk update", timeoutSeconds = 60)
            if (result["success"] as? Boolean == true) {
                updated = true
                break
            }
        }
        if (!updated) {
            throw IllegalStateException("apk update failed on all Alpine mirrors")
        }

        _state.value = SandboxState.Ready
    }

    private fun copyLibtalloc() {
        val tallocTarget = File(sandboxDir, "libtalloc.so.2")
        if (tallocTarget.exists()) return

        val source = File(nativeLibDir, "libtalloc.so")
        if (source.exists()) {
            source.copyTo(tallocTarget, overwrite = true)
        }
    }

    fun createProotExecutor(): ProotExecutor = ProotExecutor(
        prootPath = prootPath,
        libDir = sandboxDir.absolutePath,
        rootfsPath = rootfsPath,
        homePath = homePath,
        tmpPath = tmpPath,
    )

    // One bash session per logical caller (chat conversation, terminal scratch,
    // package-manager UI, etc.). Lazily created on first access; tracked here so
    // the sandbox-level `reset()` and per-conversation deletion can tear them
    // down. Live during the app process only — not persisted.
    private val shells = mutableMapOf<String, SessionShell>()
    private val _sessions = MutableStateFlow<List<String>>(emptyList())
    val sessions: StateFlow<List<String>> = _sessions

    fun shellFor(sessionId: String): SessionShell = synchronized(shells) {
        shells[sessionId]?.let { return it }
        val inner = PersistentSandboxShell(createProotExecutor(), tmpPath)
        val wrapper = SessionShell(sessionId, inner)
        shells[sessionId] = wrapper
        _sessions.value = shells.keys.toList()
        wrapper
    }

    fun transcriptFor(sessionId: String): SnapshotStateList<TerminalLine> =
        shellFor(sessionId).transcript

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
        val packages = listOf(
            "bash", "curl", "wget", "git", "jq", "python3", "py3-pip", "nodejs",
            // Remote-server tooling (issue #214). apk add is idempotent so
            // existing installs that bump into this list pay nothing for the
            // already-present ones.
            "openssh-client", "lftp", "rsync",
        )
        currentJob = scope.launch {
            try {
                val executor = createProotExecutor()
                for (pkg in packages) {
                    ensureActive()
                    _state.value = SandboxState.Installing("Installing $pkg...")
                    val result = executor.execute("apk add --no-cache $pkg", timeoutSeconds = 120)
                    ensureActive()
                    val success = result["success"] as? Boolean ?: false
                    if (!success) {
                        val stderr = result["stderr"] as? String ?: ""
                        val stdout = result["stdout"] as? String ?: ""
                        val error = result["error"] as? String ?: ""
                        val timedOut = result["timed_out"] as? Boolean ?: false
                        val exitCode = result["exit_code"] as? Int ?: -1
                        android.util.Log.e("LinuxSandbox", "Failed to install $pkg: exit=$exitCode timedOut=$timedOut error=$error stdout=$stdout stderr=$stderr")
                        _state.value = SandboxState.Error("Failed to install $pkg: ${stderr.ifEmpty { error }.ifEmpty { stdout }.take(200)}")
                        return@launch
                    }
                }
                _state.value = SandboxState.Ready
            } catch (_: kotlinx.coroutines.CancellationException) {
                _state.value = SandboxState.Ready
            } catch (e: Exception) {
                android.util.Log.e("LinuxSandbox", "Package install exception", e)
                _state.value = SandboxState.Error("Install failed: ${e.message}")
            }
        }
    }

    fun reset() {
        scope.launch {
            closeAllShells()
            sandboxDir.deleteRecursively()
            _state.value = SandboxState.NotInstalled
        }
    }

    fun getDiskUsageMB(): Long {
        if (!sandboxDir.exists()) return 0
        return sandboxDir.walkTopDown().sumOf { it.length() } / (1024 * 1024)
    }

    fun arePackagesInstalled(): Boolean {
        if (_state.value !is SandboxState.Ready) return false
        // Both checks must pass: existing installs that predate the SSH bundle
        // will report not-installed and re-prompt, picking up the new packages
        // on the next install run (apk skips already-installed ones).
        return File(rootfsPath, "usr/bin/python3").exists() &&
            File(rootfsPath, "usr/bin/ssh").exists()
    }
}
