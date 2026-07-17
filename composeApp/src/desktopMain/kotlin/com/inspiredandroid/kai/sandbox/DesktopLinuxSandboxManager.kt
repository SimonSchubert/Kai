package com.inspiredandroid.kai.sandbox

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit

private val DEFAULT_PACKAGES = listOf(
    "git", "curl", "wget", "jq", "python", "nodejs", "openssh", "lftp", "rsync",
)

class DesktopLinuxSandboxManager(
    private val baseDir: File,
    private val downloader: MicromambaDownloader,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentJob: Job? = null

    private val _state = MutableStateFlow<SandboxState>(SandboxState.NotInstalled)
    val state: StateFlow<SandboxState> = _state

    val layout: MicromambaLayout = MicromambaLayout(baseDir)

    init {
        if (layout.binaryFile.exists() && layout.binaryFile.canExecute()) {
            _state.value = SandboxState.Ready
        }
    }

    fun setup() {
        if (currentJob?.isActive == true) return
        currentJob = scope.launch {
            val archiveFile = File(baseDir, "micromamba-download.tar.bz2")
            try {
                _state.value = SandboxState.Downloading(0f)
                downloader.download(archiveFile) { progress ->
                    _state.value = SandboxState.Downloading(progress)
                }
                _state.value = SandboxState.Extracting
                downloader.extractBinary(archiveFile, baseDir)
                layout.rootPrefix.mkdirs()
                _state.value = SandboxState.Ready
            } catch (e: kotlinx.coroutines.CancellationException) {
                _state.value = if (layout.binaryFile.exists() && layout.binaryFile.canExecute()) {
                    SandboxState.Ready
                } else {
                    SandboxState.NotInstalled
                }
            } catch (e: Exception) {
                _state.value = SandboxState.Error(e.message ?: "Setup failed")
            } finally {
                archiveFile.delete()
            }
        }
    }

    fun cancel() {
        currentJob?.cancel()
        currentJob = null
        _state.value = if (layout.binaryFile.exists() && layout.binaryFile.canExecute()) {
            SandboxState.Ready
        } else {
            SandboxState.NotInstalled
        }
    }

    fun reset() {
        scope.launch {
            currentJob?.cancel()
            baseDir.deleteRecursively()
            _state.value = SandboxState.NotInstalled
        }
    }

    fun installPackages() {
        if (currentJob?.isActive == true) return
        currentJob = scope.launch {
            try {
                for (pkg in DEFAULT_PACKAGES) {
                    ensureActive()
                    _state.value = SandboxState.Installing("Installing $pkg...")
                    val processBuilder = ProcessBuilder(
                        layout.binaryFile.absolutePath,
                        "install",
                        "-y",
                        "-p",
                        layout.rootPrefix.absolutePath,
                        "-c",
                        "conda-forge",
                        pkg,
                    ).redirectErrorStream(true)
                    // Without MAMBA_ROOT_PREFIX, micromamba has to guess where its own
                    // root lives (package cache, base-env bootstrap) independently of
                    // the -p target — and can fail during config load before it ever
                    // gets to resolving the package. Same env var DesktopPersistentShell
                    // already sets for the interactive shell case.
                    processBuilder.environment()["MAMBA_ROOT_PREFIX"] = layout.rootPrefix.absolutePath
                    val process = processBuilder.start()
                    val finished = process.waitFor(120, TimeUnit.SECONDS)
                    ensureActive()
                    if (!finished) {
                        process.destroyForcibly()
                        _state.value = SandboxState.Error("Timed out installing $pkg")
                        return@launch
                    }
                    if (process.exitValue() != 0) {
                        val output = process.inputStream.bufferedReader().readText()
                        _state.value = SandboxState.Error("Failed to install $pkg: ${output.take(500)}")
                        return@launch
                    }
                }
                _state.value = SandboxState.Ready
            } catch (e: kotlinx.coroutines.CancellationException) {
                _state.value = SandboxState.Ready
            } catch (e: Exception) {
                _state.value = SandboxState.Error("Install failed: ${e.message}")
            }
        }
    }

    fun arePackagesInstalled(): Boolean {
        if (_state.value !is SandboxState.Ready) return false
        return File(layout.rootPrefix, "bin/python3").exists() ||
            File(layout.rootPrefix, "bin/git").exists()
    }

    fun getDiskUsageMB(): Long {
        if (!baseDir.isDirectory) return 0
        var total = 0L
        val stack = ArrayDeque<File>()
        stack.addLast(baseDir)
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
                    }
                } catch (_: Throwable) {
                    // skip transient/inaccessible entry
                }
            }
        }
        return total / (1024 * 1024)
    }
}
