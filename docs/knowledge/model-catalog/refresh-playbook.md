---
type: Playbook
title: Refresh model catalog
description: Fetch Arena text Elo, update ModelCatalog.kt and this OKF bundle.
tags: [models, arena, elo, playbook]
status: stable
generated: { by: human:simon, at: 2026-08-12T19:56:19Z }
---

# Trigger

- User runs `/update-model-catalog`
- Arena Elo in Settings looks wrong or stale
- [arena-scores.md](arena-scores.md) `stale_after` is today or in the past
- Logcat / desktop logs show `ModelCatalog miss:` for a model users actually pick

# Preconditions

- Read [index.md](index.md) for scope (runtime vs knowledge; Elo only).
- Read [matching-policy.md](matching-policy.md) before mapping names.
- Do **not** put free-tier flags in this catalog.

# Steps

## 1. Fetch the text leaderboard

1. Fetch `https://arena.ai/leaderboard/text` (overall). Prefer the official page; there is no supported public Arena API.
2. Parse every ranked row: arena name, integer score, `±` CI, rank.
3. Skip AutoEval-only rows that have no rank / no vote count unless they already exist in the catalog.
4. Record the page date and model/vote counts in the snapshot table.

If the page is truncated or unparsable, stop and do not write.

## 2. Read the current catalog

Read `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/data/ModelCatalog.kt`:

- `baseEntries` — do not rewrite metadata unless correcting an error or adding a new model
- `arenaScores` — attested block (provider comments) + auto-fill block

## 3. Match and diff

1. Apply [matching-policy.md](matching-policy.md).
2. Diff attested scores against the current `arenaScores` **and** against the [Attested](arena-scores.md) list.
3. Show a short summary **before** writing: scores changed, new board models, catalog ids still estimated, unmatched arena names left out.

## 4. Apply

1. Update `arenaScores` in place. Keep provider grouping, same-line **true** aliases, and score-descending order within a provider.
2. Add `baseEntries` + scores only for new **frontier** chat models (see policy). Preserve `-latest` / `-preview` display-name conventions.
3. Fill **missing** chat-model scores only (not already present) using the estimate rules in the policy. Leave existing estimates alone unless they are now attested.
4. Update this bundle:
   - Replace the attested list and snapshot table in [arena-scores.md](arena-scores.md)
   - Set `generated: { by: process:update-model-catalog, at: <ISO-8601 UTC> }`
   - Set `stale_after` to ~14 days ahead (`YYYY-MM-DD`)
   - After tests pass, set `verified`
5. Append a dated entry to [log.md](log.md) (newest first under today’s heading).

## 5. Verify

1. `./gradlew :composeApp:compileKotlinDesktop`
2. `./gradlew :composeApp:desktopTest --tests '*ModelCatalog*'`
3. `./gradlew spotlessApply`
4. On green tests, set on [arena-scores.md](arena-scores.md):

   `verified: { by: process:desktopTest-ModelCatalog, at: <ISO-8601 UTC> }`

# Hard rules

- Do **not** add runtime network calls for Elo.
- Do **not** remove existing catalog entries.
- Do **not** change `baseEntries` metadata unless correcting an error or adding a new model.
- Bundle and Kotlin must not drift: every refresh updates **both**.
- Free-ness is per service — never write it here.
- When unsure whether two names are the same model, leave the existing score (conservative).
