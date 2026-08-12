---
type: Playbook
title: Refresh LiteRT pins
description: Verify HuggingFace commit/digest/size pins; bump LocalModelCatalog.kt only when product asks.
tags: [litert, on-device, playbook]
status: stable
generated: { by: human:simon, at: 2026-08-12T20:52:00Z }
---

# Trigger

- User runs `/update-litert-models`
- User asks to **bump** a catalog model to a newer HuggingFace revision
- [models.md](models.md) `stale_after` is today or in the past
- A catalog download fails integrity (length or SHA-256)

# Preconditions

- Read [index.md](index.md) for scope (pins only; no silent bump).
- Read [pin-policy.md](pin-policy.md).
- Default mode is **verify**. Bump only when the user said to bump a named model (or “bump all that have a newer main”).

# Steps

## 1. Read the current catalog

Read `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/inference/LocalModelCatalog.kt` (`MODEL_CATALOG`).

For each row parse `downloadUrl` into `repo`, `commit`, `fileName`. Fail the refresh if any URL uses `main` or another non-hex ref.

## 2. Fetch HuggingFace

For each model:

1. `https://huggingface.co/api/models/<repo>` — `sha` is current `main`.
2. `https://huggingface.co/api/models/<repo>/tree/<pinned-commit>?recursive=true` — find `fileName`; take `.lfs.oid` and `.lfs.size`.
3. Optional: `https://huggingface.co/api/models/<repo>/tree/main?recursive=true` when `main` ≠ pin, to record the available newer digest/size.
4. Optional cross-check: `curl -sI -L <downloadUrl>` `x-linked-etag`.

## 3. Diff

Show **before** writing:

- pins whose commit+digest+size still match (attested)
- pins whose `main` has moved (available newer revision — **do not bump** unless asked)
- pins whose commit is missing or digest mismatches (broken — do not invent a replacement)

## 4. Apply

**Verify-only (default):** do not change Kotlin URLs, SHA-256, or sizes. Update this bundle’s snapshot table, `generated`, `stale_after`, and [log.md](log.md).

**Bump (only if asked):** for each named model, set commit + SHA-256 + `sizeBytes` together from the tree API of the target commit (usually current `main`). Leave GPU baseline, context defaults, and `isRecommended` alone unless product said otherwise. Never write `/resolve/main/`.

Update:

`composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/inference/LocalModelCatalog.kt`

If sizes or display names used in `docs/features/on-device-inference.md` changed, update that table and **Last verified**.

Update this bundle:

- Replace the pin table in [models.md](models.md)
- `generated: { by: process:update-litert-models, at: <ISO-8601 UTC> }`
- `stale_after` ~30 days ahead (`YYYY-MM-DD`) — pins change on product release, not biweekly
- After tests pass, set `verified`

Append a dated entry to [log.md](log.md) (newest first under today’s heading).

## 5. Verify

1. `./gradlew :composeApp:compileKotlinDesktop`
2. `./gradlew :composeApp:desktopTest --tests '*LocalModel*'`
3. `./gradlew spotlessApply`
4. On green tests, set on [models.md](models.md):

   `verified: { by: process:desktopTest-LocalModel, at: <ISO-8601 UTC> }`

# Hard rules

- Do **not** add runtime HuggingFace calls to decide which bytes to download.
- Do **not** write a branch ref into a download URL.
- Do **not** bump unless the user asked.
- Bundle and Kotlin must not drift: every refresh updates the bundle; Kotlin only on bump.
