package com.kainext.duo.build

import androidx.compose.runtime.Immutable

/**
 * What the setup progress line is showing, as a value rather than a sentence.
 *
 * The install runs on Android, where there is no composition to read resources
 * from — so the platform reports which step it is on and the UI is what turns
 * that into words.
 */
@Immutable
sealed interface KaiBuildStage {
    data class Download(val percent: Int) : KaiBuildStage
    data object Extract : KaiBuildStage
    data object Configure : KaiBuildStage
    data object BasePackages : KaiBuildStage

    /** [agentId] indexes [BuildAgents]; [label] is what the progress line shows. */
    data class Agent(val agentId: String, val label: String) : KaiBuildStage
}

/** One live shell session. Its transcript lives in the controller, keyed by [id]. */
@Immutable
data class KaiBuildSessionInfo(
    val id: String,
    val project: String,
    /** The agent that was launched in it, or null for a plain shell. */
    val agentLabel: String? = null,
)

/**
 * Everything the Kai Build screen renders. Kept as one immutable value so the
 * screen has a single source of truth across the install, projects, sessions
 * and system tabs.
 */
@Immutable
data class KaiBuildState(
    val isInstalled: Boolean = false,
    val isInstalling: Boolean = false,
    val stage: KaiBuildStage? = null,
    val lastError: String? = null,
    val projects: List<String> = emptyList(),
    val currentProject: String? = null,
    val sessions: List<KaiBuildSessionInfo> = emptyList(),
    val currentSession: String? = null,
    val isSessionActive: Boolean = false,
    val installedAgents: Set<String> = emptySet(),
    val packagesInstalled: Int = 0,
    val diskSystemMB: Long = 0,
    val diskProjectsMB: Long = 0,
    val diskFreeMB: Long = 0,
) {
    /** Sessions belonging to [project], in the order they were started. */
    fun sessionsOf(project: String): List<KaiBuildSessionInfo> = sessions.filter { it.project == project }

    fun hasSession(project: String): Boolean = sessions.any { it.project == project }
}
