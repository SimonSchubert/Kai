# Memories

**Last verified:** 2026-10-02

Kai's memory system allows the AI to learn and retain information across conversations. Memories are stored persistently, injected into every system prompt for context, and can be reinforced over time. Well-established memories can be promoted into permanent behavior (the soul/system prompt); the heartbeat surfaces candidates for promotion.

## Concepts

### Memory

A persistent key-value entry containing a descriptive key, content, category, hit count, and optional source. Memories survive app restarts and are shared across all conversations.

### Category

Each memory belongs to one of four categories:

- **General** — user preferences, facts, and important information
- **Learning** — successful approaches and patterns that worked well
- **Error** — error resolutions and known issues
- **Preference** — user corrections and explicit preferences

### Reinforcement

A mechanism for tracking how often a memory proves useful. Each reinforcement increments the hit count and updates the timestamp. Memories with 5 or more hits become promotion candidates.

### Promotion

The process of graduating a well-reinforced memory into the permanent soul/system prompt. The promoted text is appended to the soul, and the memory is removed from the memory store. Promotion is not limited to heartbeat runs — the AI can promote a learning from a normal chat as well. See the [heartbeat spec](heartbeat.md) for details on how promotion candidates are surfaced.

## Storage

- Memories are serialized as JSON in app settings
- Thread-safe access via mutex for concurrent operations
- If the stored JSON can no longer be decoded (e.g. after a schema change), the raw blob is backed up under a separate settings key before the next write, so the only copy is never overwritten. The backup is not part of settings export
- Memory feature is enabled by default
- When disabled, memory tools are removed from available tools but stored memories are preserved

## Memory Lifecycle

1. AI stores a memory using `memory_store` (general) or `memory_learn` (categorized)
2. If a memory with the same key exists, it is updated
3. On subsequent conversations, AI can reinforce memories that prove useful via `memory_reinforce`
4. AI can remove outdated memories via `memory_forget`
5. Memories with a hit count of 5 or more (a new memory starts at 1) become promotion candidates, surfaced during heartbeat checks

## System Prompt Injection

When memory is enabled, all memories are retrieved and grouped by category in the system prompt:

- General memories listed under "Your Memories"
- Preference memories under "User Preferences"
- Learning memories under "Learnings" with reinforcement count shown
- Error memories under "Known Issues & Resolutions"

For on-device (local) chat, the injected memory section is budgeted to a maximum of 2,000 characters. Entries are appended in category order until the budget is reached; if no entries fit, the header is dropped entirely. Remote chat has no such cap and includes all memories.

## Default Instructions

Built-in memory instructions (included for every chat variant when memory is enabled) guide the AI to:

- Proactively store important information (names, preferences, projects, goals) with `memory_store`
- Use `memory_forget` for outdated or incorrect information
- Avoid storing trivial or transient information

For remote models only, an additional "Structured Learning" section tells the AI to categorize with `memory_learn` (Preference for corrections, Learning for successes, Error for resolutions) and to reinforce memories that produce good outcomes. On-device models don't get this section because `memory_learn` isn't exposed to them.

## Settings UI

The memories section lives in Settings → Agent and contains:

- **Toggle** — enables or disables the memory feature with a switch
- **Description** — explains that memories are included in every message for context
- **Memory list** — shown only while memory is enabled; the five most recently updated memories are shown inline; each entry displays the key (bold) and content (max 3 lines, truncated with ellipsis)
- **Show all button** — appears when more than five memories exist; opens a modal bottom sheet listing every memory
- **Edit memory** — tapping any memory row (inline or inside the bottom sheet) opens an edit bottom sheet that lets the user modify the memory content. The key is shown but not editable. Saving updates the memory's content and timestamp
- **Delete button** — per-memory trash icon to remove individual memories; deletion is deferred with a snackbar "Undo" option (~4 seconds) before the memory is permanently removed

## AI Tools

| Tool | Purpose |
|---|---|
| `memory_store` | Store general key-value memories (General category) |
| `memory_learn` | Store categorized memories with optional source tracking (Learning, Error, or Preference). Rejects the General category at runtime and suggests using `memory_store` instead |
| `memory_forget` | Remove a memory by key |
| `memory_reinforce` | Increment a memory's hit count |
| `promote_learning` | Graduate a memory into the soul/system prompt and remove it from the store. Rides the scheduling switch (not the memory switch) and is callable from any conversation, not only heartbeat runs |

`memory_store`, `memory_forget`, and `memory_reinforce` are also exposed to on-device models; `memory_learn` and `promote_learning` are not.

## Key Files

| File | Purpose |
|---|---|
| `composeApp/src/commonMain/.../data/MemoryStore.kt` | Core storage, retrieval, reinforcement, deletion, categorization |
| `composeApp/src/commonMain/.../data/SettingsJson.kt` | Shared settings-backed JSON persistence: decode-or-default, encode-and-write, locked read-modify-write |
| `composeApp/src/commonMain/.../data/AppSettings.kt` | Persistence layer for memories JSON and enable flag |
| `composeApp/src/commonMain/.../data/RemoteDataRepository.kt` | Loads memories and memory instructions for the system prompt; on-device tool allowlist |
| `composeApp/src/commonMain/.../data/ChatSystemPromptBuilder.kt` | Category-grouped memory sections, local 2,000-char budget, Structured Learning section |
| `composeApp/src/commonMain/.../data/HeartbeatManager.kt` | Surfaces promotion candidates (5+ hits) in the heartbeat prompt |
| `composeApp/src/commonMain/.../tools/AgentToolSet.kt` | Gates memory tools on the memory switch and `promote_learning` on the scheduling switch |
| `composeApp/src/commonMain/.../tools/CommonTools.kt` | AI tool definitions for memory_store, memory_learn, memory_forget, memory_reinforce |
| `composeApp/src/commonMain/.../tools/HeartbeatTools.kt` | promote_learning tool |
| `composeApp/src/commonMain/.../ui/settings/AgentSettings.kt` | Memory management UI (list, all-memories sheet, edit sheet) |
| `composeApp/src/commonMain/.../ui/settings/SettingsViewModel.kt` | Memory state management and user actions |
