---
okf_version: "0.2"
---

# Free-tier model knowledge

OKF v0.2 bundle for Kai’s **curated free-tier model lists**. Agents use this folder for policy, provenance, and freshness when refreshing the runtime catalog.

## Runtime vs knowledge

| Layer | Path | Role |
|---|---|---|
| **Runtime** (shipped) | `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/data/FreeTierModels.kt` | Source of truth for Free badges in the app |
| **Knowledge** (this bundle) | `docs/knowledge/free-tier/` | Curation policy, current snapshot, sources, stale dates |
| **Refresh skill** | `.grok/skills/update-free-tier-models/SKILL.md` | Fetches external sources, updates Kotlin + this bundle |

The app **never** reads this directory at runtime. No live pricing calls are made in production.

## Scope

Only **OpenRouter** and **Ollama Cloud** are curated. Free-ness is **per service**, not global.

## Catalogs

* [OpenRouter free-tier models](openrouter.md) — $0 prompt + completion pricing (chat-oriented)
* [Ollama Cloud free-tier models](ollama-cloud.md) — free-plan / light-usage cloud models

## Playbooks

* [Refresh free-tier catalogs](refresh-playbook.md) — end-to-end update procedure (also `/update-free-tier-models`)

## History

* [Update log](log.md)
