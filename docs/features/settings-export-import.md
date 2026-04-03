# Settings Export / Import

**Last verified:** 2026-04-03

Users can backup and restore all Kai settings via a human-readable JSON file. The feature is available under **Settings > General** at the bottom of the page.

## Behavior

### Export
- Tapping **Export** opens a native file-save dialog.
- The exported file (`kai-settings.json`) contains every user-configurable setting except those listed under **Excluded** below.
- The JSON includes a `"version": 1` field for forward-compatibility.

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

## Excluded

- `daemon_enabled` (platform-specific, should not transfer between devices)
- `app_opens` (analytics counter)
- `encryption_key` (security-sensitive)
- `ui_scale` (platform-specific, desktop may differ from mobile)
- Migration flags

## Key Files

| File | Role |
|------|------|
| `composeApp/.../data/AppSettings.kt` | `ImportSection` enum, `detectImportSections()`, `exportToJson()` / `importFromJson()` core logic, `sanitizeScheduledTasks()` / `sanitizeMemories()` default-filling helpers |
| `composeApp/.../data/DataRepository.kt` | Interface methods |
| `composeApp/.../data/RemoteDataRepository.kt` | Wires AppSettings to platform tool IDs, serializes JSON |
| `composeApp/.../ui/settings/SettingsUiState.kt` | Callbacks (`onExportSettings`, `onImportSettings`) |
| `composeApp/.../ui/settings/SettingsViewModel.kt` | Delegates to repository, rebuilds UI state after import |
| `composeApp/.../ui/settings/SettingsScreen.kt` | Export/Import card with FileKit dialogs, `ImportPreviewDialog` |
| `composeApp/.../testutil/FakeDataRepository.kt` | Test stubs |
| `composeApp/.../data/AppSettingsExportImportTest.kt` | Unit tests including v1 snapshot test |
