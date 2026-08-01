package com.inspiredandroid.kai.build

import androidx.compose.runtime.Immutable
import com.inspiredandroid.kai.build.terminal.TerminalSnapshot
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

/** Ordered steps of a Kai Build install, shown as progress on the setup screen. */
enum class BuildStep {
    Download,
    Extract,
    Configure,
    BasePackages,
    Agent,
}

@Immutable
sealed interface BuildEnvironmentState {
    data object NotInstalled : BuildEnvironmentState

    /** [progress] is null while a step has no measurable progress. */
    data class Installing(
        val step: BuildStep,
        val progress: Float? = null,
        val agentId: String? = null,
    ) : BuildEnvironmentState

    data object Ready : BuildEnvironmentState
}

/**
 * One live shell inside a project — a tab in the terminal's session strip.
 * [agentId] is null for a plain shell; [number] disambiguates same-kind tabs.
 */
@Immutable
data class BuildTerminalSession(
    val id: String,
    val project: String,
    val agentId: String?,
    val number: Int,
    val terminal: TerminalSnapshot = TerminalSnapshot.blank(),
    val busy: Boolean = false,
)

/** What the installed Debian actually is, shown on the project list. */
@Immutable
data class BuildSystemInfo(
    /** e.g. "Debian GNU/Linux 12 (bookworm)". */
    val distribution: String,
    /** Linux architecture name of the rootfs (arm64, amd64, …). */
    val architecture: String,
    val packageCount: Int,
    /** Bytes taken by the Debian rootfs itself. */
    val systemBytes: Long,
    /** Bytes taken by the project folders (kept when Linux is uninstalled). */
    val projectsBytes: Long,
    val freeBytes: Long,
)

/** Everything the Kai Build screen renders, in one snapshot. */
@Immutable
data class KaiBuildState(
    val environment: BuildEnvironmentState = BuildEnvironmentState.NotInstalled,
    val installedAgents: ImmutableSet<String> = persistentSetOf(),
    /** Folder names under the Linux home's `projects` directory. */
    val projects: ImmutableList<String> = persistentListOf(),
    /** Live shells, oldest first. Sessions of every open project, not just the visible one. */
    val sessions: ImmutableList<BuildTerminalSession> = persistentListOf(),
    val activeSessionId: String? = null,
    /** Null until Debian is installed and its facts have been read. */
    val systemInfo: BuildSystemInfo? = null,
    /** Last install failure, in shell/English wording — cleared when work restarts. */
    val lastError: String? = null,
) {
    val isReady: Boolean get() = environment is BuildEnvironmentState.Ready
    val isInstalling: Boolean get() = environment is BuildEnvironmentState.Installing
    val activeSession: BuildTerminalSession? get() = sessions.firstOrNull { it.id == activeSessionId }
}
