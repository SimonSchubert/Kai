package com.inspiredandroid.kai.skills

import com.inspiredandroid.kai.data.SharedJson
import com.inspiredandroid.kai.httpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Fetches SKILL.md skills from public GitHub repositories.
 *
 * Browsing a marketplace is done with as few rate-limited GitHub *API* calls as
 * possible: one recursive git-tree call per repo enumerates every path (used to
 * find skill folders and to flag which ones bundle scripts), and everything else
 * — `.claude-plugin/marketplace.json`, each `SKILL.md` — is fetched from
 * `raw.githubusercontent.com`, which isn't subject to the 60-req/hour API cap.
 */
class SkillRegistry(
    private val client: HttpClient = httpClient(),
    private val json: Json = SharedJson,
) {

    /** File extensions that, if present in a skill folder, mark it sandbox-required. */
    private val sandboxExtensions = setOf("py", "sh", "js", "ts", "rb", "pl", "lua")

    /**
     * Browses every curated marketplace in parallel and returns the combined,
     * de-duplicated list of installable skills. A single marketplace failing
     * (network, rate limit) drops only that source — the rest still return.
     */
    suspend fun browseMarketplaces(marketplaces: List<SkillMarketplace>): Result<List<RegistrySkillEntry>> = runCatching {
        coroutineScope {
            marketplaces
                .map { async { browseMarketplace(it).getOrNull().orEmpty() } }
                .awaitAll()
                .flatten()
                // Same skill can appear in multiple sources; keep the first (curated order).
                .distinctBy { "${it.owner}/${it.repo}/${it.skillPath}" }
        }
    }

    /**
     * Lists the installable skills in one marketplace. Skill folders come from
     * `.claude-plugin/marketplace.json` when present, otherwise from any folder
     * under [SkillMarketplace.root] that contains a `SKILL.md`.
     */
    suspend fun browseMarketplace(marketplace: SkillMarketplace): Result<List<RegistrySkillEntry>> = runCatching {
        val (owner, repo, ref) = Triple(marketplace.owner, marketplace.repo, marketplace.ref)
        val treePaths = fetchRepoTree(owner, repo, ref)

        // An explicit allowlist skips the manifest fetch entirely.
        val manifest = if (marketplace.skills != null) {
            null
        } else {
            fetchRawFile(owner, repo, ref, MARKETPLACE_MANIFEST_PATH)
                ?.let { runCatching { parseMarketplaceManifest(it) }.getOrNull() }
        }

        val skillDirs = selectSkillDirs(treePaths, marketplace.skills, manifest, marketplace.root, marketplace.exclude)

        coroutineScope {
            skillDirs.map { dir ->
                async {
                    val md = fetchRawFile(owner, repo, ref, "$dir/SKILL.md") ?: return@async null
                    val parsed = SkillFrontmatterParser.parse(md)
                    if (parsed !is SkillFrontmatterParser.Result.Ok) return@async null
                    val requiresSandbox = treePaths.any {
                        it.startsWith("$dir/") && it.substringAfterLast('.', "").lowercase() in sandboxExtensions
                    }
                    RegistrySkillEntry(
                        id = parsed.id,
                        description = parsed.description,
                        owner = owner,
                        repo = repo,
                        ref = ref,
                        skillPath = dir,
                        requiresSandbox = requiresSandbox,
                        sourceName = marketplace.name,
                    )
                }
            }.awaitAll().filterNotNull().sortedBy { it.id }
        }
    }

    /**
     * Downloads a skill's files from a GitHub repo/folder, ready to be written into
     * the sandbox by [SkillManager]. The raw `SKILL.md` is kept verbatim; sibling
     * text files (including nested ones, e.g. a skill's `core` scripts) are
     * downloaded with their relative paths. Binaries and oversized files are skipped.
     */
    suspend fun fetchSkillFiles(source: SkillSource): Result<DownloadedSkill> = runCatching {
        val (owner, repo, ref, path) = when (source) {
            is SkillSource.GitHub -> Quad(source.owner, source.repo, source.ref, source.path)
        }

        val skillMd = fetchRawFile(owner, repo, ref, "$path/SKILL.md")
            ?: error("SKILL.md not found at $owner/$repo:$ref:$path/SKILL.md")
        val parsed = when (val r = SkillFrontmatterParser.parse(skillMd)) {
            is SkillFrontmatterParser.Result.Ok -> r
            is SkillFrontmatterParser.Result.Err -> error("Invalid SKILL.md frontmatter: ${r.reason}")
        }

        // One recursive tree call enumerates every file under the skill folder —
        // including nested ones — without a Contents API call per folder.
        val tree = runCatching { fetchRepoTree(owner, repo, ref) }.getOrDefault(emptySet())
        val siblings = tree
            .filter { it.startsWith("$path/") && it != "$path/SKILL.md" }
            .map { it.removePrefix("$path/") } // relative subpath, may contain '/'

        // Download the bundled files in parallel — sequential round-trips here were
        // the main source of the install delay. Binaries and oversized files are dropped.
        val files = coroutineScope {
            siblings
                .filter { it.substringAfterLast('.', "").lowercase() !in BINARY_EXTENSIONS }
                .map { file ->
                    async {
                        val content = fetchRawFile(owner, repo, ref, "$path/$file")
                        if (content != null && content.length <= MAX_BUNDLED_FILE_CHARS) file to content else null
                    }
                }
                .awaitAll()
                .filterNotNull()
                .toMap()
        }

        DownloadedSkill(
            id = parsed.id,
            description = parsed.description,
            rawSkillMd = skillMd,
            files = files,
        )
    }

    /**
     * Recursively lists every blob path in a repo via the git trees API — one
     * call instead of a Contents call per folder. Returns an empty set on failure.
     */
    private suspend fun fetchRepoTree(owner: String, repo: String, ref: String): Set<String> {
        val url = "https://api.github.com/repos/$owner/$repo/git/trees/$ref?recursive=1"
        val response = client.get(url) {
            header("Accept", "application/vnd.github+json")
            header("X-GitHub-Api-Version", "2022-11-28")
        }
        if (!response.status.isSuccess()) return emptySet()
        val body = response.bodyAsText()
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return emptySet()
        val treeArray = root["tree"] as? JsonArray ?: return emptySet()
        return treeArray.mapNotNull { entry ->
            val obj = entry.jsonObject
            val type = obj["type"]?.jsonPrimitive?.contentOrNull
            val path = obj["path"]?.jsonPrimitive?.contentOrNull
            if (type == "blob" && path != null) path else null
        }.toSet()
    }

    private suspend fun fetchRawFile(owner: String, repo: String, ref: String, path: String): String? {
        val url = "https://raw.githubusercontent.com/$owner/$repo/$ref/$path"
        val response = client.get(url)
        if (response.status == HttpStatusCode.NotFound) return null
        if (!response.status.isSuccess()) return null
        return response.bodyAsText()
    }

    private data class Quad(val a: String, val b: String, val c: String, val d: String)

    companion object {
        private const val MARKETPLACE_MANIFEST_PATH = ".claude-plugin/marketplace.json"
        private const val MAX_BUNDLED_FILE_CHARS = 256_000

        private val BINARY_EXTENSIONS = setOf(
            "png", "jpg", "jpeg", "gif", "webp", "bmp", "ico", "svg",
            "zip", "tar", "gz", "bz2", "7z", "rar",
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "mp3", "mp4", "wav", "ogg", "flac", "mov", "avi", "webm",
            "ttf", "otf", "woff", "woff2",
            "exe", "dll", "so", "dylib", "bin",
        )

        /**
         * Decides which skill folders to surface for a marketplace, given the repo's
         * file [treePaths]. Precedence: an explicit [allowlist] wins, else the
         * [manifestPaths] from marketplace.json, else every `<root>/<name>/SKILL.md`
         * folder. In all cases only folders that actually carry a `SKILL.md` are kept,
         * and any whose folder name is in [exclude] is dropped. Pure so the selection
         * logic can be unit-tested without the network.
         */
        fun selectSkillDirs(
            treePaths: Set<String>,
            allowlist: List<String>?,
            manifestPaths: List<String>?,
            root: String,
            exclude: Set<String> = emptySet(),
        ): List<String> {
            val hasSkillMd = { dir: String -> "$dir/SKILL.md" in treePaths }
            val selected = when {
                allowlist != null -> allowlist.map { it.trim('/') }.filter(hasSkillMd)

                !manifestPaths.isNullOrEmpty() -> manifestPaths.filter(hasSkillMd)

                else -> {
                    val prefix = "$root/"
                    treePaths
                        .filter { it.startsWith(prefix) && it.endsWith("/SKILL.md") }
                        .map { it.removeSuffix("/SKILL.md") }
                        // Only one level under root (root/<name>/SKILL.md), not nested.
                        .filter { it.removePrefix(prefix).none { ch -> ch == '/' } }
                        .distinct()
                }
            }
            return if (exclude.isEmpty()) selected else selected.filter { it.substringAfterLast('/') !in exclude }
        }

        /**
         * Extracts skill folder paths from a `.claude-plugin/marketplace.json`.
         * Flattens every plugin's `skills` array, keeping only same-repo entries
         * (`source` of `"./"` or absent) and normalizing `./skills/x` → `skills/x`.
         * Pure and side-effect-free so it can be unit-tested directly.
         */
        fun parseMarketplaceManifest(jsonText: String): List<String> {
            val root = runCatching { SharedJson.parseToJsonElement(jsonText).jsonObject }.getOrNull() ?: return emptyList()
            val plugins = root["plugins"] as? JsonArray ?: return emptyList()
            val out = mutableListOf<String>()
            for (plugin in plugins) {
                val obj = plugin.jsonObject
                val source = obj["source"]?.jsonPrimitive?.contentOrNull
                // Skip plugins that point at a different repo — we only browse this one.
                if (source != null && source != "./" && source != ".") continue
                val skills = obj["skills"] as? JsonArray ?: continue
                for (s in skills) {
                    val raw = s.jsonPrimitive.contentOrNull ?: continue
                    val normalized = raw.trim().removePrefix("./").trim('/')
                    if (normalized.isNotEmpty()) out.add(normalized)
                }
            }
            return out.distinct()
        }
    }
}
