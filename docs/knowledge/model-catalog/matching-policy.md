---
type: Policy
title: Model catalog matching and estimate policy
description: How Arena text-leaderboard names map onto Kai catalog ids, and when a score is attested vs estimated.
tags: [models, arena, elo, policy]
status: stable
generated: { by: human:simon, at: 2026-08-12T19:56:19Z }
---

# Goal

Every **chat** catalog id should have an Elo so the settings list can sort and badge models. Only some of those numbers are leaderboard facts.

# Attested

A score is **attested** when the Arena **text / overall** row maps to the catalog id by the rules below. Store the integer before `±`.

## Name matching (in order)

1. **Exact** lowercase match of the arena name to a catalog id.
2. **Display-form normalize** — treat `(xHigh)`, `(High)`, `(Max)`, `(thinking)`, `(non-thinking)`, `(thinking-minimal)` as `-xhigh` / `-high` / `-max` / `-thinking` / `-non-thinking` / `-thinking-minimal` suffixes; collapse spaces/underscores to `-`.
3. **Punctuation aliases** of that same token — `.` vs `-` in version numbers (`claude-opus-4.6` ↔ `claude-opus-4-6`), plus `:free` / `-free` on the same id.
4. **Same-model alias lines** already grouped in Kotlin (several ids on one line **that already share one score**) inherit together when one of them is attested.

## Must not copy

- A **dated snapshot** onto a **different dated snapshot** (`gpt-4o-2024-05-13` is not `gpt-4o-2024-08-06`).
- A dated snapshot onto a **generic** id that is already bound to another snapshot (`gpt-4o` stays with `gpt-4o-2024-08-06`, not May).
- A **quality / thinking tier** onto the base id (`muse-spark-1.2 (xHigh)` updates `muse-spark-1.2-xhigh` only; `mimo-v2-flash (thinking)` is not `mimo-v2-flash`).
- Unrelated ids that only **share a source line** for formatting (`gpt-4o-mini` and `gpt-oss-20b` are not aliases).
- A score from **WebDev / Vision / Agent / Search** boards onto this catalog. Text / overall only.

When two rules conflict, keep the more specific arena row.

# Estimated

If a **chat** catalog id still has no attested score:

1. Keep the existing estimate if one is already in `arenaScores` (default on refresh).
2. Only fill a **missing** score:
   - `-latest` / `-preview` of an attested base → that base score
   - Fine-tune / quant / `:free` of an attested base → that base score
   - Else the closest **same-family, similar-size** sibling that is attested
3. Never invent a score for a non-chat id (`isChatModel()` false: embed, tts, guard, image, veo, …).

Mark estimates in the auto-fill section of `arenaScores`, never in the attested snapshot.

# New board models

Add a `baseEntries` row **only** for a newly seen **frontier / currently shipped** chat model that Kai users can select. Do **not** backfill historical arena rows (early Llama, Dolly, Vicuna, …).

New rows need display name, context window (from the leaderboard when listed, else a documented family default), release month, and the attested Elo. Do not remove existing catalog entries.

# Misses

Runtime logs `ModelCatalog miss:` once per unknown id. A miss is a candidate for a later `baseEntries` add — not a reason to fuzzy-match Elo.
