---
type: Playbook
title: Refresh free-tier catalogs
description: Fetch external free-tier signals, update FreeTierModels.kt and this OKF bundle.
tags: [models, free-tier, playbook]
status: stable
generated: { by: human:simon, at: 2026-08-11T17:36:04Z }
---

# Trigger

- User runs `/update-free-tier-models`
- Free badges look wrong (missing or incorrectly present)
- Either catalog’s `stale_after` date is today or in the past (see [OpenRouter](openrouter.md), [Ollama Cloud](ollama-cloud.md))
- Free models appear or disappear at OpenRouter or Ollama Cloud

# Preconditions

- Read [index.md](index.md) for scope and layering (runtime vs knowledge).
- Read policy sections in [openrouter.md](openrouter.md) and [ollama-cloud.md](ollama-cloud.md) before scraping.
- Only **OpenRouter** and **Ollama Cloud** — do not expand services without an explicit product decision.

# Steps

## 1. OpenRouter

1. Fetch `https://openrouter.ai/api/v1/models` (no auth).
2. Keep models where `pricing.prompt` and `pricing.completion` are both numeric `0`.
3. Drop non-chat models using the same idea as app `isChatModel()` (see OpenRouter policy).
4. Prefer `:free` suffix ids and `openrouter/free`.
5. Build the candidate lowercase id set.

## 2. Ollama Cloud

1. Fetch `https://ollama.com/v1/models` for cloud model ids.
2. For each id, check library usage level at `https://ollama.com/library/{id}` (try cloud tag variants when needed).
3. Include **Low** only by default; never **High** / **Extra High**.
4. Store API-style ids; rely on runtime alias normalization for `:cloud` / `-cloud`.

## 3. Diff and apply

1. Diff candidates against the **Current set** sections in this bundle (and against `FreeTierModels.kt`).
2. Show a short added/removed summary per service **before** writing.
3. Update runtime sets in:

   `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/data/FreeTierModels.kt`

   Preserve file structure and comments; sets only.
4. Update this bundle:
   - Replace **Current set** lists in [openrouter.md](openrouter.md) and [ollama-cloud.md](ollama-cloud.md)
   - Set `generated: { by: process:update-free-tier-models, at: <ISO-8601 UTC> }`
   - Set `stale_after` to ~14 days ahead (`YYYY-MM-DD`)
   - Optionally set `verified` after tests pass (see below)
   - Keep `sources` and policy sections unless the external procedure actually changed
5. Append a dated entry to [log.md](log.md) (newest first under today’s heading).

## 4. Verify

1. `./gradlew :composeApp:compileKotlinDesktop`
2. `./gradlew :composeApp:desktopTest --tests '*FreeTierModels*' --tests '*ModelTransformations*'`
3. `./gradlew spotlessApply`
4. Update `FreeTierModelsTest.kt` only if fixture seed ids changed.
5. On green tests, set on both catalogs:

   `verified: { by: process:desktopTest-FreeTierModels, at: <ISO-8601 UTC> }`

# Hard rules

- Do **not** add runtime network calls for free flags.
- Free-ness is **per service** — never put free ids into `ModelCatalog` as global metadata.
- Keep all set members lowercase.
- When unsure about an Ollama model’s free eligibility, leave it **out**.
