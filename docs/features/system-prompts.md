# System Prompts

**Last verified:** 2026-04-22

Kai has several distinct prompt-construction paths. Each one is built by a **pure function** with explicit inputs (no DI, no suspend, no resource loading, no clocks) and is covered by a unit-test suite so future edits don't silently break unrelated variations.

## Paths

| Path | Builder | Test | Used by |
|---|---|---|---|
| Chat (remote) | `buildChatSystemPrompt(variant = CHAT_REMOTE, …)` | `ChatSystemPromptBuilderTest` | Any remote service (OpenAI, Anthropic, Gemini, etc.) via `RemoteDataRepository.ask()` |
| Chat (on-device) | `buildChatSystemPrompt(variant = CHAT_LOCAL, …)` | `ChatSystemPromptBuilderTest` | LiteRT via `RemoteDataRepository.askWithLocalEngine()` |
| Heartbeat | `buildHeartbeatPrompt(…)` | `HeartbeatPromptBuilderTest` | `TaskScheduler` via `HeartbeatManager.buildHeartbeatPrompt()` — sent as a USER message, not a system prompt |
| Splinterlands LLM picker | `buildLlmPrompt(…)` in `SplinterlandsTeamPicker.kt` | `SplinterlandsTeamPickerPromptTest` | `SplinterlandsBattleRunner` — fully isolated, does not use the chat prompt builder |

The on-device tool allowlist (`LOCAL_TOOL_ALLOWLIST` in `RemoteDataRepository.kt`) is locked in by `LocalToolAllowlistTest`. Any rename or removal of a tool in that set fails the test loudly.

## Chat variants

`SystemPromptVariant` in `ChatSystemPromptBuilder.kt` controls which sections are composed. Each `if (variant == …)` block in `buildChatSystemPrompt` is the single source of truth for where a section belongs — there is no post-hoc regex stripping.

| Section | `CHAT_REMOTE` | `CHAT_LOCAL` | Gating input |
|---|:-:|:-:|---|
| Soul | always | always | `soul` param (non-empty) |
| Memory instructions (basic) | when provided | when provided | `memoryInstructions` param |
| `## Structured Learning` | always (remote-only block) | never | baked into `DEFAULT_STRUCTURED_LEARNING_SECTION` constant |
| `## Your Memories` | when list non-empty | when list non-empty (budget-capped) | `generalMemories` |
| `## User Preferences` | when list non-empty | when list non-empty (budget-capped) | `preferenceMemories` |
| `## Learnings` | when list non-empty | when list non-empty (budget-capped) | `learningMemories` (reinforcement count rendered) |
| `## Known Issues & Resolutions` | when list non-empty | when list non-empty (budget-capped) | `errorMemories` |
| `## Automation` | always (remote-only block) | never | baked into `DEFAULT_AUTOMATION_SECTION` constant — teaches the AI that all future/recurring/heartbeat-standing behaviour goes through `schedule_task` with one of three triggers (`execute_at`, `cron`, `on_heartbeat`). Heartbeat schedule itself is user-controlled |
| `## Email Accounts` | when list non-empty (email enabled) | never | `emailAccounts` — connected address, unread count, last sync, or last sync error |
| `## Scheduled Tasks` | when list non-empty | never | `pendingTasks` (time/cron only; heartbeat-trigger tasks live in the next section) |
| `## Heartbeat Additions` | when list non-empty | never | `heartbeatAdditions` — standing `schedule_task(on_heartbeat=true)` entries the AI can see/reference/cancel |
| `## Context` | always | always | `runtime` param (date, platform, model, provider) |
| `## Dynamic UI` | when `uiMode = DYNAMIC_UI` | never | `uiMode` param |
| `## Interactive UI Mode` | when `uiMode = INTERACTIVE_UI` | never | `uiMode` param |

**Memory budget for `CHAT_LOCAL`:** the four memory category sections share a combined char budget (`LOCAL_MEMORY_BUDGET_CHARS`, currently 2000 chars). Entries are appended in order (general → preferences → learnings → errors); the next entry that would push the combined size past the budget is dropped, and all subsequent entries are dropped too. Truncation happens at entry boundaries, never mid-entry. If no entries in a category fit, that category's header is not emitted either.

**Why two variants:**

