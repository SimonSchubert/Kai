# Tasks

**Last verified:** 2026-10-02

Kai's tasks feature enables the AI to schedule one-time or recurring actions for future execution. Tasks are created through AI tools, stored persistently, and executed automatically by a background scheduler that polls on a fixed interval.

## Concepts

### Task

A scheduled action containing an id (UUID), a human-readable description, a prompt to execute, a creation time, and a **trigger** that decides when it fires. Tasks track status (PENDING or COMPLETED), the result of their last execution, a consecutive failure count for backoff, and a log of their last 10 runs (timestamp, OK/FAIL, reason).

### Trigger

How a task is dispatched. Every task carries one of three triggers:

- **TIME** — fires once at its scheduled time, then transitions to COMPLETED.
- **CRON** — recurs on a cron expression; the scheduled time holds the next fire time. Stays PENDING; the scheduler advances it after each run.
- **HEARTBEAT** — a standing addition to every heartbeat self-check. Not picked up by the time-based poll loop; instead the prompt is appended to the heartbeat message under `## Heartbeat Additions`. Stays PENDING until the user (or AI) cancels it. Scheduled time and cron are ignored.

Legacy tasks stored before triggers existed are migrated on load (and the upgrade is persisted once): tasks with a cron expression become CRON, all others TIME.

### Cron Expression

A 5-field schedule format (`minute hour day-of-month month day-of-week`) used for recurring tasks. Supports wildcards (`*`), steps (`*/n`), ranges (`1-5`), and comma-separated lists. Day-of-week follows standard cron numbering (0=Sunday, 6=Saturday). Unlike standard cron, when both day-of-month and day-of-week are restricted, a time must match **both**. The parser searches up to ~2 years ahead for the next matching time; if none is found the task is marked COMPLETED.

## Task Lifecycle

1. The AI calls the `schedule_task` tool with a description, prompt, and exactly one trigger: `execute_at`, `cron`, or `on_heartbeat: true`
2. A new task is created with a UUID and a trigger (TIME/CRON/HEARTBEAT), and persisted to storage
3. For cron-based tasks, the first execution time is computed from the cron expression
4. The background scheduler polls every 60 seconds and checks for due tasks (TIME/CRON with execution time <= now). HEARTBEAT tasks are not picked up here
5. When due, the task's prompt is sent to the AI as a fresh standalone request with full tool access (it does not see or add to the user's chat history)
6. TIME tasks: status is set to COMPLETED after execution
7. CRON tasks: next execution time is computed from the cron expression and the task remains PENDING
8. HEARTBEAT tasks: their prompts are appended to the heartbeat user message under `## Heartbeat Additions` during each heartbeat run; they never transition state on their own
9. The execution result and timestamp are stored on the task

## Execution Rules

- Task execution is skipped if the app is currently processing another API call
- Due tasks run one after another; if a foreground chat request starts mid-cycle, the remaining tasks wait for the next poll
- Task prompts run through the full tool-calling loop so the AI can use available tools (e.g. send notifications, call MCP servers). They always use the default model selection (first configured remote service, falling back to on-device) — the heartbeat model override does not apply. Tool calls are bound to the heartbeat conversation's session. When the response is non-blank it is appended to the heartbeat conversation (prefixed with the task description as a bold header) and the unread-heartbeat indicator is set, so scheduled output shares the same surface as heartbeat output without polluting the main chat history
- Results are stored as the task's last result for audit purposes, and each run is added to the task's recent-activity log
- The scheduler also handles heartbeat checks, email polling, and (FOSS builds) SMS polling in the same poll loop. Email polling fetches headers in batches of up to 50 per account per poll and buffers them for the next heartbeat to pick up; no AI call runs during email or SMS polling itself

## Failure Handling

- Tasks track their consecutive failure count
- When a **cron task fails**, its execution time is advanced to the next scheduled cron time (preventing retry flooding every poll cycle)
- When a **one-time task fails**, exponential backoff is applied: the execution time is pushed forward by 60s × 2^failures, capped at 1 hour
- On successful execution, the failure counter resets to zero
- Failed tasks store the error (exception type plus message) as their last result, including what the scheduler will do next (e.g. the backoff delay or next cron time), for visibility in the settings UI
- A run interrupted because the scheduler itself stopped (e.g. the daemon shutting down) is not a failure: nothing is recorded and the task stays due, so it runs on the next cycle instead of skipping that occurrence

## AI Tools

| Tool | Purpose |
|---|---|
| `schedule_task` | Create a one-time, recurring, or heartbeat-triggered task with a description, prompt, and exactly one trigger |
| `list_tasks` | List all tasks, optionally filtered by status (PENDING or COMPLETED) |
| `cancel_task` | Remove a task by its id |

### schedule_task Validation

- **Exactly one** of `execute_at` (ISO 8601), `cron`, or `on_heartbeat: true` must be provided — they are mutually exclusive. Empty values (`"cron": ""`, `on_heartbeat: false`) count as not provided, since models often send unused optional fields as defaults
- `execute_at` accepts offset-qualified (`2026-04-22T22:32:39+02:00`, `...Z`) or naive (`2026-04-22T22:32:39`) values. Naive values are interpreted in the user's local timezone, which is surfaced to the AI via the `## Context` block's `Local time` line. Offset-qualified form is preferred to avoid UTC/local ambiguity
- Past-dated `execute_at` values (more than a minute before now) are rejected at tool-call time with an error that points at the `Local time` context — most often this is a UTC/local sign flip that the model can correct on retry
- The cron expression is not validated at call time; an expression the parser can't read schedules the task to run on the next poll
- Returns the created task's id, description, trigger, the requested `execute_at` (or "n/a"), and cron expression (or "none")

