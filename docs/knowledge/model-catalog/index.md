---
okf_version: "0.2"
---

# Model catalog knowledge

OKF v0.2 bundle for Kai’s **curated model metadata and Arena Elo scores**. Agents use this folder for matching policy, provenance, and freshness when refreshing the runtime catalog.

## Runtime vs knowledge

| Layer | Path | Role |
|---|---|---|
| **Runtime** (shipped) | `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/data/ModelCatalog.kt` | Source of truth for display name, context window, release date, params, and Elo shown in the app |
| **Knowledge** (this bundle) | `docs/knowledge/model-catalog/` | Matching policy, attested Arena snapshot, stale dates |
| **Refresh skill** | `.grok/skills/update-model-catalog/SKILL.md` | Fetches the text leaderboard, updates Kotlin + this bundle |

The app **never** reads this directory at runtime. No live Arena calls are made in production.

## Scope

- **In:** Arena **text / overall** Elo, and how those numbers map onto catalog ids.
- **Out:** `baseEntries` editorial metadata (display name, context window, release date, parameter count) stays in Kotlin only — it does not churn from the leaderboard the way Elo does.
- **Out:** Free-tier badges. Those are per-service and live in [`docs/knowledge/free-tier/`](../free-tier/index.md). Never write free-ness into this catalog.

## Catalogs

* [Arena text Elo scores](arena-scores.md) — attested leaderboard snapshot vs estimated fills

## Policy

* [Matching and estimate policy](matching-policy.md) — exact / punctuation aliases, what must not be copied, how gaps are filled

## Playbooks

* [Refresh model catalog](refresh-playbook.md) — end-to-end update procedure (also `/update-model-catalog`)

## History

* [Update log](log.md)
