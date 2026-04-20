# Splinterlands Auto-Battle

Last verified: 2026-04-20

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

### Scoring-Based Fallback
- Scores each monster based on stats, abilities, and active rulesets for two positions: tank (position 1) and backline (positions 2+)
- Tank scoring rewards HP, armor, Shield, Void, Heal, Taunt, and other defensive abilities
- Backline scoring rewards Tank Heal, Triage, Inspire, Resurrect, damage abilities, and team buffs
- All ~40 strategic rulesets shift scoring (e.g., Earthquake boosts Flying, Reverse Speed rewards low speed, Noxious Fumes prioritizes HP and Immunity, Equalizer favors low-mana high-attack, Briar Patch/Counterspell/Fire & Regret penalize reflected damage types)
- Counter-scoring anticipates enemy strategies: since both players face the same rulesets, predicts likely enemy attack types and boosts defensive counters (e.g., if rulesets favor melee, boosts Thorns/Demoralize/Shield/Blind; if magic, boosts Void/Silence/Phase). Enemy debuffs (Demoralize, Headwinds, Silence) are only scored when the enemy can use that attack type. Disruptive abilities (Affliction, Dispel, Halving, Shatter) are scored based on enemy composition.
- Abilities are ignored when Back to Basics is active (raw stats only)
- Summoners are scored by buff synergy with the available monster pool and ruleset interactions
- For each summoner: tries top 3 tank candidates, fills backline by score-per-mana ratio, picks the best overall team across all summoners
- Dragon summoners: try each ally color, pick best scoring combination
- Super Sneak: moves the second-tankiest monster to last position (targeted by all enemy Sneaks)

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

A **Model Rankings** table appears above the battle log when there is battle data. It groups battles by model name (and separately tracks the fallback auto picker), showing wins, losses, and win rate percentage for each. Models are sorted by win rate descending. The top-ranked model row is highlighted. Win rates are color-coded: green for 60%+, red for below 40%.

Recent Battles log shows up to 500 entries (5 visible by default, expandable): Victory/Defeat badge, opponent name, relative timestamp ("just now", "5 min", "2 hours"), account name, mana, rulesets, and the model name that picked the team. Clicking a battle log entry with activity opens a dialog showing the full activity log. A "View Battle" link opens the Splinterlands battle page. The Add Account form appears below the battle log.

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
