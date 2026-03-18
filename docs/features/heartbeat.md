# Heartbeat

**Last verified:** 2026-03-18

Kai's heartbeat feature enables periodic automatic self-checks. The AI reviews pending tasks, email status, and learned memories on a configurable interval, surfacing anything that needs attention without requiring user interaction.

## Concepts

### Heartbeat

A silent, scheduled prompt sent to the AI during active hours. If nothing needs attention, the AI responds with "HEARTBEAT_OK" and the user sees nothing. If something requires follow-up, the response appears as an assistant message in the chat.

### Active Hours

A configurable time window (default 8:00–22:00) during which heartbeats are allowed to fire. Outside this window, heartbeats are skipped regardless of interval.

### Promotion

A mechanism for graduating well-established memories into the permanent soul/system prompt. Memories that have been reinforced 5 or more times become promotion candidates and are surfaced during heartbeat checks for the AI to evaluate.

## Configuration

Heartbeat configuration is stored as a serialized JSON object in app settings. All values are configurable from both the settings UI and via the `configure_heartbeat` AI tool:

- **Enabled**: true
- **Interval**: 30 minutes between heartbeats (UI slider offers 5m, 10m, 15m, 30m, 45m, 1h, 2h, 4h)
- **Active hours start**: 8 (hour, 24h format; UI range slider covers 0–23)
- **Active hours end**: 22 (hour, 24h format; UI range slider covers 0–23)

Validation rules enforced by the `configure_heartbeat` tool:

- Interval must be at least 5 minutes
- Active hours must be in the range 0–23

## Execution Flow

1. The task scheduler polls every 60 seconds
2. On each poll, it checks: is heartbeat enabled? Is the current hour within active hours? Has the configured interval elapsed since the last heartbeat?
3. If all conditions are met and no other API call is in progress, a heartbeat prompt is built and sent via `askWithTools` (which includes the full tool-calling loop)
4. The last heartbeat timestamp is updated and a log entry is recorded

## Response Handling

- If the AI responds with exactly "HEARTBEAT_OK" (after trimming), nothing is shown to the user
- Any other response is saved into a dedicated heartbeat conversation (type `heartbeat`) via `addAssistantMessage`
- A dismissable banner appears at the top of the chat when the heartbeat has something to report
- Tapping the banner loads the heartbeat conversation so the user can read the report and reply
- The X button dismisses the banner without navigating
- Heartbeat conversations are included in the chat history list with a "Heartbeat" label badge, and can also be accessed via the banner
- The heartbeat prompt is sent as a standalone message (not including user chat history as context)
- If the API call fails, a failure entry is recorded in the heartbeat log

## Prompt Building

The heartbeat prompt is assembled from multiple sources:

1. **Custom prompt** — user-defined text from settings, or the default prompt if empty. The default instructs the AI to review memories and tasks, respond "HEARTBEAT_OK" if nothing needs attention, or address anything that does
2. **Pending tasks** — all tasks with status PENDING are listed with their description, id, scheduled time, and cron expression (if recurring)
3. **Email status** — if email is enabled and accounts exist, each account's email address, unread count, and last sync time are included
4. **Promotion candidates** — memories with 5 or more hits are listed with their key, hit count, category, and content, along with a suggestion to use the `promote_learning` tool

## Heartbeat Log

- Stores up to 5 most recent heartbeat entries
- Each entry records success/failure and a timestamp
- Displayed in the settings UI under the heartbeat section
- Entries show an OK/FAIL indicator and a formatted local timestamp

## Promote Learning

When a memory has been reinforced 5 or more times, it becomes a promotion candidate. The `promote_learning` tool:

1. Looks up the memory by key
2. Appends the provided `soul_addition` text to the soul/system prompt
3. Removes the original memory from the memory store
4. Returns confirmation with the promoted key and hit count

This allows well-established patterns to graduate from ephemeral memory into permanent AI behavior.

## Settings UI

The heartbeat section in settings contains:

- **Toggle** — enables or disables heartbeat with a switch
- **Interval display** — shows the current interval in minutes in the section description
- **Interval slider** — a snap-to-preset slider with positions for 5m, 10m, 15m, 30m, 45m, 1h, 2h, 4h. Displays the formatted value (e.g. "15m", "2h") next to the label
- **Active hours range slider** — a dual-thumb range slider spanning 0–23 (24-hour clock). Displays "H:00 – H:00" next to the label (unpadded hours)
- **Custom prompt editor** — a text field (max 4000 characters) for editing the heartbeat prompt, with a save button that appears when changes are detected. Shows the default prompt text when no custom prompt is set
- **Log display** — when log entries exist, shows a "Recent" label followed by each entry with an OK/FAIL indicator and timestamp

## AI Tools

| Tool | Purpose |
|---|---|
| `configure_heartbeat` | Enable/disable heartbeat, set interval and active hours |
| `trigger_heartbeat` | Force a heartbeat on the next poll cycle by resetting the last heartbeat time and enabling heartbeat |
| `promote_learning` | Promote a reinforced memory into the soul/system prompt |

## Key Files

| File | Purpose |
|---|---|
| `composeApp/src/commonMain/.../data/HeartbeatManager.kt` | Config, prompt building, log management |
| `composeApp/src/commonMain/.../tools/HeartbeatTools.kt` | AI tool definitions for heartbeat and promotion |
| `composeApp/src/commonMain/.../data/TaskScheduler.kt` | Poll loop that triggers heartbeat checks |
| `composeApp/src/commonMain/.../data/AppSettings.kt` | Persisted heartbeat config, prompt, and log storage |
| `composeApp/src/commonMain/.../data/RemoteDataRepository.kt` | Heartbeat conversation creation, unread flag management |
| `composeApp/src/commonMain/.../ui/chat/composables/HeartbeatBanner.kt` | Dismissable notification banner UI |
| `composeApp/src/commonMain/.../ui/settings/SettingsScreen.kt` | Heartbeat settings UI section |
