package com.inspiredandroid.kai.skills

import com.inspiredandroid.kai.SandboxController
import com.inspiredandroid.kai.getBackgroundDispatcher
import kai.composeapp.generated.resources.Res
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.CoroutineContext

/**
 * Manages the user's skills. Most skills live in the Linux sandbox at `~/skills/<id>/`
 * (each is a folder containing `SKILL.md` plus any bundled files); a small set of
 * "built-in" skills ships inside the app as compose resources and is merged into the
 * same in-memory cache so synchronous callers ([getInstalled], [getSkill]) stay cheap.
 * On id collision the sandbox copy wins, so users can override a built-in.
 *
 * The cache is (re)loaded after every install/uninstall and whenever the sandbox
 * becomes installed — built-ins are loaded then too, gated on sandbox availability
 * because they only make sense when their `execute_shell_command` writes can land.
 * On platforms without a sandbox the file ops are no-ops and `load()` never runs, so
 * no skills (built-in or otherwise) appear off-Android.
 */
class SkillManager(
    private val sandboxController: SandboxController,
    private val registry: SkillRegistry = SkillRegistry(),
    backgroundDispatcher: CoroutineContext = getBackgroundDispatcher(),
) {

    private val scope = CoroutineScope(SupervisorJob() + backgroundDispatcher)
    private val mutex = Mutex()

    private val _skills = MutableStateFlow<List<SkillManifest>>(emptyList())
    val skills: StateFlow<List<SkillManifest>> = _skills

    init {
        // Load once the sandbox is installed (the file ops resolve real paths only
        // then); the StateFlow re-emits when it flips, so reset/install refresh too.
        scope.launch {
            var wasInstalled = false
            sandboxController.status.collect { status ->
                if (status.installed && !wasInstalled) load()
                wasInstalled = status.installed
            }
        }
    }

    fun getInstalled(): List<SkillManifest> = _skills.value

    fun getSkill(id: String): SkillManifest? = _skills.value.firstOrNull { it.id == id }

    suspend fun uninstall(id: String) {
        sandboxController.deleteEntry("$SKILLS_DIR/$id", recursive = true)
        load()
    }

    suspend fun installFromGitHub(owner: String, repo: String, ref: String, path: String): Result<SkillManifest> = registry.fetchSkillFiles(SkillSource.GitHub(owner, repo, ref, path)).mapCatching { install(it) }

    /** Installs a skill the user picked from the browse list, using its repo coordinates. */
    suspend fun installFromRegistryEntry(entry: RegistrySkillEntry): Result<SkillManifest> = installFromGitHub(entry.owner, entry.repo, entry.ref, entry.skillPath)

    /** Browses the curated marketplaces and returns the combined, searchable list. */
    suspend fun browseMarketplaces(): Result<List<RegistrySkillEntry>> = registry.browseMarketplaces(curatedSkillMarketplaces)

    /** Writes a downloaded skill into `~/skills/<id>/`, replacing any existing copy, then reloads. */
    internal suspend fun install(downloaded: DownloadedSkill): SkillManifest {
        val base = "$SKILLS_DIR/${downloaded.id}"
        sandboxController.deleteEntry(base, recursive = true) // replace if present
        sandboxController.writeTextFile("$base/SKILL.md", downloaded.rawSkillMd)
        for ((relPath, content) in downloaded.files) {
            val safe = relPath.split('/', '\\').filterNot { it.isEmpty() || it == ".." }
            if (safe.isEmpty()) continue
            sandboxController.writeTextFile("$base/${safe.joinToString("/")}", content)
        }
        load()
        return getSkill(downloaded.id) ?: error("Skill '${downloaded.id}' not found after install")
    }

    /** Reads every `~/skills/<id>/` folder back into the in-memory cache. */
    suspend fun load() {
        val skills = mutex.withLock {
            val sandboxSkills = sandboxController.listDirectory(SKILLS_DIR)
                .filter { it.isDirectory }
                .mapNotNull { dir ->
                    val base = "$SKILLS_DIR/${dir.name}"
                    val md = sandboxController.readTextFile("$base/SKILL.md") ?: return@mapNotNull null
                    val parsed = SkillFrontmatterParser.parse(md) as? SkillFrontmatterParser.Result.Ok
                        ?: return@mapNotNull null
                    val files = sandboxController.listDirectory(base)
                        .filter { !it.isDirectory && it.name != "SKILL.md" }
                        .map { it.name }
                        .sorted()
                    SkillManifest(
                        id = parsed.id,
                        displayName = SkillFrontmatterParser.displayName(parsed.id),
                        description = parsed.description,
                        body = parsed.body,
                        bundledFilePaths = files,
                    )
                }
            // Sandbox-installed skills win on id collision so power users can override a built-in.
            val sandboxIds = sandboxSkills.mapTo(mutableSetOf()) { it.id }
            val builtIns = loadBuiltInSkills().filter { it.id !in sandboxIds }
            (builtIns + sandboxSkills).sortedBy { it.id }
        }
        _skills.value = skills
    }

    /**
     * Reads bundled SKILL.md files shipped in compose resources. They appear alongside
     * sandbox-installed skills, can be invoked as `/<id>` from chat, and cannot be
     * uninstalled. Updates flow with each app release — nothing is persisted to the
     * sandbox. A built-in whose resource read or frontmatter parse fails is silently
     * dropped (no user-facing failure for a missing/broken bundled asset).
     */
    private suspend fun loadBuiltInSkills(): List<SkillManifest> = BUILT_IN_SKILL_IDS.mapNotNull { id ->
        val bytes = runCatching { Res.readBytes("files/skills/$id/SKILL.md") }.getOrNull()
            ?: return@mapNotNull null
        val parsed = SkillFrontmatterParser.parse(bytes.decodeToString()) as? SkillFrontmatterParser.Result.Ok
            ?: return@mapNotNull null
        SkillManifest(
            id = parsed.id,
            displayName = SkillFrontmatterParser.displayName(parsed.id),
            description = parsed.description,
            body = parsed.body,
            isBuiltIn = true,
        )
    }

    companion object {
        /** Absolute sandbox path of the skills folder (`~/skills`, home = `/root`). */
        const val SKILLS_DIR = "/root/skills"

        /**
         * Ids of skills bundled in compose resources at
         * `composeResources/files/skills/<id>/SKILL.md`. Hardcoded so the asset path is
         * explicit at compile time and we don't need a resource directory listing.
         */
        private val BUILT_IN_SKILL_IDS = listOf("create-skill")
    }
}

