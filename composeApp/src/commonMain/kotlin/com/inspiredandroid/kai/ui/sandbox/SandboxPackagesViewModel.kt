package com.inspiredandroid.kai.ui.sandbox

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inspiredandroid.kai.SandboxController
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.sandbox_packages_install_failed
import kai.composeapp.generated.resources.sandbox_packages_install_success
import kai.composeapp.generated.resources.sandbox_packages_uninstall_failed
import kai.composeapp.generated.resources.sandbox_packages_uninstall_success
import kai.composeapp.generated.resources.sandbox_packages_upgrade_failed
import kai.composeapp.generated.resources.sandbox_packages_upgraded
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

@Immutable
data class PackageEntry(
    val name: String,
    val version: String,
    val description: String? = null,
)

@Immutable
data class PackagesUiState(
    val installed: List<PackageEntry> = emptyList(),
    val installedNames: Set<String> = emptySet(),
    val searchQuery: String = "",
    val searchResults: List<PackageEntry> = emptyList(),
    val loadingInstalled: Boolean = false,
    val searching: Boolean = false,
    val mutating: Set<String> = emptySet(),
    val pendingUninstall: PackageEntry? = null,
    val upgrading: Boolean = false,
    val snackbarMessage: SnackbarMessage? = null,
)

@Immutable
data class SnackbarMessage(val resource: StringResource, val arg: String? = null)

private const val SEARCH_DEBOUNCE_MS = 300L
private const val SEARCH_RESULT_LIMIT = 200
private const val ERROR_SUMMARY_MAX_CHARS = 200
private const val LOG_TAG = "SandboxPackages"

private val ALPINE_REVISION_SUFFIX = Regex("-r\\d+$")

