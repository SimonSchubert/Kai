package com.inspiredandroid.kai.ui.sandbox

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inspiredandroid.kai.SandboxController
import com.inspiredandroid.kai.SandboxSessions
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.sandbox_packages_install_failed
import kai.composeapp.generated.resources.sandbox_packages_install_success
import kai.composeapp.generated.resources.sandbox_packages_uninstall_failed
import kai.composeapp.generated.resources.sandbox_packages_uninstall_success
import kai.composeapp.generated.resources.sandbox_packages_up_to_date
import kai.composeapp.generated.resources.sandbox_packages_upgrade_count
import kai.composeapp.generated.resources.sandbox_packages_upgrade_failed
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import kotlin.time.Duration.Companion.milliseconds

@Immutable
data class PackageEntry(
    val name: String,
    val version: String,
    val description: String? = null,
)

@Immutable
data class PackagesUiState(
    val installed: ImmutableList<PackageEntry> = persistentListOf(),
    val installedNames: ImmutableSet<String> = persistentSetOf(),
    val searchQuery: String = "",
    val searchResults: ImmutableList<PackageEntry> = persistentListOf(),
    val loadingInstalled: Boolean = false,
    val searching: Boolean = false,
    val mutating: ImmutableSet<String> = persistentSetOf(),
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

private val UPGRADE_SUMMARY_REGEX = Regex("""(\d+)\s+upgraded""")

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
        val cmd = "dpkg-query -W -f='\${Package}\t\${Version}\t\${binary:Summary}\n' | sort"
        val output = sandboxController.executeCommand(cmd, SandboxSessions.SYSTEM)
        log("loadInstalled", cmd, output)
        return parseDpkgLines(output)
    }

    private fun applyInstalled(parsed: List<PackageEntry>) {
        _state.update {
            it.copy(
                installed = parsed.toImmutableList(),
                installedNames = parsed.mapTo(mutableSetOf()) { p -> p.name }.toImmutableSet(),
            )
        }
    }

    fun updateSearchQuery(query: String) {
        searchJob?.cancel()
        _state.update { it.copy(searchQuery = query) }
        if (query.isBlank()) {
            _state.update { it.copy(searchResults = persistentListOf(), searching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS.milliseconds)
            _state.update { it.copy(searching = true) }
            runSearch(query)
        }
    }

    private suspend fun runSearch(query: String) {
        val cmd = "apt-cache search ${shellQuote(query)} | head -n $SEARCH_RESULT_LIMIT"
        val output = sandboxController.executeCommand(cmd, SandboxSessions.SYSTEM)
        log("runSearch($query)", cmd, output)
        val results = parseSearchLines(output).toImmutableList()
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
            cmd = "DEBIAN_FRONTEND=noninteractive apt-get install -y ${shellQuote(pkg.name)}",
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
            cmd = "DEBIAN_FRONTEND=noninteractive apt-get remove -y ${shellQuote(pkg.name)}",
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
            val updateResult = runAndCapture("DEBIAN_FRONTEND=noninteractive apt-get update -y")
            if (updateResult.hasAptErrors()) {
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
            val upgradeResult = runAndCapture("DEBIAN_FRONTEND=noninteractive apt-get upgrade -y")
            applyInstalled(loadInstalled())
            _state.update {
                val msg = if (upgradeResult.hasAptErrors()) {
                    SnackbarMessage(Res.string.sandbox_packages_upgrade_failed, upgradeResult.errorSummary())
                } else {
                    val count = countUpgradedPackages(upgradeResult.stdout)
                    if (count == 0) {
                        SnackbarMessage(Res.string.sandbox_packages_up_to_date)
                    } else {
                        SnackbarMessage(Res.string.sandbox_packages_upgrade_count, count.toString())
                    }
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
            it.copy(mutating = next.toImmutableSet())
        }
    }

    private data class CommandResult(val exit: Int, val stdout: String, val stderr: String) {
        fun errorSummary(): String {
            val tail = stderr.lineSequence().filter { it.isNotBlank() }.lastOrNull()
                ?: stdout.lineSequence().filter { it.isNotBlank() }.lastOrNull()
                ?: "exit code $exit"
            return tail.take(ERROR_SUMMARY_MAX_CHARS)
        }

        fun hasAptErrors(): Boolean {
            if (exit != 0) return true
            val errorPrefixes = listOf("E:", "Err:", "dpkg:")
            return stdout.lineSequence().any { line -> errorPrefixes.any { line.startsWith(it) } } ||
                stderr.lineSequence().any { line -> errorPrefixes.any { line.startsWith(it) } }
        }
    }

    private suspend fun runAndCapture(cmd: String): CommandResult {
        val stdoutChannel = Channel<String>(capacity = Channel.UNLIMITED)
        val stderrChannel = Channel<String>(capacity = Channel.UNLIMITED)
        val handle = sandboxController.executeCommandStreaming(
            command = cmd,
            onStdout = { stdoutChannel.trySend(it) },
            onStderr = { stderrChannel.trySend(it) },
            sessionId = SandboxSessions.SYSTEM,
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

    // apt-get emits a summary line like "12 upgraded, 2 newly installed, 0 to remove and 0 not upgraded."
    // We parse the "X upgraded" part to report back to the user.
    private fun countUpgradedPackages(stdout: String): Int {
        val match = UPGRADE_SUMMARY_REGEX.find(stdout)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    /**
     * Parses dpkg-query output: `name\tversion\tsummary`
     */
    private fun parseDpkgLines(raw: String): List<PackageEntry> = raw.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("dpkg-query:") }
        .mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 2) return@mapNotNull null
            val name = parts[0].trim()
            val version = parts[1].trim()
            val description = parts.getOrNull(2)?.trim()?.takeIf { it.isNotEmpty() }
            if (name.isEmpty()) null else PackageEntry(name, version, description)
        }
        .distinctBy { it.name }
        .toList()



    private fun parseSearchLines(raw: String): List<PackageEntry> = raw.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("WARNING:") && !it.startsWith("E:") }
        .mapNotNull { line ->
            // apt-cache search format: "name - description"
            val sepIdx = line.indexOf(" - ")
            if (sepIdx < 0) return@mapNotNull null
            val name = line.substring(0, sepIdx).trim()
            val description = line.substring(sepIdx + 3).trim().takeIf { it.isNotEmpty() }
            if (name.isEmpty()) null else PackageEntry(name, "", description)
        }
        .distinctBy { it.name }
        .toList()


    private fun shellQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"
}
