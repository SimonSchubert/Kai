# Memories

**Last verified:** 2026-03-18

Kai's memory system allows the AI to learn and retain information across conversations. Memories are stored persistently, injected into every system prompt for context, and can be reinforced over time. Well-established memories can be promoted into permanent behavior via the heartbeat feature.

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

The process of graduating a well-reinforced memory into the permanent soul/system prompt. Once promoted, the memory is removed from the memory store. See the [heartbeat spec](heartbeat.md) for details on how promotion candidates are surfaced.

## Storage

- Memories are serialized as JSON in app settings
- Thread-safe access via mutex for concurrent operations
- Memory feature is enabled by default
- When disabled, memory tools are removed from available tools but stored memories are preserved

## Memory Lifecycle

1. AI stores a memory using `memory_store` (general) or `memory_learn` (categorized)
2. If a memory with the same key exists, it is updated
3. On subsequent conversations, AI can reinforce memories that prove useful via `memory_reinforce`
4. AI can remove outdated memories via `memory_forget`
5. Memories with 5+ reinforcements become promotion candidates, surfaced during heartbeat checks

## System Prompt Injection

When memory is enabled, all memories are retrieved and grouped by category in the system prompt:

- General memories listed under "Your Memories"
- Preference memories under "User Preferences"
- Learning memories under "Learnings" with reinforcement count shown
- Error memories under "Known Issues & Resolutions"

## Default Instructions

Built-in memory instructions guide the AI to:

- Proactively store important information (names, preferences, projects, goals)
- Use `memory_forget` for outdated information
- Avoid storing trivial or transient information
- Categorize appropriately: Preference for corrections, Learning for successes, Error for resolutions
- Reinforce memories that produce good outcomes

## Settings UI

The memories section in settings contains:

- **Toggle** — enables or disables the memory feature with a switch
- **Description** — explains that memories are included in every message for context
- **Memory list** — each entry displays the key (bold) and content (max 3 lines, truncated with ellipsis)
- **Delete button** — per-memory trash icon to remove individual memories

## AI Tools

| Tool | Purpose |
|---|---|
| `memory_store` | Store general key-value memories (General category) |
| `memory_learn` | Store categorized memories with optional source tracking (Learning, Error, or Preference) |
| `memory_forget` | Remove a memory by key |
| `memory_reinforce` | Increment a memory's hit count |
| `promote_learning` | Graduate a reinforced memory into the soul/system prompt (via heartbeat) |

## Key Files

| File | Purpose |
|---|---|
| `composeApp/src/commonMain/.../data/MemoryStore.kt` | Core storage, retrieval, reinforcement, deletion, categorization |
| `composeApp/src/commonMain/.../data/AppSettings.kt` | Persistence layer for memories JSON and enable flag |
| `composeApp/src/commonMain/.../data/RemoteDataRepository.kt` | System prompt injection with category grouping |
| `composeApp/src/commonMain/.../tools/CommonTools.kt` | AI tool definitions for memory_store, memory_learn, memory_forget, memory_reinforce |
| `composeApp/src/commonMain/.../tools/HeartbeatTools.kt` | promote_learning tool |
| `composeApp/src/commonMain/.../ui/settings/SettingsScreen.kt` | Memory management UI |
| `composeApp/src/commonMain/.../ui/settings/SettingsViewModel.kt` | Memory state management and user actions |
