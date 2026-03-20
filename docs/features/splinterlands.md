# Splinterlands Auto-Battle

Last verified: 2026-03-18

## Overview

Kai can automatically play Wild Ranked Splinterlands battles. The feature uses one or more LLM services to pick teams based on match constraints (mana cap, rulesets, inactive splinters), submits them via the Hive blockchain, and plays continuously until the player hits Stop or runs out of energy.

## Configuration

Users configure the feature in **Settings > Integrations > Splinterlands**:

Multiple accounts can be added, each with independent battle controls. Settings:

- **Hive Username** -- the Splinterlands account name
- **Posting Key** -- WIF-encoded Hive posting key (stored per-account, never exported by default)
- **LLM Services** -- one or more configured service instances in priority order. All services are queried in parallel; the first valid response by priority wins. Services can be added, removed, and reordered via the service list UI.

The Start button is disabled when no services are configured.

## Battle Flow

1. Login with posting key to get JWT
2. Fetch card details (full game card database)
3. Check REWARD_ENERGY balance; stop if zero
4. Check for outstanding match (resume if found, including waiting for result if team already submitted)
5. Sign and submit `sm_find_match` custom_json operation
6. Poll for opponent (3s interval, 180s timeout)
7. Fetch player card collection
8. Build LLM prompt with available summoners, monsters, rulesets, and mana cap
9. Query all configured services in parallel; parse and validate each response independently
10. Pick the first valid result by priority order; apply silent fixes per-service if needed
11. Fall back to simple greedy picker if all services fail
12. Generate team hash (MD5) and secret
13. Sign and submit `sm_submit_team` custom_json operation
14. Poll for battle result (5s interval, 120s timeout)
15. Log result (including winning service's model name)

The battle loop runs in its own long-lived coroutine scope (tied to the Koin singleton lifetime, not the ViewModel). This means battles survive navigation away from the Settings screen and continue running in the background. On Android, starting a battle also activates the foreground service (via DaemonController) to keep the process alive.

The Stop button behavior depends on the current phase. Before a match is committed (Idle, LoggingIn, CheckingEnergy, FindingMatch, WaitingForOpponent) pressing Stop cancels immediately and signs `sm_cancel_match` if needed. During mid-battle phases (FetchingCollection, PickingTeam, SubmittingTeam, WaitingForResult) pressing Stop sets a graceful stop flag — the current battle finishes normally, then the loop exits. The button shows "Stopping..." while waiting for the battle to complete. The battle loop also auto-stops after 5 consecutive errors or when energy reaches zero.

## Team Picking

### Parallel LLM Picker
- System prompt includes full game rules reference (abilities, rulesets, combat mechanics)
- Lists numbered summoners (S1, S2...) and monsters (M1, M2...) with stats
- Pre-filters cards by inactive splinters, ruleset restrictions, and gladiator eligibility (Conscript check)
- Deduplicates cards by detail ID before prompting
- Expects JSON response with plain integer IDs: `{"summoner": <number>, "monsters": [<number>...], "mana_total": <number>}`
- All configured services are queried simultaneously with the same prompt via `async` coroutines
- Each response is validated independently; silent fixes (dedup, mana trim, color fix, gladiator fix, auto-fill) are applied per-service
- As soon as all higher-priority services have finished, the best valid result is selected immediately and remaining services are cancelled (e.g. if priority-0 returns a valid team first, it is used instantly without waiting for others)
- Time-aware: skips services if less than 10s remain before deadline; per-request HTTP timeout is deadline minus 5s (minimum 10s). The deadline comes from the server's `submit_expiration_date` minus 10s, falling back to 180s.
- 70% mana efficiency check

### Simple Fallback
- Sort monsters by mana descending
- For each summoner: fill with highest-mana monsters of matching colors
- Dragon summoners: try each ally color, pick best team

### Ruleset Filters (Category A -- card selection)
Rarity, attack type, mana cost, color, and stat threshold filters applied before team picking.

## UI

The service list shows configured LLM services in priority order with:
- Priority number, service icon (from DrawableResource), name, and model
- Up/down arrow buttons for reordering
- Trash icon button to remove a service
- "Add Service" / "Add Another Service" dropdown button filtered to exclude already-added services

The account row shows avatar (loaded via Coil), username, energy, W/L stats, and start/stop controls. The Start button is disabled when no services are configured. While a battle is running, additional details appear below the player row:

- **Match info**: opponent name, mana cap, and rulesets (shown once a match is found)
- **Phase status**: current battle phase (logging in, finding match, picking team, waiting for result, etc.)
- **Per-service status**: during the PickingTeam phase, each service shows a status indicator (spinner for Querying, checkmark for ValidResponse, X for InvalidResponse/Failed, star for Selected)
- **LLM indicator**: winning service's model name (from `winningServiceName`) or "Auto" badge once team selection completes
- **Countdown timer**: live countdown from team deadline to 0:00 (turns red below 30s)

The opponent name is extracted from the battle result (`player_1`/`player_2` fields) for accurate display; during match-finding, the match queue's `opponent_player` field is tried first.

Recent Battles log shows up to 30 entries (5 visible by default, expandable): Victory/Defeat badge, opponent name, relative timestamp ("just now", "5 min", "2 hours"), account name, mana, rulesets, and the model name that picked the team. Clicking a battle log entry with activity opens a dialog showing the full activity log. A "View Battle" link opens the Splinterlands battle page. The Add Account form appears below the battle log.

## Platform Support

- **Desktop (JVM)** and **Android**: Full support via BouncyCastle secp256k1. Signing uses RFC 6979 deterministic k with y-parity based recovery ID computation. Transaction signing uses single SHA-256 of (chain_id + serialized tx), following the Hive/Graphene signing protocol.
- **iOS** and **Web**: Hidden (`isSplinterlandsSupported = false`)

## Key Files

| File | Purpose |
|------|---------|
| `splinterlands/SplinterlandsModels.kt` | Data classes, constants, `LlmServiceStatus` enum, `BattleStatus` with `serviceStatuses` and `winningServiceName` |
| `splinterlands/SplinterlandsStore.kt` | CRUD via AppSettings; `getInstanceIds()`/`setInstanceIds()` for multi-service, `getModelName(instanceId)` |
| `splinterlands/SplinterlandsApi.kt` | Ktor HTTP client for Splinterlands REST API |
| `splinterlands/SplinterlandsTeamPicker.kt` | Ruleset filtering, card scoring, LLM prompt building, response parsing, validation, silent fixes |
| `splinterlands/SplinterlandsBattleRunner.kt` | Battle loop with `queryServicesInParallel()` for parallel multi-service LLM querying |
| `splinterlands/HiveCrypto.kt` | Expect declarations for Hive signing |
| `splinterlands/HiveCrypto.jvm.kt` | BouncyCastle secp256k1 ECDSA (Desktop) |
| `splinterlands/HiveCrypto.android.kt` | BouncyCastle secp256k1 ECDSA (Android) |
| `splinterlands/HiveCrypto.ios.kt` | Stub/unsupported implementation (iOS) |
| `splinterlands/HiveCrypto.wasmJs.kt` | Stub/unsupported implementation (Web) |
| `splinterlands/HiveCryptoTest.kt` | Signing + recovery round-trip tests (desktopTest) |
| `data/AppSettings.kt` | Splinterlands key/value accessors including `splinterlands_instance_ids` JSON array |
| `ui/settings/SplinterlandsUiState.kt` | `SplinterlandsUiState`, `SplinterlandsAccountUiState`, `SplinterlandsAddStatus` |
| `ui/settings/SplinterlandsViewModel.kt` | Wires Splinterlands callbacks, builds account states from battle runner |
| `ui/settings/SplinterlandsComposables.kt` | `SplinterlandsServiceList`, `SplinterlandsAccountRow` with per-service battle status |
| `ui/settings/SettingsScreen.kt` | `SplinterlandsSection` call site in `IntegrationsContent` |