- `CHAT_REMOTE` — full chat prompt for remote services. No limits.
- `CHAT_LOCAL` — trimmed chat prompt for on-device LiteRT. Small Gemma models (2-4B params) can't coherently attend to the full remote prompt, so Structured Learning, Automation, Email Accounts, Scheduled Tasks, Heartbeat Additions, and kai-ui sections are dropped, and memory sections are char-capped. The model still gets soul + basic memory guidance + memories (up to the cap) + runtime Context — memories matter because `memory_store` / `memory_forget` / `memory_reinforce` are all in the local tool allowlist, and a memory might have been learned via a remote model in an earlier turn.

Interactive UI mode is **not** available on on-device services — the kai-ui component schema is too large for small Gemma models to coherently attend to, and in practice the model can't produce valid kai-ui JSON. The "Start interactive mode" button in the empty-state UI is hidden when the primary service is on-device (see `ChatScreen.kt`). If you have LiteRT selected and need interactive UI mode, switch to a remote service first.

## Heartbeat sections

`buildHeartbeatPrompt` has no variant — it's a single shape sent as the user message.

| Section | Gating input |
|---|---|
| Opening prompt | `customOrDefaultPrompt` (custom or `DEFAULT_HEARTBEAT_PROMPT`) |
| `## Previous Heartbeat Results` | `recentResponses` non-empty |
| `## Heartbeat Additions` | `heartbeatAdditions` non-empty — appended standing instructions from `schedule_task(on_heartbeat=true)` tasks |
| `## Pending Tasks` (with optional cron annotation) | `pendingTasks` non-empty |
| `## Email Status` (per-account unread count + last sync) | `emailAccounts` non-empty |
| `## Promotion Candidates` (with hit counts + category) | `promotionCandidates` non-empty |

## How to add a new section

1. **Decide which variant(s) need the section.** For the chat builder, that's `CHAT_REMOTE`, `CHAT_LOCAL`, or both. For heartbeat, it's always "present iff gating input is non-empty".
2. **Add the input parameter** to the builder's signature.
3. **Add the `if (variant == …)` block** or conditional in the builder body — composition is explicit, not post-hoc.
4. **Add a focused test** in the corresponding `*BuilderTest` class that verifies the section is present when gated on, absent otherwise.
5. **Update this doc's table** with the new row.
6. **Update the wrapper** (`RemoteDataRepository.getActiveSystemPrompt` or `HeartbeatManager.buildHeartbeatPrompt`) to thread the new input through from `AppSettings` / stores.
7. **Run** `./gradlew :composeApp:desktopTest --tests '*BuilderTest'` to confirm nothing else broke.

## Key files

| File | Purpose |
|---|---|
| `composeApp/src/commonMain/.../data/ChatSystemPromptBuilder.kt` | Pure builder + `SystemPromptVariant` + section helpers + `DEFAULT_STRUCTURED_LEARNING_SECTION` |
| `composeApp/src/commonMain/.../data/HeartbeatPromptBuilder.kt` | Pure heartbeat builder + input data classes |
| `composeApp/src/commonMain/.../data/RemoteDataRepository.kt` | `getActiveSystemPrompt(variant)` wrapper that gathers inputs from `AppSettings` / `MemoryStore` / `TaskStore` and calls the pure builder |
| `composeApp/src/commonMain/.../data/HeartbeatManager.kt` | `buildHeartbeatPrompt()` wrapper that gathers inputs and calls the pure builder |
| `composeApp/src/commonMain/.../data/AppSettings.kt` | `DEFAULT_MEMORY_INSTRUCTIONS` (basic block, used by both variants). The advanced `## Structured Learning` block lives in `ChatSystemPromptBuilder.kt` and is remote-only |
| `composeApp/src/commonMain/.../splinterlands/SplinterlandsTeamPicker.kt` | `buildLlmPrompt` (separate path, independent contract) |
| `composeApp/src/commonTest/.../data/ChatSystemPromptBuilderTest.kt` | Focused + golden tests for every chat variant section |
| `composeApp/src/commonTest/.../data/HeartbeatPromptBuilderTest.kt` | Focused + golden tests for every heartbeat section |
| `composeApp/src/commonTest/.../data/LocalToolAllowlistTest.kt` | Lock-in test for the on-device tool allowlist |
| `composeApp/src/commonTest/.../splinterlands/SplinterlandsTeamPickerPromptTest.kt` | Focused tests for the Splinterlands LLM picker |
