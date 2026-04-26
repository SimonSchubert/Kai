# Settings Export / Import

**Last verified:** 2026-04-26

Users can backup and restore all Kai settings via a human-readable JSON file. The feature is available under **Settings > General** at the bottom of the page.

## Behavior

### Export
- Tapping **Export** opens an **Export Preview Dialog** that lists each settings section currently holding real user data, with item counts where applicable (e.g. "Services (2)", "Memory (5)"). All sections are checked by default; the user can untick any section to leave it out of the file.
- A section is only listed when it actually has data. Pure feature-toggle flags (e.g. SMS turned off, Splinterlands without a configured account, an empty MCP server list) do not appear in the dialog. Specifically:
  - SERVICES — only if at least one service is configured
  - SOUL — only if soul text is non-empty
  - MEMORY / SCHEDULING / CONVERSATIONS — only if at least one entry exists
  - HEARTBEAT — only if a custom prompt, config, or log entries exist
  - EMAIL — only if at least one account is configured
  - SMS — only if SMS receive or send is enabled
  - SPLINTERLANDS — only if an account is configured
  - MCP — only if at least one server is configured
  - TOOLS — only if at least one tool override is recorded
- Confirming the dialog opens a native file-save dialog and writes `kai-settings.json` containing only the selected sections (plus a `"version": 1` field for forward-compatibility).
- Cancelling the dialog discards the export without writing a file.
- Sections listed under **Excluded** below are never exported.

### Import
- Tapping **Import** opens a native file picker filtered to `.json` files.
- After the file is selected and parsed, an **Import Preview Dialog** appears.
- The dialog detects which sections are present in the JSON and shows a checkbox for each one (all enabled by default), with item counts where applicable (e.g. "Services (2)", "Memory (5)").
- A Replace/Merge toggle controls what happens to unselected sections:
  - **Replace** (default): Unselected sections reset to their defaults.
  - **Merge**: Only apply selected sections; all other settings stay unchanged.
- Clicking **Import** in the dialog applies the selected sections.
- Each settings section is imported independently. If one section contains malformed data, the remaining sections are still imported and the error is counted.
- Unknown keys are silently ignored, so older exports can be imported into newer app versions.
- Scheduled tasks and memories with missing or invalid fields are auto-filled with sensible defaults (e.g. generated UUIDs for missing IDs, `PENDING` for invalid task status, `GENERAL` for invalid memory category). This ensures items are preserved even if the JSON was hand-edited or exported from a different version.

## Import Sections

| Section | Display Name | JSON keys detected |
|---------|-------------|-------------------|
| SERVICES | Services | `configured_services`, `current_service_id`, `free_fallback_enabled`, `instance_settings` |
| SOUL | Soul | `soul_text` |
| MEMORY | Memory | `memory_enabled`, `agent_memories` |
| SCHEDULING | Scheduling | `scheduling_enabled`, `scheduled_tasks` |
| HEARTBEAT | Heartbeat | `heartbeat_config`, `heartbeat_prompt`, `heartbeat_log` |
| EMAIL | Email | `email_enabled`, `email_accounts` |
| TOOLS | Tools | `tool_overrides` |
| MCP | MCP Servers | `mcp_servers` |
| CONVERSATIONS | Conversations | `conversations` |
| SPLINTERLANDS | Splinterlands | `splinterlands_enabled`, `splinterlands_account` |
| SMS | SMS | `sms_enabled`, `sms_poll_interval`, `sms_send_enabled` |

## Settings Included

| Category | Keys |
|----------|------|
| Services | `configured_services`, `current_service_id`, `free_fallback_enabled`, per-instance `api_key` / `model_id` / `base_url` |
| Soul | `soul_text` |
| Memory | `memory_enabled`, `agent_memories` |
| Scheduling | `scheduling_enabled`, `scheduled_tasks` |
| Heartbeat | `heartbeat_config`, `heartbeat_prompt`, `heartbeat_log` |
| Email | `email_enabled`, `email_accounts`, per-account passwords and sync state, `email_poll_interval` |
| Tools | Per-tool `tool_enabled_*` overrides |
| MCP | `mcp_servers` |
| Conversations | `conversations` (array of conversation objects with messages) |
| Splinterlands | `splinterlands_enabled`, `splinterlands_account`, `splinterlands_instance_ids`, `splinterlands_battle_log` |
| SMS | `sms_enabled`, `sms_poll_interval`, `sms_send_enabled`, `sms_pending`, `sms_sync_state`, `sms_drafts` |

## Excluded

- `daemon_enabled` (platform-specific, should not transfer between devices)
- `app_opens` (analytics counter)
- `encryption_key` (security-sensitive)
- `ui_scale` (platform-specific, desktop may differ from mobile)
- Migration flags

## Key Files

| File | Role |
|------|------|
| `composeApp/.../data/AppSettings.kt` | `ImportSection` enum, `detectImportSections()` / `detectExportableSections()` (the latter only flags sections with real user data), `exportToJson()` / `importFromJson()` core logic (both accept `Set<ImportSection>` for selective filtering), `sanitizeScheduledTasks()` / `sanitizeMemories()` default-filling helpers |
| `composeApp/.../data/DataRepository.kt` | Interface methods (`exportSettingsToJson(sections)`, `getExportPreview()`, `importSettingsFromJson(...)`) |
| `composeApp/.../data/RemoteDataRepository.kt` | Wires AppSettings to platform tool IDs, serializes JSON, runs `detectImportSections` over a full export to drive the export preview |
| `composeApp/.../ui/settings/SettingsActions.kt` | Callbacks (`onExportSettings`, `onPrepareExport`, `onImportSettings`) |
| `composeApp/.../ui/settings/SettingsViewModel.kt` | Delegates to repository, rebuilds UI state after import |
| `composeApp/.../ui/settings/SettingsScreen.kt` | Export/Import card with FileKit dialogs, `ExportPreviewDialog`, `ImportPreviewDialog` |
| `composeApp/.../testutil/FakeDataRepository.kt` | Test stubs |
| `composeApp/.../data/AppSettingsExportImportTest.kt` | Unit tests including v1 snapshot test |