/**
 * Parses several common forms users might paste to add a GitHub skill:
 * - `owner/repo`
 * - `owner/repo/path/to/skill`
 * - `https://github.com/owner/repo`
 * - `https://github.com/owner/repo/tree/<ref>/path/to/skill`
 *
 * Returns null on a shape we don't recognize so the dialog can surface a hint.
 */
fun parseGitHubSkillUrl(input: String): SkillSource.GitHub? {
    val trimmed = input.trim().removePrefix("https://").removePrefix("http://").removePrefix("github.com/")
    if (trimmed.isEmpty()) return null
    val parts = trimmed.trim('/').split('/').filter { it.isNotEmpty() }
    if (parts.size < 2) return null
    val owner = parts[0]
    val repo = parts[1]
    if (parts.size == 2) {
        return SkillSource.GitHub(owner = owner, repo = repo, ref = "main", path = "")
    }
    // owner/repo/tree/<ref>/<path…> or owner/repo/<path…> (assume main)
    return if (parts[2] == "tree" && parts.size >= 5) {
        val ref = parts[3]
        val path = parts.drop(4).joinToString("/")
        SkillSource.GitHub(owner = owner, repo = repo, ref = ref, path = path)
    } else {
        val path = parts.drop(2).joinToString("/")
        SkillSource.GitHub(owner = owner, repo = repo, ref = "main", path = path)
    }
}
