package com.inspiredandroid.kai.build

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * The coding agents Kai Build can install on a fresh Debian. Each one ships a
 * self-contained installer, so the base system plus curl is all they need.
 *
 * Install locations (guest paths under `/root`):
 * - Claude → `~/.local/bin/claude` (versioned under `~/.local/share/claude/versions/`)
 * - Grok → `~/.grok/bin/grok`
 * - OpenCode → `~/.opencode/bin/opencode`
 *
 * Proot injects all three bin dirs into PATH for probes/installers. Login
 * shells rebuild PATH from profile files, so Kai Build also writes a
 * profile.d snippet and launches agents by absolute path.
 */
@Immutable
data class BuildAgent(
    val id: String,
    /** Product name — shown as-is, not translated. */
    val title: String,
    val binary: String,
    val installCommand: String,
)

object BuildAgents {

    val all: ImmutableList<BuildAgent> = persistentListOf(
        BuildAgent(
            id = "claude-code",
            title = "Claude Code",
            binary = "claude",
            installCommand = "curl -fsSL https://claude.ai/install.sh | bash",
        ),
        BuildAgent(
            id = "grok",
            title = "Grok",
            binary = "grok",
            installCommand = "curl -fsSL https://x.ai/cli/install.sh | bash",
        ),
        BuildAgent(
            id = "opencode",
            title = "OpenCode",
            binary = "opencode",
            installCommand = "curl -fsSL https://opencode.ai/install | bash",
        ),
    )

    fun get(id: String?): BuildAgent? = all.firstOrNull { it.id == id }
}
