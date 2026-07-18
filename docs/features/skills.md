# Skills

**Last verified:** 2026-07-18

Kai supports installable **skills**: reusable instruction bundles, modeled on Anthropic's [SKILL.md](https://github.com/anthropics/skills) format (now an open standard at [agentskills.io](https://agentskills.io)). A skill packages a name, a description, a body of instructions, and optional bundled files.

User-installed skills live **in the Linux sandbox**: each is a folder at `~/skills/<id>/` (its `SKILL.md` plus any files). There is no separate copy of those folders in app settings. Because of this, the Skills UI is **Android-only** (the only platform with a sandbox) and requires the sandbox to be installed first. The user browses a curated set of skill marketplaces (or installs from any GitHub repo) and triggers a skill in chat by starting a message with its slash command.

In addition, Kai ships a small set of **built-in skills** (currently `create-skill`) loaded from app compose resources. Built-ins appear in the Skills list once the sandbox is installed, are labeled as built-in, and are not removable through the UI. If a user installs a sandbox skill with the same id, the sandbox copy takes precedence over the built-in.

## Concepts

### Skill

Most skills are a folder `~/skills/<id>/` in the sandbox containing a `SKILL.md` (and any other files). The `SKILL.md` frontmatter provides a `name` (the slash-command id) and a `description`; the markdown after the frontmatter is the instruction body. `SkillManager` keeps an in-memory cache of installed sandbox skills plus any built-ins that are not overridden by a same-id sandbox folder; the cache is reloaded after every install/uninstall and once the sandbox becomes installed. On platforms without a sandbox the file ops are no-ops, so the cache is simply always empty — skills never appear off-Android.

A skill has an id (lowercase letters, digits, and hyphens; ≤ 64 chars), a display name derived from the id, the instruction body, and the list of its other top-level file names (surfaced in the prompt). There is no enable/disable state: an installed skill is active. Uninstall deletes the sandbox folder for user-installed skills; built-ins have no remove action.

### Slash command

In chat, a message whose first token is `/<skill-id>` activates that skill for that single turn. The id must match an installed skill (case-insensitive). The user's message text is sent **verbatim** — it is not rewritten or stripped — so the conversation visibly reflects what was typed. The skill's instructions in the system prompt tell the model how to interpret any arguments after the slash command. Slash commands are opt-in: a message that doesn't match a skill behaves like any normal message.

### Active skill section

When a turn activates a skill, the skill's body is appended to the system prompt under an "Active skill" heading for that turn only. On every other turn the section adds zero bytes to the prompt. If the skill's folder has other files, their names are listed with a note that they live at `~/skills/<id>/` in the Linux sandbox, where the model reads them via `execute_shell_command`. Nothing is materialized at chat time — the files are already in the sandbox.

### Marketplace

A marketplace is a public GitHub repo of skills. The browse list aggregates a small **curated, vetted** set of marketplaces (`curatedSkillMarketplaces`) — skills bundle scripts that run in the sandbox, so the suggested set favors trusted sources over breadth. Current sources:

- **Anthropic** ([anthropics/skills](https://github.com/anthropics/skills)) — a curated subset of the official repo that works well in Kai: document/data (pdf, docx, xlsx, pptx) and creative (algorithmic-art, slack-gif-creator). The Claude.ai/Claude-Code-oriented ones that don't translate to a mobile assistant (mcp-builder, skill-creator, theme-factory, web-artifacts-builder, webapp-testing, internal-comms, frontend-design, doc-coauthoring, canvas-design, brand-guidelines, claude-api) are excluded via the marketplace's `exclude` set.
- **Superpowers** ([obra/superpowers](https://github.com/obra/superpowers)) — the most popular Claude-skills repo, but a software-dev methodology, so only its broadly-useful "how to work" skills are surfaced via an allowlist (brainstorming, writing-plans); the Claude-Code-internal or coding-flow ones (git worktrees, code review, subagent dispatch, debugging, verification) are excluded.

A marketplace is read via the [Claude Code plugin-marketplace standard](https://github.com/anthropics/skills): an explicit per-source skill **allowlist** wins when set; otherwise a `.claude-plugin/marketplace.json`, when present, provides the authoritative skill list (and grouping); otherwise the registry falls back to scanning skill folders under the marketplace's `root` (default `skills/`). Any folder named in the source's **exclude** set is then dropped. In all cases a single recursive git-tree call per repo enumerates paths, and each `SKILL.md` is then fetched from `raw.githubusercontent.com` to keep within GitHub's unauthenticated API rate limit.

## Installing a Skill

The "Skills" section lives in the Tools tab of settings (Android only, below MCP servers).

- **If the sandbox isn't installed**, the section shows a notice and a "Set up sandbox" button that jumps to the Sandbox tab. No skills can be added until the sandbox is installed.
- Otherwise, **"Add Skill"** opens a bottom sheet where the user can either paste a GitHub reference (`owner/repo`, `owner/repo/path/to/skill`, a full `https://github.com/owner/repo` URL, or a `.../tree/<ref>/path` URL) or **browse** the curated marketplaces. The browse list is fetched automatically when the dialog opens, is searchable (filter by id, description, or source), shows each entry's source name, and marks already-installed entries.

Both paths install the same way: a browsed entry carries its full repo coordinates and installs through the same GitHub fetch as a manual install. Installing downloads the raw `SKILL.md` plus its sibling files (including nested ones; binaries and files over 256 KB are skipped) and writes them into `~/skills/<id>/`, replacing any existing folder, then reloads the cache. Install errors (invalid frontmatter, missing `SKILL.md`, unrecognized URL) are surfaced inline in the dialog.

## Skill Management

Each skill card shows the slash command (`/<id>`) and the description; expanding a **user-installed** skill reveals a remove button. Removal is deferred with a snackbar "Undo" option before the folder is deleted, matching the MCP and service flows. Built-in skills show a Built-in badge and omit the remove action.

## Chat Autocomplete

While the user is typing the first token of a message and it starts with `/`, a dropdown of installed skills appears above the composer, filtered by the typed query. Selecting an entry rewrites the leading token to the canonical `/<id> ` and positions the cursor for follow-up arguments. The dropdown only triggers when the cursor is within the leading slash token.

## Limitations

- Android only — user-installed skills live in the Linux sandbox, which other platforms don't have. The Skills section is hidden off-Android.
- The sandbox must be installed before skills (including built-ins) appear and before any skill can be added.
- Only text files are stored for GitHub installs; binaries and files over 256 KB are skipped.
- The skill body is appended verbatim to the system prompt, so very large skills consume prompt budget for the turn they're active.
- GitHub browsing/installation requires network access and uses the unauthenticated GitHub API (subject to its rate limits).
- Built-in skills are not sandbox folders unless the user installs an override with the same id.

## Key Files

| File | Purpose |
|---|---|
| `composeApp/src/commonMain/.../skills/SkillManifest.kt` | `SkillManifest` (in-memory view of an installed skill), `DownloadedSkill`, `RegistrySkillEntry`, `SkillSource` |
| `composeApp/src/commonMain/.../skills/SkillMarketplaces.kt` | `SkillMarketplace` model + the curated, vetted marketplace list |
| `composeApp/src/commonMain/.../skills/SkillFrontmatterParser.kt` | SKILL.md frontmatter parser and id validation |
| `composeApp/src/commonMain/.../skills/SkillRegistry.kt` | Browses marketplaces (`.claude-plugin/marketplace.json`, git-tree discovery, raw SKILL.md fetch) and downloads a skill's files |
| `composeApp/src/commonMain/.../skills/SkillManager.kt` | Reads/installs/uninstalls skills in the sandbox (`~/skills/`), in-memory cache, GitHub URL parsing |
| `composeApp/src/commonMain/.../data/ChatSystemPromptBuilder.kt` | Appends the active-skill section to the system prompt |
| `composeApp/src/commonMain/.../data/RemoteDataRepository.kt` | Per-turn active-skill resolution (no materialization — files already in the sandbox) |
| `composeApp/src/commonMain/.../ui/chat/ChatViewModel.kt` | Parses the leading slash command into a skill id |
| `composeApp/src/commonMain/.../ui/chat/composables/QuestionInput.kt` | Detects the slash query while typing |
| `composeApp/src/commonMain/.../ui/chat/composables/SkillAutocomplete.kt` | Slash-command dropdown above the composer |
| `composeApp/src/commonMain/.../ui/settings/SkillsSection.kt` | Skill cards, sandbox-install prompt, and add-skill bottom sheet (GitHub + marketplace browse) |
| `composeApp/src/commonMain/.../ui/settings/SettingsViewModel.kt` | Skill install/uninstall and browse UI state |
| `composeApp/src/commonMain/.../ui/settings/SettingsScreen.kt` | Gates the Skills section on Android + sandbox-installed via `sandboxState` |
