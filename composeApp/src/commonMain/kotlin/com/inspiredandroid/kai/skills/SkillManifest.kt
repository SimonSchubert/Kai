package com.inspiredandroid.kai.skills

/**
 * An installed skill, read from its folder in the Linux sandbox at
 * `~/skills/<id>/`. The sandbox filesystem is the single source of truth — this
 * is just the in-memory view loaded from `SKILL.md` plus a listing of the folder's
 * other files. [bundledFilePaths] are surfaced in the system prompt so the model
 * knows what's available at `~/skills/<id>/`; their contents already live in the
 * sandbox and are read there via `execute_shell_command`.
 *
 * [isBuiltIn] marks skills that ship inside the app (loaded from compose resources
 * rather than the sandbox). They are always available when the sandbox is up,
 * cannot be uninstalled, and update with each app release.
 */
data class SkillManifest(
    val id: String,
    val displayName: String,
    val description: String,
    val body: String,
    val bundledFilePaths: List<String> = emptyList(),
    val isBuiltIn: Boolean = false,
)

/** Where a skill is downloaded from when installing into the sandbox. */
sealed class SkillSource {
    /** An arbitrary GitHub repo + folder path containing a `SKILL.md`. */
    data class GitHub(
        val owner: String,
        val repo: String,
        val ref: String = "main",
        val path: String,
    ) : SkillSource()
}

/**
 * The raw files of a skill downloaded from GitHub, ready to be written into the
 * sandbox at `~/skills/<id>/`. [rawSkillMd] is stored verbatim as `SKILL.md`;
 * [files] maps each sibling's relative path to its text content.
 */
data class DownloadedSkill(
    val id: String,
    val description: String,
    val rawSkillMd: String,
    val files: Map<String, String> = emptyMap(),
)

/**
 * Lightweight record returned by the registry browse call — one installable skill
 * discovered in a marketplace. Carries the full GitHub coordinates so it can be
 * installed directly (via the same path as a manual GitHub install) and labeled
 * with its [sourceName] in the browse list.
 */
data class RegistrySkillEntry(
    val id: String,
    val description: String,
    val owner: String,
    val repo: String,
    val ref: String,
    val skillPath: String,
    val requiresSandbox: Boolean,
    val sourceName: String,
)