class SandboxPackagesViewModel(
    private val sandboxController: SandboxController,
) : ViewModel() {

    private val _state = MutableStateFlow(PackagesUiState())
    val state = _state.asStateFlow()

    private var searchJob: Job? = null

    fun start() {
        val current = _state.value
        if (current.installed.isNotEmpty() || current.loadingInstalled) return
        refreshInstalled()
    }

    fun refreshInstalled() {
        if (_state.value.loadingInstalled) return
        _state.update { it.copy(loadingInstalled = true) }
        viewModelScope.launch {
            applyInstalled(loadInstalled())
            _state.update { it.copy(loadingInstalled = false) }
        }
    }

    private suspend fun loadInstalled(): List<PackageEntry> {
        val cmd = "apk info -v | sort"
        val output = sandboxController.executeCommand(cmd)
        log("loadInstalled", cmd, output)
        return parseInfoLines(output)
    }

    private fun applyInstalled(parsed: List<PackageEntry>) {
        _state.update {
            it.copy(
                installed = parsed,
                installedNames = parsed.mapTo(mutableSetOf()) { p -> p.name },
            )
        }
    }

    fun updateSearchQuery(query: String) {
        searchJob?.cancel()
        _state.update { it.copy(searchQuery = query) }
        if (query.isBlank()) {
            _state.update { it.copy(searchResults = emptyList(), searching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            _state.update { it.copy(searching = true) }
            runSearch(query)
        }
    }

    private suspend fun runSearch(query: String) {
        val cmd = "apk search -v ${shellQuote(query)} | head -n $SEARCH_RESULT_LIMIT"
        val output = sandboxController.executeCommand(cmd)
        log("runSearch($query)", cmd, output)
        val results = parseSearchLines(output)
        _state.update {
            if (it.searchQuery == query) {
                it.copy(searchResults = results, searching = false)
            } else {
                it
            }
        }
    }

    fun install(pkg: PackageEntry) {
        mutateInstalled(
            pkg = pkg,
            cmd = "apk add --no-cache ${shellQuote(pkg.name)}",
            successWhenInstalled = true,
            successRes = Res.string.sandbox_packages_install_success,
            failureRes = Res.string.sandbox_packages_install_failed,
        )
    }

    fun requestUninstall(pkg: PackageEntry) {
        _state.update { it.copy(pendingUninstall = pkg) }
    }

    fun cancelUninstall() {
        _state.update { it.copy(pendingUninstall = null) }
    }

    fun confirmUninstall() {
        val pkg = _state.value.pendingUninstall ?: return
        _state.update { it.copy(pendingUninstall = null) }
        mutateInstalled(
            pkg = pkg,
            cmd = "apk del ${shellQuote(pkg.name)}",
            successWhenInstalled = false,
            successRes = Res.string.sandbox_packages_uninstall_success,
            failureRes = Res.string.sandbox_packages_uninstall_failed,
        )
    }

    // apk's exit code is polluted by cumulative DB error count under PRoot, so we
    // verify success by re-reading the installed list and checking the package's
    // presence (install) or absence (uninstall) instead of trusting the exit code.
    private fun mutateInstalled(
        pkg: PackageEntry,
        cmd: String,
        successWhenInstalled: Boolean,
        successRes: StringResource,
        failureRes: StringResource,
    ) {
        if (pkg.name in _state.value.mutating) return
        markMutating(pkg.name, true)
        viewModelScope.launch {
            val result = runAndCapture(cmd)
            applyInstalled(loadInstalled())
            markMutating(pkg.name, false)
            val isInstalled = pkg.name in _state.value.installedNames
            val succeeded = isInstalled == successWhenInstalled
            val msg = if (succeeded) {
                SnackbarMessage(successRes, pkg.name)
            } else {
                SnackbarMessage(failureRes, result.errorSummary())
            }
            _state.update { it.copy(snackbarMessage = msg) }
        }
    }

    fun upgradePackages() {
        if (_state.value.upgrading) return
        _state.update { it.copy(upgrading = true) }
        viewModelScope.launch {
            // apk's exit code is polluted by cumulative DB error count under PRoot.
            // Real apk failures are prefixed with "ERROR:" — the lowercase "errors"
            // in the summary line is just a cumulative count, not a per-run failure.
            val updateResult = runAndCapture("apk update")
            if (updateResult.hasApkErrors()) {
                _state.update {
                    it.copy(
                        upgrading = false,
                        snackbarMessage = SnackbarMessage(
                            Res.string.sandbox_packages_upgrade_failed,
                            updateResult.errorSummary(),
                        ),
                    )
                }
                return@launch
            }
            val upgradeResult = runAndCapture("apk upgrade")
            applyInstalled(loadInstalled())
            _state.update {
                val msg = if (!upgradeResult.hasApkErrors()) {
                    SnackbarMessage(Res.string.sandbox_packages_upgraded)
                } else {
                    SnackbarMessage(Res.string.sandbox_packages_upgrade_failed, upgradeResult.errorSummary())
                }
                it.copy(upgrading = false, snackbarMessage = msg)
            }
        }
    }

    fun consumeSnackbar() {
        _state.update { it.copy(snackbarMessage = null) }
    }

    private fun markMutating(name: String, mutating: Boolean) {
        _state.update {
            val next = it.mutating.toMutableSet()
            if (mutating) next.add(name) else next.remove(name)
            it.copy(mutating = next)
        }
    }

    private data class CommandResult(val exit: Int, val stdout: String, val stderr: String) {
        fun errorSummary(): String {
            val tail = stderr.lineSequence().filter { it.isNotBlank() }.lastOrNull()
                ?: stdout.lineSequence().filter { it.isNotBlank() }.lastOrNull()
                ?: "exit code $exit"
            return tail.take(ERROR_SUMMARY_MAX_CHARS)
        }

        fun hasApkErrors(): Boolean = stdout.lineSequence().any { it.startsWith("ERROR:") } ||
            stderr.lineSequence().any { it.startsWith("ERROR:") }
    }

    private suspend fun runAndCapture(cmd: String): CommandResult {
        val stdoutChannel = Channel<String>(capacity = Channel.UNLIMITED)
        val stderrChannel = Channel<String>(capacity = Channel.UNLIMITED)
        val handle = sandboxController.executeCommandStreaming(
            command = cmd,
            onStdout = { stdoutChannel.trySend(it) },
            onStderr = { stderrChannel.trySend(it) },
        )
        val exit = handle.awaitExit()
        stdoutChannel.close()
        stderrChannel.close()
        val stdout = buildString { for (line in stdoutChannel) appendLine(line) }
        val stderr = buildString { for (line in stderrChannel) appendLine(line) }
        println("$LOG_TAG [runAndCapture] exit=$exit cmd=$cmd")
        logMultiline("runAndCapture stdout", stdout)
        logMultiline("runAndCapture stderr", stderr)
        return CommandResult(exit, stdout, stderr)
    }

    private fun log(label: String, cmd: String, output: String) {
        println("$LOG_TAG [$label] cmd=$cmd")
        logMultiline("$label output", output)
    }

    private fun logMultiline(label: String, body: String) {
        if (body.isEmpty()) {
            println("$LOG_TAG [$label] <empty>")
            return
        }
        body.lineSequence().forEach { line ->
            if (line.isNotEmpty()) println("$LOG_TAG [$label] $line")
        }
    }

    private fun parseInfoLines(raw: String): List<PackageEntry> = raw.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("WARNING:") && !it.startsWith("ERROR:") }
        .mapNotNull { line -> parseNameVersion(line)?.let { PackageEntry(it.first, it.second) } }
        .distinctBy { "${it.name}@${it.version}" }
        .toList()

    private fun parseSearchLines(raw: String): List<PackageEntry> = raw.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("WARNING:") && !it.startsWith("ERROR:") }
        .mapNotNull { line ->
            val sepIdx = line.indexOf(" - ")
            val nameVer = if (sepIdx >= 0) line.substring(0, sepIdx) else line
            val description = if (sepIdx >= 0) line.substring(sepIdx + 3).trim() else null
            parseNameVersion(nameVer)?.let { (n, v) -> PackageEntry(n, v, description?.takeIf { it.isNotEmpty() }) }
        }
        .distinctBy { "${it.name}@${it.version}" }
        .toList()

    // Alpine package idents are `<name>-<version>-r<rev>`, but names themselves
    // can contain hyphen-digit segments (e.g. `webkit2gtk-4.1`, `glib-2.0`).
    // Strategy: peel off the trailing `-r<digits>` revision, then split at the
    // *last* `-<digit>` boundary — that's the version, anything before it is name.
    private fun parseNameVersion(s: String): Pair<String, String>? {
        if (s.isEmpty()) return null
        val revision = ALPINE_REVISION_SUFFIX.find(s)?.value.orEmpty()
        val withoutRev = if (revision.isNotEmpty()) s.dropLast(revision.length) else s
        var splitAt = -1
        for (i in withoutRev.length - 1 downTo 1) {
            if (withoutRev[i - 1] == '-' && withoutRev[i].isDigit()) {
                splitAt = i - 1
                break
            }
        }
        if (splitAt < 0) {
            return if (revision.isNotEmpty()) withoutRev to revision.trimStart('-') else s to ""
        }
        val name = withoutRev.substring(0, splitAt)
        val version = withoutRev.substring(splitAt + 1) + revision
        if (name.isEmpty()) return null
        return name to version
    }

    private fun shellQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"
}
