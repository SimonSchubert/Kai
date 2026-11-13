package com.kainext.duo.build

import androidx.compose.runtime.Immutable

/**
 * A coding agent CLI that Kai Build can install into its Debian system.
 *
 * The vendor installers are the ones each project publishes; Kai Build does not
 * reimplement them. [binary] is what `command -v` is asked about — that is how
 * the app tells an installed agent from a missing one without trusting a file
 * path that differs between install methods.
 */
@Immutable
data class BuildAgent(
    val id: String,
    val label: String,
    /** Executable name on `PATH` inside the guest. */
    val binary: String,
    /** One shell line; runs as root inside the guest. */
    val installCommand: String,
)

/** The agents offered at setup. Order is the order they are listed in. */
object BuildAgents {
    val all: List<BuildAgent> = listOf(
        BuildAgent(
            id = "claude",
            label = "Claude Code",
            binary = "claude",
            installCommand = "curl -fsSL https://claude.ai/install.sh | bash",
        ),
        BuildAgent(
            id = "codex",
            label = "Codex",
            binary = "codex",
            installCommand = NODE_GUARD + "npm i -g @openai/codex",
        ),
        BuildAgent(
            id = "gemini",
            label = "Gemini CLI",
            binary = "gemini",
            installCommand = NODE_GUARD + "npm i -g @google/gemini-cli",
        ),
        BuildAgent(
            id = "opencode",
            label = "OpenCode",
            binary = "opencode",
            installCommand = "curl -fsSL https://opencode.ai/install | bash",
        ),
        BuildAgent(
            id = "aider",
            label = "Aider",
            binary = "aider",
            installCommand = "python3 -m pip install --user --break-system-packages aider-chat",
        ),
    )

    fun byId(id: String?): BuildAgent? = all.firstOrNull { it.id == id }

    /**
     * `npm` is not part of Debian's base set, and the npm-backed installers need
     * it. Installing it here keeps every agent a single line the manager can run
     * without branching.
     */
    private const val NODE_GUARD =
        "command -v npm >/dev/null 2>&1 || { apt-get update -y && apt-get install -y --no-install-recommends nodejs npm; }; "

    /** Shell snippet that prints the ids of the agents whose binary is on `PATH`. */
    val detectCommand: String =
        "for c in ${all.joinToString(" ") { it.binary }}; do command -v \"\$c\" >/dev/null 2>&1 && printf '%s\\n' \"\$c\"; done"
}