### Heartbeat-triggered tasks

Tasks created with `on_heartbeat: true` carry `trigger = HEARTBEAT`. Their prompts are appended to every heartbeat self-check under `## Heartbeat Additions`. They're the mechanism for standing heartbeat behaviour ("greet me on every heartbeat", "always summarise new emails") — the main heartbeat prompt stays untouched, and each addition is a cancellable first-class task visible via `list_tasks` and in the Scheduled Tasks UI. They stay PENDING until cancelled.

## Settings UI

The scheduled tasks section in settings provides:

- **Feature toggle** — enables or disables the scheduling feature globally; when disabled, no tasks execute and scheduling tools are unavailable to the AI
- **Task list** — shown only while the toggle is on. Each task shows its description, status (PENDING or COMPLETED), and either a local execution time with UTC offset, a human-readable cron description, or "on every heartbeat"
- **Tap a task** — opens a details sheet with the trigger, status, schedule (or next run for cron), creation time, consecutive failure count (when non-zero), last result, and a recent-activity log of up to the last 10 runs (timestamp + OK/FAIL + reason). For HEARTBEAT-trigger tasks the activity list is sourced from the heartbeat-wide log instead, since they don't fire on their own schedule
- **Delete button** — available on all tasks to remove them; deletion is deferred with a snackbar "Undo" option (~4 seconds) before the task is permanently cancelled

### Cron Description

Cron expressions are converted to readable descriptions in the UI:

- `0 9 * * *` displays as "Daily at 9:00"
- `0 14 * * 1,2,3` displays as "Every Mon, Tue, Wed at 14:00"
- `0 8 15 * *` displays as "Monthly on day 15 at 8:00"
- Complex expressions fall back to the raw cron string

## Storage

- Tasks are serialized as a JSON array in app settings
- All task operations are thread-safe via mutex synchronization
- The scheduling enabled state is stored as a separate boolean setting

## Scheduler Lifecycle

The scheduler runs on its own process-lifetime background scope — it is **not** coupled to any screen's lifetime. Both the chat screen (when it is created) and the Android foreground service (when it starts) start the scheduler; the first start creates the loop, later starts are no-ops. The loop only dies when the process dies.

Consequences:

- When the Activity is destroyed (user backgrounds the app, MIUI reclaims memory, etc.), the scheduler **keeps running** as long as the process is alive.
- On Android, the `DaemonService` foreground notification is what keeps the process alive. If the user disables the daemon, the process can be killed and the scheduler dies with it — tasks will resume firing the next time the app is opened (past-due tasks are picked up on the first poll, since anything scheduled at or before now counts as due).
- Aggressive OEM battery managers (MIUI, EMUI/Huawei) sometimes kill the foreground service while the activity is still alive in the background. To recover sooner, the main activity re-asserts the daemon on every foreground bring-up (idempotent when the service is already running), so the user only has to swipe back into the app rather than fully relaunch it.
- Task execution is gated on a "chat is busy" check supplied by the chat screen (so a foreground chat request doesn't race with a scheduled task). When the chat screen goes away, the check is reset to "never busy" so the daemon-only path keeps running unblocked.
- Scheduled tasks fire independently of heartbeat state — heartbeat being off never prevents a scheduled task from running.

## Daemon Mode

When daemon mode is active (Android foreground service), the app process is kept alive, so the scheduler scope keeps polling — tasks execute on time without user interaction. When daemon mode is off, the scheduler still runs whenever the app is open; missed tasks fire on the next open.

## Key Files

| File | Purpose |
|---|---|
| `composeApp/src/commonMain/.../data/ScheduledTask.kt` | Task data class and status enum |
| `composeApp/src/commonMain/.../data/TaskStore.kt` | Task CRUD operations and due-task queries |
| `composeApp/src/commonMain/.../data/SettingsJson.kt` | Shared settings-backed JSON persistence: decode-or-default, encode-and-write, locked read-modify-write |
| `composeApp/src/commonMain/.../data/TaskScheduler.kt` | Background poll loop and task execution |
| `composeApp/src/commonMain/.../data/CronExpression.kt` | Cron parsing and next-execution-time computation |
| `composeApp/src/commonMain/.../tools/SchedulingTools.kt` | AI tool definitions for schedule, list, cancel |
| `composeApp/src/commonMain/.../data/AppSettings.kt` | Persisted task JSON and scheduling toggle |
| `composeApp/src/commonMain/.../ui/settings/AgentSettings.kt` | Scheduled tasks UI section and task details sheet |
| `composeApp/src/commonMain/.../ui/settings/HeartbeatSection.kt` | Cron-to-readable-description formatting used by the task list |
| `composeApp/src/commonMain/.../ui/settings/SettingsViewModel.kt` | Scheduling toggle and deferred (undoable) task deletion |
| `composeApp/src/commonMain/.../tools/AgentToolSet.kt` | Exposes the scheduling tools only while scheduling is enabled |
| `composeApp/src/commonMain/.../ui/chat/ChatViewModel.kt` | Starts the scheduler and supplies the "chat is busy" check |
| `composeApp/src/commonMain/.../DaemonController.kt` | Background execution support |
| `composeApp/src/androidMain/.../DaemonService.kt` | Android foreground service that keeps the process alive so the scheduler scope keeps polling |
