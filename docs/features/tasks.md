# Tasks

**Last verified:** 2026-04-10

Kai's tasks feature enables the AI to schedule one-time or recurring actions for future execution. Tasks are created through AI tools, stored persistently, and executed automatically by a background scheduler that polls on a fixed interval.

## Concepts

### Task

A scheduled action containing an id (UUID), a human-readable description, a prompt to execute, a target execution time, and optionally a cron expression for recurrence. Tasks track their status (PENDING or COMPLETED), the result of their last execution, and a consecutive failure count for backoff.

### Cron Expression

A 5-field schedule format (`minute hour day-of-month month day-of-week`) used for recurring tasks. Supports wildcards (`*`), steps (`*/n`), ranges (`1-5`), and comma-separated lists. Day-of-week follows standard cron numbering (0=Sunday, 6=Saturday). The parser searches up to ~2 years ahead for the next matching time.

## Task Lifecycle

1. The AI calls the `schedule_task` tool with a description, prompt, and either an execution time or cron expression (or both)
2. A new task is created with a UUID and persisted to storage
3. For cron-based tasks, the first execution time is computed from the cron expression
4. The background scheduler polls every 60 seconds and checks for due tasks (execution time <= now)
5. When due, the task's prompt is sent to the AI via `askWithTools` (with full tool access but without adding to chat history)
6. For one-time tasks, the status is set to COMPLETED after execution
7. For recurring tasks, the next execution time is computed from the cron expression and the task remains PENDING
8. The execution result and timestamp are stored on the task

## Execution Rules

- Task execution is skipped if the app is currently processing another API call
- Task prompts are executed via `askWithTools`, which includes the full tool-calling loop so the AI can use available tools (e.g. send notifications, call MCP servers). The response is added as an assistant message without polluting the main chat history
- Results are stored as the `lastResult` on the task for audit purposes
- The scheduler also handles heartbeat checks and email polling in the same poll loop

## Failure Handling

- Tasks track their `consecutiveFailures` count
- When a **cron task fails**, its execution time is advanced to the next scheduled cron time (preventing retry flooding every poll cycle)
- When a **one-time task fails**, exponential backoff is applied: the execution time is pushed forward by `60s * 2^failures`, capped at 1 hour
- On successful execution, the failure counter resets to zero
- Failed tasks store the error message in `lastResult` for visibility in the settings UI

## AI Tools

| Tool | Purpose |
|---|---|
| `schedule_task` | Create a one-time or recurring task with a description, prompt, execution time, and/or cron expression |
| `list_tasks` | List all tasks, optionally filtered by status (PENDING or COMPLETED) |
| `cancel_task` | Remove a task by its id |

### schedule_task Validation

- At least one of `execute_at` (ISO 8601) or `cron` must be provided
- Returns the created task's id, description, scheduled time, and cron expression

## Settings UI

The scheduled tasks section in settings provides:

- **Feature toggle** — enables or disables the scheduling feature globally; when disabled, no tasks execute and scheduling tools are unavailable to the AI
- **Task list** — each task shows its description, status (PENDING or COMPLETED), and either a formatted execution time or a human-readable cron description
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

## Daemon Mode

When daemon mode is active, the task scheduler continues running in the background even when the app is not in the foreground, ensuring scheduled tasks execute on time without user interaction.

## Key Files

| File | Purpose |
|---|---|
| `composeApp/src/commonMain/.../data/ScheduledTask.kt` | Task data class and status enum |
| `composeApp/src/commonMain/.../data/TaskStore.kt` | Task CRUD operations and due-task queries |
| `composeApp/src/commonMain/.../data/TaskScheduler.kt` | Background poll loop and task execution |
| `composeApp/src/commonMain/.../data/CronExpression.kt` | Cron parsing and next-execution-time computation |
| `composeApp/src/commonMain/.../tools/SchedulingTools.kt` | AI tool definitions for schedule, list, cancel |
| `composeApp/src/commonMain/.../data/AppSettings.kt` | Persisted task JSON and scheduling toggle |
| `composeApp/src/commonMain/.../ui/settings/SettingsScreen.kt` | Scheduled tasks UI section |
| `composeApp/src/commonMain/.../DaemonController.kt` | Background execution support |
