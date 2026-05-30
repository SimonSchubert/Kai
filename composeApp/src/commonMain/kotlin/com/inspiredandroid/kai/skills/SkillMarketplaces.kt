package com.inspiredandroid.kai.skills

/**
 * A skill marketplace: a public GitHub repo that hosts SKILL.md skills. The
 * registry browses each one — preferring its `.claude-plugin/marketplace.json`
 * index when present, falling back to scraping skill folders under [root].
 *
 * The curated set ([curatedSkillMarketplaces]) is intentionally small and vetted:
 * skills bundle scripts that run in the Linux sandbox, so the suggested list
 * favors trustworthy sources over breadth. Users can still install any skill from
 * an arbitrary repo via the "Install from GitHub" field.
 */
data class SkillMarketplace(
    val name: String,
    val owner: String,
    val repo: String,
    val ref: String = "main",
    /** Folder under which skill subfolders live, used only when no marketplace.json is present. */
    val root: String = "skills",
    /**
     * Optional allowlist of specific skill folder paths to surface from this repo.
     * When set, only these are browsed (the repo's marketplace.json / folder scan is
     * ignored) — used to cherry-pick the broadly-useful skills from a large repo
     * without flooding the list with ones that don't fit a mobile assistant.
     */
    val skills: List<String>? = null,
    /**
     * Skill folder names to hide from this repo's listing — used to drop skills
     * that don't work well on Kai while still surfacing the rest of the repo.
     * Applied after the allowlist/manifest/folder-scan selection.
     */
    val exclude: Set<String> = emptySet(),
)

/**
 * Vetted marketplaces shown in the browse dialog. From Anthropic's official repo
 * we keep the document/data and creative skills that work well in Kai
 * (pdf/docx/xlsx/pptx, algorithmic-art, slack-gif-creator) and exclude the rest —
 * mostly Claude.ai/Claude-Code-oriented ones that don't translate to a mobile
 * assistant. Add further trusted repos here.
 */
val curatedSkillMarketplaces: List<SkillMarketplace> = listOf(
    SkillMarketplace(
        name = "Anthropic",
        owner = "anthropics",
        repo = "skills",
        ref = "main",
        root = "skills",
        exclude = setOf(
            "mcp-builder",
            "skill-creator",
            "theme-factory",
            "web-artifacts-builder",
            "webapp-testing",
            "internal-comms",
            "frontend-design",
            "doc-coauthoring",
            "canvas-design",
            "brand-guidelines",
            "claude-api",
        ),
    ),
    // The most popular Claude-skills repo (Anthropic-accepted). It's a software-dev
    // methodology, so we surface only its general "how to work" skills — ideation and
    // planning — and skip the Claude-Code-internal or coding-flow ones (git worktrees,
    // code review, subagent dispatch, debugging, verification).
    SkillMarketplace(
        name = "Superpowers",
        owner = "obra",
        repo = "superpowers",
        ref = "main",
        skills = listOf(
            "skills/brainstorming",
            "skills/writing-plans",
        ),
    ),
)
