# Settings Export / Import

**Last verified:** 2026-03-10

Users can backup and restore all Kai settings via a human-readable JSON file. The feature is available under **Settings > General** at the bottom of the page.

## Behavior

### Export
- Tapping **Export** opens a native file-save dialog.
- The exported file (`kai-settings.json`) contains every user-configurable setting except those listed under **Excluded** below.
- The JSON includes a `"version": 1` field for forward-compatibility.

### Import
- Tapping **Import** opens a native file picker filtered to `.json` files.
- All keys in the JSON are optional; missing keys leave the corresponding setting unchanged.
- Unknown keys are silently ignored, so older exports can be imported into newer app versions.

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

## Excluded

- `daemon_enabled` (platform-specific, should not transfer between devices)
- `app_opens` (analytics counter)
- `encryption_key` (security-sensitive)
- `ui_scale` (platform-specific, desktop may differ from mobile)
- Migration flags

## Key Files

| File | Role |
|------|------|
| `composeApp/.../data/AppSettings.kt` | `exportToJson()` / `importFromJson()` core logic |
| `composeApp/.../data/DataRepository.kt` | Interface methods |
| `composeApp/.../data/RemoteDataRepository.kt` | Wires AppSettings to platform tool IDs, serializes JSON |
| `composeApp/.../ui/settings/SettingsUiState.kt` | Callbacks (`onExportSettings`, `onImportSettings`) |
| `composeApp/.../ui/settings/SettingsViewModel.kt` | Delegates to repository, rebuilds UI state after import |
| `composeApp/.../ui/settings/SettingsScreen.kt` | Export/Import card with FileKit dialogs |
| `composeApp/.../testutil/FakeDataRepository.kt` | Test stubs |
| `composeApp/.../data/AppSettingsExportImportTest.kt` | Unit tests including v1 snapshot test |
